package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.core.config.CardsyncAppProperties;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliationAnalysisService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal VALUE_TOLERANCE = BigDecimal.valueOf(0.05);

  static final int ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE = 5_000;
  static final Integer EXCLUDED_CARD_RECONCILIATION_MODALITY = ModalityEnum.DIGITAL_WALLET.getCode();

  private final EntityManager entityManager;
  private final AdjustmentRepository adjustmentRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final AcquirerRepository acquirerRepository;
  private final FileProcessingProperties fileProcessingProperties;
  private final CardsyncAppProperties appProperties;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final ConciliationFeeAnalysisService feeAnalysisService;
  private final ConciliationDebitChargebackClassifier debitChargebackClassifier;
  private final PlatformTransactionManager transactionManager;

  private record ErpAcqBatchResult(
    int analyzed, int matched, int updated, int skippedDivergent,
    int flagUpdated, int businessContextUpdated, int notMatched,
    int valueDivergences, int acquirerDivergences, int ambiguousMatches,
    int candidatesCount
  ) {}

  @Transactional(readOnly = true)
  public ConciliationDashboardModel dashboard() {
    List<TransactionErpEntity> erpSales = transactionErpRepository.findAll();
    List<TransactionAcqEntity> acquirerSales = transactionAcqRepository.findAll();
    List<CreditOrderEntity> creditOrders = creditOrderRepository.findAll();
    List<ReleasesBankEntity> bankReleases = releasesBankRepository.findAll();
    List<PendingDebtEntity> pendingDebts = pendingDebtRepository.findAll();
    List<AdjustmentEntity> adjustments = adjustmentRepository.findAll();
    List<FeeAnalysisResult> feeAnalyses = acquirerSales.stream().map(feeAnalysisService::analyze).toList();

    BigDecimal erpGross = sum(erpSales.stream().map(TransactionErpEntity::getGrossValue));
    BigDecimal acquirerGross = sum(acquirerSales.stream().map(TransactionAcqEntity::getGrossValue));
    BigDecimal feeAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::appliedFeeValue));
    BigDecimal expectedFeeAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::expectedFeeValue));
    BigDecimal feeDifferenceAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::feeDifference));

    BigDecimal debitPending = sum(pendingDebts.stream()
      .filter(debt -> !debitChargebackClassifier.isChargeback(debt))
      .map(PendingDebtEntity::getPendingValue));
    BigDecimal chargebackOpen = sum(pendingDebts.stream()
      .filter(debitChargebackClassifier::isChargeback)
      .map(PendingDebtEntity::getPendingValue))
      .add(sum(adjustments.stream()
        .filter(debitChargebackClassifier::isChargeback)
        .map(debitChargebackClassifier::debitValue)));

    long matchedSales = acquirerSales.stream().filter(this::hasAnyReconciliationSignal).count();
    BigDecimal matchedAmount = sum(acquirerSales.stream().filter(this::hasAnyReconciliationSignal).map(TransactionAcqEntity::getGrossValue));
    long pendingSales = Math.max(0, erpSales.size() + acquirerSales.size() - matchedSales);
    BigDecimal pendingAmount = erpGross.subtract(matchedAmount).abs();

    BigDecimal difference = erpGross.subtract(acquirerGross).abs();
    long divergenceQuantity = countDivergences(erpGross, acquirerGross, adjustments, pendingDebts, feeAnalyses);
    BigDecimal divergenceAmount = difference
      .add(sum(adjustments.stream().map(AdjustmentEntity::getAdjustmentValue).map(this::abs)))
      .add(debitPending)
      .add(sum(feeAnalyses.stream().filter(fee -> !"OK".equals(fee.status())).map(FeeAnalysisResult::feeDifference).map(this::abs)));

    ConciliationSummaryModel summary = new ConciliationSummaryModel(
      erpSales.size(), erpGross,
      acquirerSales.size(), acquirerGross,
      matchedSales, matchedAmount,
      pendingSales, pendingAmount,
      feeAmount, expectedFeeAmount, feeDifferenceAmount,
      null, null,
      debitPending, chargebackOpen,
      divergenceQuantity, divergenceAmount
    );

    return new ConciliationDashboardModel(
      summary,
      salesByPeriod(erpSales, acquirerSales),
      new ConciliationComparisonModel(erpGross, acquirerGross, erpGross.subtract(acquirerGross), matchedAmount, pendingAmount),
      feesByAcquirer(feeAnalyses),
      divergencesByType(erpGross, acquirerGross, pendingDebts, adjustments, creditOrders, bankReleases, feeAnalyses),
      aging()
    );
  }

  @Transactional
  public ReconcileErpAcquirerFeesResultModel reconcileRedeErpAcquirerFees() {
    return reconcileRedeErpAcquirerFees("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerFeesResultModel reconcileRedeErpAcquirerFees(String trigger) {
    boolean reprocess = fileProcessingProperties.getReconciliation().isReprocessErpAcquirerFees();
    List<Integer> reconciledStatuses = erpAcquirerReconciledStatusCodes();
    List<Integer> pendingFeeStatuses = pendingFeeReconciliationStatusCodes();
    OffsetDateTime implantationDate = appProperties.getImplantationDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime lookbackDate = reconciliationLookbackDate();
    List<UUID> erpIds = transactionErpRepository.findRedeErpIdsForFeeReconciliation(
      reprocess,
      reconciledStatuses,
      EXCLUDED_CARD_RECONCILIATION_MODALITY,
      pendingFeeStatuses,
      implantationDate,
      lookbackDate
    );

    int analyzed = 0;
    int updatedErpSales = 0;
    int divergentRates = 0;
    int missingValidContracts = 0;
    int okRates = 0;
    int skippedWithoutAcquirer = 0;

    int batchNumber = 0;
    int totalBatches = (int) Math.ceil((double) erpIds.size() / ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE);

    log.info(
      "📌 Iniciando conciliação de taxas ERP Vendas Rede x Adquirente Rede. trigger={}, totalErpPendentesTaxa={}, batchSize={}, " +
        "totalBatches={}, statusVenda={}, statusTaxaPendente={}, reprocess={}",
      trigger,
      erpIds.size(),
      ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE,
      totalBatches,
      reconciledStatuses,
      pendingFeeStatuses,
      reprocess
    );

    for (int start = 0; start < erpIds.size(); start += ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE) {
      batchNumber++;
      int end = Math.min(start + ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE, erpIds.size());
      List<UUID> batchIds = erpIds.subList(start, end);

      long t0 = System.currentTimeMillis();
      List<TransactionErpEntity> erpBatch = transactionErpRepository.findRedeErpBatchForFeeReconciliation(
        batchIds,
        reprocess,
        reconciledStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        pendingFeeStatuses
      );
      long tErpFetch = System.currentTimeMillis() - t0;
      if (erpBatch.isEmpty()) {
        continue;
      }

      List<TransactionErpEntity> changedErpSales = new ArrayList<>();
      int batchAnalyzed = 0;
      int batchUpdated = 0;
      int batchDivergentRates = 0;
      int batchMissingContracts = 0;

      long tFeeStart = System.currentTimeMillis();
      for (TransactionErpEntity erp : erpBatch) {
        TransactionAcqEntity acq = erp.getTransactionAcq();
        if (acq == null) {
          skippedWithoutAcquirer++;
          continue;
        }

        if (isExcludedFromCardReconciliation(erp) || isExcludedFromCardReconciliation(acq)) {
          continue;
        }

        analyzed++;
        batchAnalyzed++;

        ConciliationFeeAnalysisService.FeeReconciliationResult feeResult = feeAnalysisService.reconcileMatchedSale(erp, acq);

        if (feeResult.erpChanged()) {
          updatedErpSales++;
          batchUpdated++;
          changedErpSales.add(erp);
        }

        if (feeResult.divergentRate()) {
          divergentRates++;
          batchDivergentRates++;
        } else if (feeResult.missingValidContract()) {
          missingValidContracts++;
          batchMissingContracts++;
        } else {
          okRates++;
        }
      }
      long tFee = System.currentTimeMillis() - tFeeStart;

      long tFlushStart = System.currentTimeMillis();
      if (!changedErpSales.isEmpty()) {
        transactionErpRepository.saveAll(changedErpSales);
      }
      entityManager.flush();
      entityManager.clear();
      long tFlush = System.currentTimeMillis() - tFlushStart;

      log.info(
        "⏱️ taxa batch={}/{} erpFetch={}ms fee={}ms flush={}ms analisadas={} atualizadas={} divergencias={} semContrato={}",
        batchNumber, totalBatches, tErpFetch, tFee, tFlush,
        batchAnalyzed, batchUpdated, batchDivergentRates, batchMissingContracts
      );
    }

    log.info(
      "✅ Conciliação de taxas ERP Vendas Rede x Adquirente Rede finalizada. trigger={}, analisadas={}, erpAtualizadas={}, divergenciasTaxa={}, " +
        "semContratoValido={}, taxasOk={}, semAdquirente={}",
      trigger,
      analyzed,
      updatedErpSales,
      divergentRates,
      missingValidContracts,
      okRates,
      skippedWithoutAcquirer
    );

    return new ReconcileErpAcquirerFeesResultModel(
      analyzed,
      updatedErpSales,
      divergentRates,
      missingValidContracts,
      okRates,
      skippedWithoutAcquirer
    );
  }

  private List<Integer> erpAcquirerReconciledStatusCodes() {
    return List.of(
      StatusTransactionEnum.AUTOMATICALLY_RECONCILED.getCode(),
      StatusTransactionEnum.MANUALLY_RECONCILED.getCode()
    );
  }

  private List<Integer> pendingFeeReconciliationStatusCodes() {
    return List.of(
      FeeReconciliationStatusEnum.NULL.getCode(),
      FeeReconciliationStatusEnum.PENDING.getCode()
    );
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileRedeErpWithAcquirer() {
    return reconcileRedeErpWithAcquirer("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileRedeErpWithAcquirer(String trigger) {
    boolean reconcileAlreadyReconciled = shouldReconcileAlreadyReconciledErpAcquirerSales();
    List<Integer> pendingStatuses = erpAcquirerPendingStatusCodes();

    OffsetDateTime implantationDate = appProperties.getImplantationDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime lookbackDate = reconciliationLookbackDate();

    UUID redeAcquirerId = acquirerRepository.findByFileIdentifierIgnoreCase("REDE")
      .map(AcquirerEntity::getId)
      .orElse(null);
    if (redeAcquirerId == null) {
      log.warn("⚠️ Adquirente REDE não encontrada na base, conciliação ERP x ADQ ignorada.");
      return new ReconcileErpAcquirerResultModel(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    List<UUID> erpIds = transactionErpRepository.findRedeErpIdsForReconciliation(
      reconcileAlreadyReconciled,
      pendingStatuses,
      EXCLUDED_CARD_RECONCILIATION_MODALITY,
      implantationDate,
      lookbackDate
    );

    int analyzed = 0;
    int matched = 0;
    int updated = 0;
    int skippedDivergent = 0;
    int flagUpdated = 0;
    int businessContextUpdated = 0;
    int notMatched = 0;
    int valueDivergences = 0;
    int acquirerDivergences = 0;
    int ambiguousMatches = 0;

    int batchNumber = 0;
    int totalBatches = (int) Math.ceil((double) erpIds.size() / ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE);

    log.info(
      "📌 Iniciando conciliação ERP Vendas Rede x Adquirente Rede em lotes. trigger={}, totalErp={}, batchSize={}, totalBatches={}, reconcileAlreadyReconciled={}",
      trigger,
      erpIds.size(),
      ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE,
      totalBatches,
      reconcileAlreadyReconciled
    );

    TransactionTemplate batchTx = new TransactionTemplate(transactionManager);
    batchTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    for (int start = 0; start < erpIds.size(); start += ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE) {
      batchNumber++;
      final int end = Math.min(start + ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE, erpIds.size());
      final List<UUID> batchIds = erpIds.subList(start, end);
      final int currentBatch = batchNumber;

      ErpAcqBatchResult result = batchTx.execute(status -> {
        long t0 = System.currentTimeMillis();
        List<TransactionErpEntity> erpBatch = transactionErpRepository.findRedeErpBatchForReconciliation(batchIds);
        long tErpFetch = System.currentTimeMillis() - t0;
        if (erpBatch.isEmpty()) {
          return null;
        }

        t0 = System.currentTimeMillis();
        List<TransactionAcqEntity> acquirerCandidates = findAcquirerCandidatesForBatch(
          erpBatch,
          reconcileAlreadyReconciled,
          pendingStatuses,
          lookbackDate,
          redeAcquirerId
        );
        long tAcqFetch = System.currentTimeMillis() - t0;
        Map<ErpAcquirerIdentityKey, List<TransactionAcqEntity>> acquirersByIdentity = indexAcquirerCandidates(acquirerCandidates);
        long tMatchStart = System.currentTimeMillis();

        List<TransactionErpEntity> changedErpSales = new ArrayList<>();
        List<TransactionAcqEntity> changedAcquirerSales = new ArrayList<>();
        Set<UUID> changedAcquirerIds = new HashSet<>();

        int batchAnalyzed = 0;
        int batchMatched = 0;
        int batchUpdated = 0;
        int batchNotMatched = 0;
        int batchValueDiv = 0;
        int batchAcqDiv = 0;
        int batchAmbiguous = 0;
        int batchSkipped = 0;
        int batchFlagUpd = 0;
        int batchCtxUpd = 0;

        for (TransactionErpEntity erp : erpBatch) {
          if (isExcludedFromCardReconciliation(erp)) {
            continue;
          }

          if (!reconcileAlreadyReconciled && !isPendingForErpAcquirerReconciliation(erp)) {
            continue;
          }

          batchAnalyzed++;

          List<TransactionAcqEntity> identityCandidates = acquirersByIdentity.getOrDefault(
            ErpAcquirerIdentityKey.fromErp(erp),
            List.of()
          );

          ErpAcquirerMatchResult matchResult = findBestAcquirerMatchForReconciliation(erp, identityCandidates);

          if (matchResult.status() == ErpAcquirerMatchStatus.NOT_MATCHED) {
            batchNotMatched++;
            if (applyErpReconciliationStatus(
              erp,
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ
            )) {
              batchUpdated++;
              changedErpSales.add(erp);
            }
            continue;
          }

          if (matchResult.status() == ErpAcquirerMatchStatus.VALUE_DIVERGENCE) {
            batchSkipped++;
            batchValueDiv++;
            if (applyErpReconciliationStatus(
              erp,
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.VALUE_MISMATCH
            )) {
              batchUpdated++;
              changedErpSales.add(erp);
            }

            int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
              matchResult.acquirerSales(),
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.VALUE_MISMATCH,
              changedAcquirerIds,
              changedAcquirerSales
            );
            batchUpdated += acquirerUpdated;
            continue;
          }

          if (matchResult.status() == ErpAcquirerMatchStatus.ACQUIRER_DIVERGENCE) {
            batchSkipped++;
            batchAcqDiv++;
            if (applyErpReconciliationStatus(
              erp,
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.ACQUIRER_MISMATCH
            )) {
              batchUpdated++;
              changedErpSales.add(erp);
            }

            int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
              matchResult.acquirerSales(),
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.ACQUIRER_MISMATCH,
              changedAcquirerIds,
              changedAcquirerSales
            );
            batchUpdated += acquirerUpdated;
            continue;
          }

          if (matchResult.status() == ErpAcquirerMatchStatus.AMBIGUOUS) {
            batchSkipped++;
            batchAmbiguous++;
            if (applyErpReconciliationStatus(
              erp,
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.AMBIGUOUS_MATCH
            )) {
              batchUpdated++;
              changedErpSales.add(erp);
            }

            int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
              matchResult.acquirerSales(),
              StatusReconciliationEnum.PENDING,
              StatusTransactionReasonEnum.AMBIGUOUS_MATCH,
              changedAcquirerIds,
              changedAcquirerSales
            );
            batchUpdated += acquirerUpdated;
            continue;
          }

          TransactionAcqEntity acq = matchResult.acquirerSale();
          batchMatched++;

          ErpAcquirerApplyResult applyResult = applyAcquirerBusinessContext(erp, acq);
          if (applyResult.changed()) {
            batchUpdated++;
            changedErpSales.add(erp);
          }
          if (applyResult.flagUpdated()) {
            batchFlagUpd++;
          }
          if (applyResult.businessContextUpdated()) {
            batchCtxUpd++;
          }

          // A conciliação ERP x Adquirente deve apenas parear a venda e atualizar o contexto comercial.
          // A análise/atualização de taxas e o feeReconciliationStatus são exclusivos do fluxo
          // reconcileErpAcquirerFees(...), para evitar reprocessamento indevido ou bloqueio prematuro.

          if (acq.getId() != null && changedAcquirerIds.add(acq.getId())) {
            changedAcquirerSales.add(acq);
          }
        }

        long tMatch = System.currentTimeMillis() - tMatchStart;

        long tFlushStart = System.currentTimeMillis();
        if (!changedErpSales.isEmpty()) {
          transactionErpRepository.saveAll(changedErpSales);
        }
        if (!changedAcquirerSales.isEmpty()) {
          transactionAcqRepository.saveAll(changedAcquirerSales);
        }
        entityManager.flush();
        entityManager.clear();
        long tFlush = System.currentTimeMillis() - tFlushStart;

        log.info(
          "⏱️ batch={}/{} erpFetch={}ms acqFetch={}ms match={}ms flush={}ms erp={} acq={}",
          currentBatch, totalBatches, tErpFetch, tAcqFetch, tMatch, tFlush,
          changedErpSales.size(), changedAcquirerSales.size()
        );

        return new ErpAcqBatchResult(batchAnalyzed, batchMatched, batchUpdated, batchSkipped,
          batchFlagUpd, batchCtxUpd, batchNotMatched, batchValueDiv, batchAcqDiv,
          batchAmbiguous, acquirerCandidates.size());
      });

      if (result == null) {
        continue;
      }

      analyzed += result.analyzed();
      matched += result.matched();
      updated += result.updated();
      skippedDivergent += result.skippedDivergent();
      flagUpdated += result.flagUpdated();
      businessContextUpdated += result.businessContextUpdated();
      notMatched += result.notMatched();
      valueDivergences += result.valueDivergences();
      acquirerDivergences += result.acquirerDivergences();
      ambiguousMatches += result.ambiguousMatches();

      log.info(
        "🔄 Conciliação ERP x Adquirente: batch={}/{}, erpAnalisadas={}, acqCandidatas={}, conciliadas={}, " +
          "atualizadas={}, totalAnalisadas={}, totalConciliadas={}",
        currentBatch,
        totalBatches,
        result.analyzed(),
        result.candidatesCount(),
        result.matched(),
        result.updated(),
        analyzed,
        matched
      );
    }

    int acquirerMissingErpUpdated = classifyRedeAcquirerSalesMissingInErp(
      reconcileAlreadyReconciled,
      pendingStatuses,
      implantationDate,
      lookbackDate,
      redeAcquirerId,
      batchTx
    );

    if (acquirerMissingErpUpdated > 0) {
      updated += acquirerMissingErpUpdated;
    }

    log.info(
      "✅ Conciliação ERP Vendas Rede x Adquirente Rede finalizada. trigger={}, analisadas={}, conciliadas={}, atualizadas={}, divergentes={}, " +
        "semMatchErp={}, semErpNaAdquirenteAtualizadas={}, valorDivergente={}, adquirenteDivergente={}, ambiguas={}",
      trigger,
      analyzed,
      matched,
      updated,
      skippedDivergent,
      notMatched,
      acquirerMissingErpUpdated,
      valueDivergences,
      acquirerDivergences,
      ambiguousMatches
    );

    return new ReconcileErpAcquirerResultModel(
      analyzed,
      matched,
      updated,
      skippedDivergent,
      flagUpdated,
      businessContextUpdated,
      notMatched,
      valueDivergences,
      acquirerDivergences,
      ambiguousMatches
    );
  }

  @Transactional(readOnly = true)
  public List<ConciliationAgingModel> aging() {
    List<ConciliationAgingModel> items = new ArrayList<>();
    addAging(items, "ERP_PENDENTE_COMERCIAL", transactionErpRepository.findAll().stream()
      .filter(t -> t.getCommercialStatus() != null && t.getCommercialStatus() != ErpCommercialStatusEnum.OK)
      .map(AgingItem::fromErp));
    addAging(items, "DEBITO_PENDENTE", pendingDebtRepository.findAll().stream()
      .filter(debt -> !debitChargebackClassifier.isChargeback(debt))
      .map(AgingItem::fromPendingDebt));
    addAging(items, "CHARGEBACK_ABERTO", Stream.concat(
      pendingDebtRepository.findAll().stream()
        .filter(debitChargebackClassifier::isChargeback)
        .map(AgingItem::fromPendingDebt),
      adjustmentRepository.findAll().stream()
        .filter(debitChargebackClassifier::isChargeback)
        .map(adjustment -> AgingItem.fromAdjustment(adjustment, debitChargebackClassifier.debitValue(adjustment)))
    ));
    addAging(items, "ORDEM_CREDITO_SEM_BANCO", creditOrderRepository.findAll().stream()
      .filter(co -> co.getReleaseBank() == null)
      .map(AgingItem::fromCreditOrder));
    return items;
  }

  private List<ConciliationChartPointModel> salesByPeriod(List<TransactionErpEntity> erpSales, List<TransactionAcqEntity> acquirerSales) {
    Map<String, BigDecimal> totals = new TreeMap<>();
    erpSales.forEach(s -> totals.merge(periodLabel(s.getSaleDate()), nz(s.getGrossValue()), BigDecimal::add));
    acquirerSales.forEach(s -> totals.merge(periodLabel(s.getSaleDate()), nz(s.getGrossValue()), BigDecimal::add));
    return totals.entrySet().stream().map(e -> new ConciliationChartPointModel(e.getKey(), e.getValue(), null)).toList();
  }

  private List<ConciliationChartPointModel> feesByAcquirer(List<FeeAnalysisResult> fees) {
    Map<String, BigDecimal> totals = new TreeMap<>();
    Map<String, Long> counts = new HashMap<>();
    fees.forEach(fee -> {
      String label = Optional.ofNullable(fee.acquirer()).orElse("Sem adquirente");
      totals.merge(label, nz(fee.appliedFeeValue()), BigDecimal::add);
      counts.merge(label, 1L, Long::sum);
    });
    return totals.entrySet().stream().map(e -> new ConciliationChartPointModel(e.getKey(), e.getValue(), counts.get(e.getKey()))).toList();
  }

  private List<ConciliationChartPointModel> divergencesByType(
    BigDecimal erpGross, BigDecimal acquirerGross, List<PendingDebtEntity> pendingDebts, List<AdjustmentEntity> adjustments,
    List<CreditOrderEntity> creditOrders, List<ReleasesBankEntity> bankReleases, List<FeeAnalysisResult> fees) {
    List<FeeAnalysisResult> feeDivergences = fees.stream().filter(fee -> !"OK".equals(fee.status())).toList();
    List<PendingDebtEntity> chargebackDebts = pendingDebts.stream().filter(debitChargebackClassifier::isChargeback).toList();
    List<AdjustmentEntity> chargebackAdjustments = adjustments.stream().filter(debitChargebackClassifier::isChargeback).toList();
    return List.of(
      new ConciliationChartPointModel("ERP_X_ADQUIRENTE", erpGross.subtract(acquirerGross).abs(), erpGross.compareTo(acquirerGross) == 0 ? 0L : 1L),
      new ConciliationChartPointModel("TAXAS_DIVERGENTES", sum(feeDivergences.stream().map(FeeAnalysisResult::feeDifference).map(this::abs)), (long) feeDivergences.size()),
      new ConciliationChartPointModel("DEBITO_PENDENTE", sum(pendingDebts.stream().filter(debt -> !debitChargebackClassifier.isChargeback(debt)).map(PendingDebtEntity::getPendingValue)), pendingDebts.stream().filter(debt -> !debitChargebackClassifier.isChargeback(debt)).count()),
      new ConciliationChartPointModel("CHARGEBACK_ABERTO", sum(chargebackDebts.stream().map(PendingDebtEntity::getPendingValue)).add(sum(chargebackAdjustments.stream().map(debitChargebackClassifier::debitValue))), (long) chargebackDebts.size() + chargebackAdjustments.size()),
      new ConciliationChartPointModel("AJUSTES", sum(adjustments.stream().map(AdjustmentEntity::getAdjustmentValue).map(this::abs)), (long) adjustments.size()),
      new ConciliationChartPointModel("ORDEM_CREDITO_SEM_BANCO", sum(creditOrders.stream().filter(co -> co.getReleaseBank() == null).map(CreditOrderEntity::getReleaseValue)), creditOrders.stream().filter(co -> co.getReleaseBank() == null).count()),
      new ConciliationChartPointModel("BANCO_NAO_CONCILIADO", sum(bankReleases.stream().filter(r -> !isReconciled(r)).map(ReleasesBankEntity::getReleaseValue)), bankReleases.stream().filter(r -> !isReconciled(r)).count())
    );
  }

  ErpAcquirerApplyResult applyAcquirerBusinessContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    boolean changed = false;
    boolean flagUpdated = false;
    boolean businessContextUpdated = false;

    if (acq != null && !sameId(erp.getTransactionAcq(), acq)) {
      erp.setTransactionAcq(acq);
      changed = true;
      businessContextUpdated = true;
    }

    FileProcessingProperties.Erp erpConfig = fileProcessingProperties.getErp();
    boolean erpInformsCompany = erpConfig != null && erpConfig.isInformsCompany();
    boolean erpInformsEstablishment = erpConfig != null && erpConfig.isInformsEstablishment();

    if (shouldUpdateCompanyFromAcquirer(erp, acq, erpInformsCompany)) {
      erp.setCompany(acq.getCompany());
      changed = true;
      businessContextUpdated = true;
    }

    if (shouldUpdateEstablishmentFromAcquirer(erp, acq, erpInformsEstablishment)) {
      erp.setEstablishment(acq.getEstablishment());
      changed = true;
      businessContextUpdated = true;
    }

    boolean sourceContextUpdated = applyAcquirerSourceContext(erp, acq);
    if (sourceContextUpdated) {
      changed = true;
      businessContextUpdated = true;
    }

    if (acq.getAcquirer() != null && !sameId(erp.getAcquirer(), acq.getAcquirer())) {
      erp.setAcquirer(acq.getAcquirer());
      changed = true;
      businessContextUpdated = true;
    }

    if (acq.getFlag() != null && !sameId(erp.getFlag(), acq.getFlag())) {
      erp.setFlag(acq.getFlag());
      changed = true;
      flagUpdated = true;
    }

    BankingDomicileEntity acquirerBankingDomicile = resolveAcquirerBankingDomicile(acq);
    if (acquirerBankingDomicile != null && !sameId(erp.getBankingDomicile(), acquirerBankingDomicile)) {
      erp.setBankingDomicile(acquirerBankingDomicile);
      changed = true;
      businessContextUpdated = true;
    }

    AdjustmentEntity acquirerAdjustment = resolveAcquirerAdjustment(acq);
    if (acquirerAdjustment != null && !sameId(erp.getAdjustment(), acquirerAdjustment)) {
      erp.setAdjustment(acquirerAdjustment);
      changed = true;
      businessContextUpdated = true;
    }

    OffsetDateTime reconciliationDate = firstNonNull(erp.getSaleReconciliationDate(), acq.getSaleReconciliationDate(), OffsetDateTime.now());

    if (erp.getSaleReconciliationDate() == null) {
      erp.setSaleReconciliationDate(reconciliationDate);
      changed = true;
    }

    if (acq.getSaleReconciliationDate() == null) {
      acq.setSaleReconciliationDate(reconciliationDate);
    }

    changed |= applyErpReconciliationStatus(
      erp,
      StatusReconciliationEnum.RECONCILED,
      StatusTransactionReasonEnum.SCHEDULED
    );

    applyAcquirerReconciliationStatus(
      acq,
      StatusReconciliationEnum.RECONCILED,
      StatusTransactionReasonEnum.SCHEDULED
    );

    if (erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_COMPANY
      || erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_ESTABLISHMENT
      || erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT
      || erp.getCommercialStatus() == null) {
      erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
      erp.setCommercialStatusMessage(null);
      changed = true;
    }

    return new ErpAcquirerApplyResult(changed, flagUpdated, businessContextUpdated);
  }

  boolean applyErpReconciliationStatus(
    TransactionErpEntity erp, StatusReconciliationEnum status, StatusTransactionReasonEnum reason) {
    if (erp == null || status == null || isFinalErpStatusTransaction(erp)) {
      return false;
    }

    StatusTransactionReasonEnum normalizedReason = normalizeReasonForStatus(status, reason);

    boolean changed = false;
    changed |= setIfDifferent(erp::getStatusTransaction, erp::setStatusTransaction, StatusTransactionEnum.fromCode(status.getCode()));
    changed |= setIfDifferent(erp::getStatusTransactionReason, erp::setStatusTransactionReason, reasonCode(normalizedReason));
    return changed;
  }

  boolean applyAcquirerReconciliationStatus(
    TransactionAcqEntity acq, StatusReconciliationEnum status, StatusTransactionReasonEnum reason) {
    if (acq == null || status == null || isFinalAcquirerStatusTransaction(acq)) {
      return false;
    }

    StatusTransactionReasonEnum normalizedReason = normalizeReasonForStatus(status, reason);

    boolean changed = false;
    changed |= setIfDifferent(acq::getStatusTransaction, acq::setStatusTransaction, StatusTransactionEnum.fromCode(status.getCode()));
    changed |= setIfDifferent(acq::getStatusTransactionReason, acq::setStatusTransactionReason, reasonCode(normalizedReason));
    return changed;
  }

  private int applyAcquirerReconciliationStatusToCandidates(
    List<TransactionAcqEntity> candidates, StatusReconciliationEnum status, StatusTransactionReasonEnum reason,
    Set<UUID> changedAcquirerIds, List<TransactionAcqEntity> changedAcquirerSales) {
    if (candidates == null || candidates.isEmpty()) {
      return 0;
    }

    int updated = 0;

    for (TransactionAcqEntity acq : candidates) {
      if (acq == null) {
        continue;
      }

      if (applyAcquirerReconciliationStatus(acq, status, reason)) {
        updated++;

        if (acq.getId() == null || changedAcquirerIds.add(acq.getId())) {
          changedAcquirerSales.add(acq);
        }
      }
    }

    return updated;
  }

  private StatusTransactionReasonEnum normalizeReasonForStatus(
    StatusReconciliationEnum status, StatusTransactionReasonEnum reason) {
    if (status == StatusReconciliationEnum.RECONCILED) {
      return StatusTransactionReasonEnum.SCHEDULED;
    }

    return reason;
  }

  private boolean isFinalErpStatusTransaction(TransactionErpEntity erp) {
    return isFinalStatusTransaction(erp.getStatusTransaction());
  }

  private boolean isFinalAcquirerStatusTransaction(TransactionAcqEntity acq) {
    return isFinalStatusTransaction(acq.getStatusTransaction());
  }

  private boolean isFinalStatusTransaction(StatusTransactionEnum status) {
    return status == StatusTransactionEnum.CANCELED || status == StatusTransactionEnum.DELETED;
  }

  private int classifyRedeAcquirerSalesMissingInErp(boolean reconcileAlreadyReconciled, List<Integer> pendingStatuses, OffsetDateTime implantationDate, OffsetDateTime lookbackDate, UUID redeAcquirerId, TransactionTemplate batchTx) {
    int totalUpdated = 0;
    int batchNumber = 0;

    while (true) {
      int[] batchResult = batchTx.execute(status -> {
        List<UUID> acquirerIds = transactionAcqRepository.findRedeAcqIdsForMissingInErpClassification(
          PageRequest.of(0, ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE),
          reconcileAlreadyReconciled,
          pendingStatuses,
          StatusTransactionEnum.PENDING.getCode(),
          StatusTransactionReasonEnum.NULL.getCode(),
          EXCLUDED_CARD_RECONCILIATION_MODALITY,
          implantationDate,
          lookbackDate,
          redeAcquirerId
        );

        if (acquirerIds.isEmpty()) {
          return null;
        }

        List<TransactionAcqEntity> acquirerBatch = transactionAcqRepository.findBatchForMissingInErpStatusClassification(acquirerIds);
        List<TransactionAcqEntity> changedAcquirerSales = new ArrayList<>();

        for (TransactionAcqEntity acq : acquirerBatch) {
          if (isExcludedFromCardReconciliation(acq)) {
            continue;
          }

          if (!reconcileAlreadyReconciled && !isPendingForErpAcquirerReconciliation(acq)) {
            continue;
          }

          if (applyAcquirerReconciliationStatus(
            acq,
            StatusReconciliationEnum.PENDING,
            StatusTransactionReasonEnum.CV_NOT_FOUND_ERP
          )) {
            changedAcquirerSales.add(acq);
          }
        }

        if (changedAcquirerSales.isEmpty()) {
          return new int[]{-1, acquirerBatch.size()};
        }

        transactionAcqRepository.saveAll(changedAcquirerSales);
        return new int[]{changedAcquirerSales.size(), acquirerBatch.size()};
      });

      if (batchResult == null || batchResult[0] < 0) {
        break;
      }

      totalUpdated += batchResult[0];
      batchNumber++;

      log.info(
        "🔎 Classificação de vendas da adquirente sem ERP: batch={}, analisadas={}, atualizadas={}",
        batchNumber,
        batchResult[1],
        batchResult[0]
      );
    }

    return totalUpdated;
  }

  boolean isExcludedFromCardReconciliation(TransactionErpEntity erp) {
    return erp == null
      || erp.getModality() == null
      || Objects.equals(erp.getModality(), EXCLUDED_CARD_RECONCILIATION_MODALITY);
  }

  private boolean isExcludedFromCardReconciliation(TransactionAcqEntity acq) {
    return acq == null
      || acq.getModality() == null
      || Objects.equals(acq.getModality(), EXCLUDED_CARD_RECONCILIATION_MODALITY);
  }

  boolean shouldReconcileAlreadyReconciledErpAcquirerSales() {
    return fileProcessingProperties != null
      && fileProcessingProperties.getReconciliation() != null
      && fileProcessingProperties.getReconciliation().isReconcileAlreadyReconciledErpAcquirerSales();
  }

  boolean isPendingForErpAcquirerReconciliation(TransactionErpEntity erp) {
    if (erp == null) {
      return false;
    }

    return isPendingStatusTransaction(erp.getStatusTransaction())
      && erp.getSaleReconciliationDate() == null;
  }

  private boolean isPendingForErpAcquirerReconciliation(TransactionAcqEntity acq) {
    if (acq == null) {
      return false;
    }

    return isPendingStatusTransaction(acq.getStatusTransaction())
      && acq.getSaleReconciliationDate() == null;
  }

  private boolean isPendingStatusTransaction(StatusTransactionEnum status) {
    return status == null
      || status == StatusTransactionEnum.NULL
      || status == StatusTransactionEnum.PENDING;
  }

  private StatusTransactionReasonEnum reasonCode(StatusTransactionReasonEnum reason) {
    return reason != null ? reason : StatusTransactionReasonEnum.NULL;
  }

  private AdjustmentEntity resolveAcquirerAdjustment(TransactionAcqEntity acq) {
    if (acq == null) {
      return null;
    }

    /*
     * Importante para performance:
     * a conciliação em lote pode passar por centenas de milhares de vendas.
     * Por isso, não fazemos lookup no AdjustmentRepository aqui.
     * O ajuste deve ser vinculado previamente à TransactionAcq pelo processamento Rede
     * via AdjustmentTransactionLinkService. Na conciliação, apenas propagamos
     * acq.adjustment para erp.adjustment.
     */
    return acq.getAdjustment();
  }

  private BankingDomicileEntity resolveAcquirerBankingDomicile(TransactionAcqEntity acq) {
    if (acq == null || acq.getSalesSummary() == null) {
      return null;
    }
    return acq.getSalesSummary().getBankingDomicile();
  }

  private boolean applyAcquirerSourceContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    boolean changed = false;

    CompanyEntity company = acq.getCompany() != null ? acq.getCompany()
      : (acq.getEstablishment() != null ? acq.getEstablishment().getCompany() : null);
    EstablishmentEntity establishment = acq.getEstablishment();

    if (company != null) {
      changed |= setIfDifferent(erp::getSourceCompanyCnpj, erp::setSourceCompanyCnpj, company.getCnpj());
      changed |= setIfDifferent(erp::getSourceCompanyName, erp::setSourceCompanyName, companyName(company));
    }

    if (establishment != null) {
      changed |= setIfDifferent(
        erp::getSourceEstablishmentPvNumber,
        erp::setSourceEstablishmentPvNumber,
        establishment.getPvNumber()
      );
    }

    return changed;
  }

  private <T> boolean setIfDifferent(Supplier<T> getter, Consumer<T> setter, T newValue) {
    if (newValue == null || Objects.equals(getter.get(), newValue)) return false;
    setter.accept(newValue);
    return true;
  }

  private boolean shouldUpdateCompanyFromAcquirer(TransactionErpEntity erp, TransactionAcqEntity acq, boolean erpInformsCompany) {
    if (acq.getCompany() == null) return false;
    if (!erpInformsCompany) return !sameId(erp.getCompany(), acq.getCompany());
    return erp.getCompany() == null;
  }

  private boolean shouldUpdateEstablishmentFromAcquirer(TransactionErpEntity erp, TransactionAcqEntity acq, boolean erpInformsEstablishment) {
    if (acq.getEstablishment() == null) return false;
    if (!erpInformsEstablishment) return !sameId(erp.getEstablishment(), acq.getEstablishment());
    return erp.getEstablishment() == null;
  }

  List<Integer> erpAcquirerPendingStatusCodes() {
    return List.of(
      StatusTransactionEnum.NULL.getCode(),
      StatusTransactionEnum.PENDING.getCode()
    );
  }

  List<TransactionAcqEntity> findAcquirerCandidatesForBatch(
    List<TransactionErpEntity> erpBatch, boolean reconcileAlreadyReconciled, List<Integer> pendingStatuses,
    OffsetDateTime lookbackDate, UUID redeAcquirerId) {
    Set<Long> nsus = erpBatch.stream()
      .filter(erp -> !isExcludedFromCardReconciliation(erp))
      .map(TransactionErpEntity::getNsu)
      .filter(Objects::nonNull)
      .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

    Set<String> authorizations = erpBatch.stream()
      .filter(erp -> !isExcludedFromCardReconciliation(erp))
      .map(TransactionErpEntity::getAuthorization)
      .map(this::normalizeLookupText)
      .filter(Objects::nonNull)
      .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

    Map<UUID, TransactionAcqEntity> candidates = new LinkedHashMap<>();

    OffsetDateTime implantationDate = appProperties.getImplantationDate().atStartOfDay().atOffset(ZoneOffset.UTC);

    if (!nsus.isEmpty()) {
      transactionAcqRepository.findRedeAcqCandidatesForReconciliationByNsus(
        nsus,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        implantationDate,
        lookbackDate,
        redeAcquirerId
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    if (!authorizations.isEmpty()) {
      transactionAcqRepository.findRedeAcqCandidatesForReconciliationByAuthorizations(
        authorizations,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        implantationDate,
        lookbackDate,
        redeAcquirerId
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    return candidates.values().stream()
      .filter(acq -> !isExcludedFromCardReconciliation(acq))
      .toList();
  }

  /**
   * Busca candidatas da adquirente para o fluxo MANUAL com NSU/autorização invertidos.
   *
   * A busca padrão cruza NSU-do-ERP com NSU-da-ADQ e autorização-do-ERP com
   * autorização-da-ADQ. Quando os campos do ERP estão trocados, esse cruzamento nunca
   * encontra a venda correta. Aqui fazemos o cruzamento INVERTIDO: procuramos ADQ cujo
   * NSU esteja nas AUTORIZAÇÕES do ERP, e ADQ cuja AUTORIZAÇÃO esteja nos NSUs do ERP.
   */
  List<TransactionAcqEntity> findAcquirerCandidatesForBatchSwapped(
    List<TransactionErpEntity> erpBatch, boolean reconcileAlreadyReconciled, List<Integer> pendingStatuses,
    OffsetDateTime lookbackDate, UUID redeAcquirerId) {

    // NSUs candidatos = autorizações do ERP convertidas para número.
    Set<Long> swappedNsus = erpBatch.stream()
      .filter(erp -> !isExcludedFromCardReconciliation(erp))
      .map(erp -> parseLongOrNull(erp.getAuthorization()))
      .filter(Objects::nonNull)
      .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

    // Autorizações candidatas = NSUs do ERP como texto.
    Set<String> swappedAuthorizations = erpBatch.stream()
      .filter(erp -> !isExcludedFromCardReconciliation(erp))
      .map(TransactionErpEntity::getNsu)
      .filter(Objects::nonNull)
      .map(nsu -> normalizeLookupText(String.valueOf(nsu)))
      .filter(Objects::nonNull)
      .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

    Map<UUID, TransactionAcqEntity> candidates = new LinkedHashMap<>();

    OffsetDateTime implantationDateSwapped = appProperties.getImplantationDate().atStartOfDay().atOffset(ZoneOffset.UTC);

    if (!swappedNsus.isEmpty()) {
      transactionAcqRepository.findRedeAcqCandidatesForReconciliationByNsus(
        swappedNsus,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        implantationDateSwapped,
        lookbackDate,
        redeAcquirerId
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    if (!swappedAuthorizations.isEmpty()) {
      transactionAcqRepository.findRedeAcqCandidatesForReconciliationByAuthorizations(
        swappedAuthorizations,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        implantationDateSwapped,
        lookbackDate,
        redeAcquirerId
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    return candidates.values().stream()
      .filter(acq -> !isExcludedFromCardReconciliation(acq))
      .toList();
  }

  Map<ErpAcquirerIdentityKey, List<TransactionAcqEntity>> indexAcquirerCandidates(List<TransactionAcqEntity> acquirerCandidates) {
    Map<ErpAcquirerIdentityKey, List<TransactionAcqEntity>> index = new HashMap<>();

    for (TransactionAcqEntity acq : acquirerCandidates) {
      ErpAcquirerIdentityKey key = ErpAcquirerIdentityKey.fromAcq(acq);
      if (!key.isUsable()) {
        continue;
      }
      index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(acq);
    }

    return index;
  }

  private String normalizeLookupText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private ErpAcquirerMatchResult findBestAcquirerMatchForReconciliation(TransactionErpEntity erp, List<TransactionAcqEntity> acquirerSales) {
    return findBestAcquirerMatchForReconciliation(erp, acquirerSales, false);
  }

  /**
   * Quando {@code swapNsuAuth} é true, compara o NSU do ERP contra a autorização da
   * adquirente e a autorização do ERP contra o NSU da adquirente. Usado pela
   * conciliação de transações manuais com NSU/autorização invertidos. As demais
   * regras (valor, adquirente, score, ambiguidade) permanecem idênticas.
   */
  ErpAcquirerMatchResult findBestAcquirerMatchForReconciliation(
    TransactionErpEntity erp,
    List<TransactionAcqEntity> acquirerSales,
    boolean swapNsuAuth
  ) {
    if (erp == null || acquirerSales == null || acquirerSales.isEmpty()) {
      return ErpAcquirerMatchResult.notMatched();
    }

    Long erpNsuForMatch = swapNsuAuth ? parseLongOrNull(erp.getAuthorization()) : erp.getNsu();
    String erpAuthForMatch = swapNsuAuth
      ? (erp.getNsu() != null ? String.valueOf(erp.getNsu()) : null)
      : erp.getAuthorization();

    List<TransactionAcqEntity> sameIdentity = acquirerSales.stream()
      .filter(acq -> swapNsuAuth
        ? sameIdentityText(erpAuthForMatch, acq.getAuthorization())
        : sameText(erpAuthForMatch, acq.getAuthorization()))
      .filter(acq -> erpNsuForMatch != null && Objects.equals(erpNsuForMatch, acq.getNsu()))
      .toList();

    if (sameIdentity.isEmpty()) {
      return ErpAcquirerMatchResult.notMatched();
    }

    // Janela de data da venda: aplicada apenas na conciliação manual (swap), onde a
    // data digitada pode divergir bastante. Mantém só candidatas cuja saleDate esteja
    // dentro da tolerância (em dias) configurada em relação à saleDate do ERP.
    if (swapNsuAuth) {
      int toleranceDays = manualSwapSaleDateToleranceDays();
      sameIdentity = sameIdentity.stream()
        .filter(acq -> withinSaleDateWindow(erp.getSaleDate(), acq.getSaleDate(), toleranceDays))
        .toList();

      if (sameIdentity.isEmpty()) {
        return ErpAcquirerMatchResult.notMatched();
      }
    } else {
      int backwardDays = erpAcquirerPreviousDaysLookback();
      int forwardDays = erpAcquirerFutureDaysLookback();
      if (backwardDays > 0 || forwardDays > 0) {
        sameIdentity = sameIdentity.stream()
          .filter(acq -> withinAsymmetricSaleDateWindow(erp.getSaleDate(), acq.getSaleDate(), backwardDays, forwardDays))
          .toList();
        if (sameIdentity.isEmpty()) {
          return ErpAcquirerMatchResult.notMatched();
        }
      }
    }

    List<TransactionAcqEntity> sameValue = sameIdentity.stream()
      .filter(acq -> sameValue(erp.getGrossValue(), acq.getGrossValue(), reconciliationValueTolerance()))
      .toList();

    if (sameValue.isEmpty()) {
      return ErpAcquirerMatchResult.valueDivergence(sameIdentity);
    }

    List<TransactionAcqEntity> sameAcquirer = sameValue.stream()
      .filter(acq -> sameAcquirerForReconciliation(erp, acq))
      .toList();

    if (sameAcquirer.isEmpty()) {
      return ErpAcquirerMatchResult.acquirerDivergence(sameValue);
    }

    int bestScore = sameAcquirer.stream()
      .mapToInt(acq -> matchScore(erp, acq))
      .max()
      .orElse(0);

    List<TransactionAcqEntity> best = sameAcquirer.stream()
      .filter(acq -> matchScore(erp, acq) == bestScore)
      .toList();

    if (best.size() != 1) {
      return ErpAcquirerMatchResult.ambiguous(best);
    }

    return ErpAcquirerMatchResult.matched(best.get(0));
  }

  private static Long parseLongOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean sameAcquirerForReconciliation(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq.getAcquirer() == null) return false;
    if (erp.getAcquirer() == null) return true;
    return sameId(erp.getAcquirer(), acq.getAcquirer());
  }

  private BigDecimal reconciliationValueTolerance() {
    FileProcessingProperties.Reconciliation reconciliation = fileProcessingProperties.getReconciliation();
    return reconciliation != null ? reconciliation.valueToleranceAsBigDecimal() : VALUE_TOLERANCE;
  }

  private int manualSwapSaleDateToleranceDays() {
    FileProcessingProperties.Reconciliation reconciliation = fileProcessingProperties.getReconciliation();
    int days = reconciliation != null ? reconciliation.getManualSwapSaleDateToleranceDays() : 60;
    return days > 0 ? days : 60;
  }

  private int erpAcquirerPreviousDaysLookback() {
    return reconciliationSettingsService.getErpAcquirerPreviousDaysLookback();
  }

  private int erpAcquirerFutureDaysLookback() {
    return reconciliationSettingsService.getErpAcquirerFutureDaysLookback();
  }

  private OffsetDateTime reconciliationLookbackDate() {
    int months = reconciliationSettingsService.getReconciliationLookbackMonths();
    return LocalDate.now().minusMonths(months).atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  /**
   * Janela assimétrica em torno da data de venda do ERP. Permite que a adquirente tenha
   * registrado a transação até {@code backwardDays} dias antes ou até {@code forwardDays}
   * dias depois da data do ERP.
   */
  private boolean withinAsymmetricSaleDateWindow(
    OffsetDateTime erpSaleDate, OffsetDateTime acqSaleDate, int backwardDays, int forwardDays
  ) {
    if (erpSaleDate == null || acqSaleDate == null) return false;
    LocalDate erpDate = erpSaleDate.toLocalDate();
    LocalDate acqDate = acqSaleDate.toLocalDate();
    return !acqDate.isBefore(erpDate.minusDays(backwardDays))
        && !acqDate.isAfter(erpDate.plusDays(forwardDays));
  }

  /**
   * Verdadeiro se as duas datas de venda estão dentro da janela (em dias). Se alguma
   * data for nula, considera fora da janela (não casa por data), pois não há como
   * garantir a proximidade temporal.
   */
  private boolean withinSaleDateWindow(OffsetDateTime erpSaleDate, OffsetDateTime acqSaleDate, int toleranceDays) {
    if (erpSaleDate == null || acqSaleDate == null) {
      return false;
    }
    long diffDays = Math.abs(ChronoUnit.DAYS.between(erpSaleDate.toLocalDate(), acqSaleDate.toLocalDate()));
    return diffDays <= toleranceDays;
  }

  private boolean sameValue(BigDecimal left, BigDecimal right, BigDecimal tolerance) {
    if (left == null || right == null) return false;
    BigDecimal effectiveTolerance = tolerance != null ? tolerance : VALUE_TOLERANCE;
    return left.subtract(right).abs().compareTo(effectiveTolerance) <= 0;
  }

  private int matchScore(TransactionErpEntity erp, TransactionAcqEntity acq) {
    int score = 0;
    if (erp.getNsu() != null && Objects.equals(erp.getNsu(), acq.getNsu())) score += 40;
    if (sameText(erp.getAuthorization(), acq.getAuthorization())) score += 40;
    if (sameText(erp.getTid(), acq.getTid())) score += 30;
    if (sameValue(erp.getGrossValue(), acq.getGrossValue(), reconciliationValueTolerance())) score += 20;
    if (sameId(erp.getAcquirer(), acq.getAcquirer())) score += 20;
    if (sameId(erp.getFlag(), acq.getFlag())) score += 10;
    if (erp.getSaleDate() != null && acq.getSaleDate() != null && erp.getSaleDate().toLocalDate().equals(acq.getSaleDate().toLocalDate())) score += 10;
    return score;
  }

  private boolean sameText(String left, String right) {
    return left != null && !left.isBlank() && right != null && left.equalsIgnoreCase(right);
  }

  /**
   * Comparação de identidade tolerante a zeros à esquerda. Usada no fluxo manual, onde
   * comparamos o NSU do ERP (Integer, sem zeros) contra a autorização da adquirente
   * (String, que pode ter zeros à esquerda). Para valores numéricos, ignora os zeros
   * à esquerda ("063451" == "63451"); para os demais, compara como texto.
   */
  private boolean sameIdentityText(String left, String right) {
    if (left == null || right == null || left.isBlank() || right.isBlank()) {
      return false;
    }
    String l = left.trim();
    String r = right.trim();
    if (l.chars().allMatch(Character::isDigit) && r.chars().allMatch(Character::isDigit)) {
      return l.replaceFirst("^0+", "").equals(r.replaceFirst("^0+", ""));
    }
    return l.equalsIgnoreCase(r);
  }

  private boolean sameId(AuditableEntityBase left, AuditableEntityBase right) {
    if (left == null || right == null) return false;
    return Objects.equals(left.getId(), right.getId());
  }

  record ErpAcquirerIdentityKey(Long nsu, String authorization) {
    static ErpAcquirerIdentityKey fromErp(TransactionErpEntity erp) {
      if (erp == null) {
        return new ErpAcquirerIdentityKey(null, null);
      }
      return new ErpAcquirerIdentityKey(erp.getNsu(), normalizeKeyText(erp.getAuthorization()));
    }

    /**
     * Chave do ERP com NSU e autorização TROCADOS. Usada pela conciliação de
     * transações manuais, onde por vezes o NSU vem gravado no campo de autorização
     * e vice-versa. O NSU passa a ser derivado da autorização (numérica) e a
     * autorização passa a ser o NSU original como texto.
     */
    static ErpAcquirerIdentityKey fromErpSwapped(TransactionErpEntity erp) {
      if (erp == null) {
        return new ErpAcquirerIdentityKey(null, null);
      }
      Long swappedNsu = parseLongOrNull(erp.getAuthorization());
      String swappedAuthorization = erp.getNsu() != null ? normalizeKeyText(String.valueOf(erp.getNsu())) : null;
      return new ErpAcquirerIdentityKey(swappedNsu, swappedAuthorization);
    }

    static ErpAcquirerIdentityKey fromAcq(TransactionAcqEntity acq) {
      if (acq == null) {
        return new ErpAcquirerIdentityKey(null, null);
      }
      return new ErpAcquirerIdentityKey(acq.getNsu(), normalizeKeyText(acq.getAuthorization()));
    }

    boolean isUsable() {
      return nsu != null && authorization != null;
    }

    private static String normalizeKeyText(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String trimmed = value.trim();
      // Autorização é String (preserva zeros à esquerda) e NSU é Integer (não preserva).
      // Quando o valor é puramente numérico, comparamos sem os zeros à esquerda para que
      // "063451" (autorização) e "63451" (NSU convertido) sejam considerados iguais.
      if (trimmed.chars().allMatch(Character::isDigit)) {
        String stripped = trimmed.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
      }
      return trimmed.toLowerCase(Locale.ROOT);
    }

    private static Long parseLongOrNull(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return Long.parseLong(value.trim());
      } catch (NumberFormatException ex) {
        return null;
      }
    }
  }

  enum ErpAcquirerMatchStatus {
    MATCHED,
    NOT_MATCHED,
    VALUE_DIVERGENCE,
    ACQUIRER_DIVERGENCE,
    AMBIGUOUS
  }

  record ErpAcquirerMatchResult(
    ErpAcquirerMatchStatus status, TransactionAcqEntity acquirerSale, List<TransactionAcqEntity> acquirerSales) {
    static ErpAcquirerMatchResult matched(TransactionAcqEntity acquirerSale) {
      return new ErpAcquirerMatchResult(
        ErpAcquirerMatchStatus.MATCHED,
        acquirerSale,
        acquirerSale != null ? List.of(acquirerSale) : List.of()
      );
    }

    static ErpAcquirerMatchResult notMatched() {
      return new ErpAcquirerMatchResult(ErpAcquirerMatchStatus.NOT_MATCHED, null, List.of());
    }

    static ErpAcquirerMatchResult valueDivergence(List<TransactionAcqEntity> acquirerSales) {
      return new ErpAcquirerMatchResult(ErpAcquirerMatchStatus.VALUE_DIVERGENCE, null, safeSales(acquirerSales));
    }

    static ErpAcquirerMatchResult acquirerDivergence(List<TransactionAcqEntity> acquirerSales) {
      return new ErpAcquirerMatchResult(ErpAcquirerMatchStatus.ACQUIRER_DIVERGENCE, null, safeSales(acquirerSales));
    }

    static ErpAcquirerMatchResult ambiguous(List<TransactionAcqEntity> acquirerSales) {
      return new ErpAcquirerMatchResult(ErpAcquirerMatchStatus.AMBIGUOUS, null, safeSales(acquirerSales));
    }

    private static List<TransactionAcqEntity> safeSales(List<TransactionAcqEntity> acquirerSales) {
      return acquirerSales != null ? acquirerSales : List.of();
    }
  }

  record ErpAcquirerApplyResult(boolean changed, boolean flagUpdated, boolean businessContextUpdated) {}

  private void addAging(List<ConciliationAgingModel> target, String type, Stream<AgingItem> source) {
    Map<String, List<AgingItem>> grouped = new LinkedHashMap<>();
    grouped.put("0-2 dias", new ArrayList<>());
    grouped.put("3-7 dias", new ArrayList<>());
    grouped.put("8-15 dias", new ArrayList<>());
    grouped.put("16-30 dias", new ArrayList<>());
    grouped.put("30+ dias", new ArrayList<>());
    source.forEach(item -> grouped.get(bucket(item.referenceDate())).add(item));
    grouped.forEach((bucket, items) -> target.add(new ConciliationAgingModel(bucket, items.size(), sum(items.stream().map(AgingItem::amount)), type)));
  }

  private String bucket(LocalDate date) {
    if (date == null) return "30+ dias";
    long days = Math.max(0, ChronoUnit.DAYS.between(date, LocalDate.now()));
    if (days <= 2) return "0-2 dias";
    if (days <= 7) return "3-7 dias";
    if (days <= 15) return "8-15 dias";
    if (days <= 30) return "16-30 dias";
    return "30+ dias";
  }

  private boolean isReconciled(ReleasesBankEntity entity) {
    return entity.getNumberReconciliations() != null && entity.getNumberReconciliations() > 0;
  }

  private boolean hasAnyReconciliationSignal(TransactionAcqEntity entity) {
    return entity.getSaleReconciliationDate() != null || entity.getStatusPaymentBank() != null || entity.getStatusTransaction() != null;
  }

  private long countDivergences(
    BigDecimal erpGross, BigDecimal acquirerGross, List<AdjustmentEntity> adjustments,
    List<PendingDebtEntity> pendingDebts, List<FeeAnalysisResult> fees) {
    long total = 0;
    if (erpGross.compareTo(acquirerGross) != 0) total++;
    total += adjustments.size();
    total += pendingDebts.size();
    total += fees.stream().filter(fee -> !"OK".equals(fee.status())).count();
    return total;
  }

  private BigDecimal sum(Stream<BigDecimal> values) {
    return values.filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal abs(BigDecimal value) {
    return value == null ? ZERO : value.abs();
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private String periodLabel(OffsetDateTime date) {
    if (date == null) return "Sem data";
    return date.toLocalDate().toString();
  }

  private String companyName(CompanyEntity company) {
    return company != null ? firstNonBlank(company.getFantasyName(), company.getSocialReason(), company.getCnpj()) : null;
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private record AgingItem(LocalDate referenceDate, BigDecimal amount) {
    static AgingItem fromErp(TransactionErpEntity entity) {
      return new AgingItem(entity.getSaleDate() != null ? entity.getSaleDate().toLocalDate() : null, entity.getGrossValue());
    }

    static AgingItem fromPendingDebt(PendingDebtEntity entity) {
      return new AgingItem(entity.getDateDebitOrder(), first(entity.getPendingValue(), entity.getValueDebitOrder()));
    }

    static AgingItem fromCreditOrder(CreditOrderEntity entity) {
      return new AgingItem(entity.getReleaseDate(), entity.getReleaseValue());
    }

    static AgingItem fromAdjustment(AdjustmentEntity entity, BigDecimal amount) {
      LocalDate referenceDate = entity.getAdjustmentDate() != null ? entity.getAdjustmentDate() : entity.getTransactionDate();
      return new AgingItem(referenceDate, amount);
    }

    private static BigDecimal first(BigDecimal first, BigDecimal second) {
      return first != null ? first : second;
    }
  }
}