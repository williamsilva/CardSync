package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

  private static final int ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE = 5_000;
  private static final Integer EXCLUDED_CARD_RECONCILIATION_MODALITY = ModalityEnum.DIGITAL_WALLET.getCode();

  private final EntityManager entityManager;
  private final AdjustmentRepository adjustmentRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final SettledDebtRepository settledDebtRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final FileProcessingProperties fileProcessingProperties;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final ConciliationFeeAnalysisService feeAnalysisService;
  private final ConciliationDebitChargebackClassifier debitChargebackClassifier;

  @Transactional(readOnly = true)
  public ConciliationDashboardModel dashboard() {
    List<TransactionErpEntity> erpSales = transactionErpRepository.findAll();
    List<TransactionAcqEntity> acquirerSales = transactionAcqRepository.findAll();
    List<CreditOrderEntity> creditOrders = creditOrderRepository.findAll();
    List<ReleasesBankEntity> bankReleases = releasesBankRepository.findAll();
    List<PendingDebtEntity> pendingDebts = pendingDebtRepository.findAll();
    List<SettledDebtEntity> settledDebts = settledDebtRepository.findAll();
    List<AdjustmentEntity> adjustments = adjustmentRepository.findAll();
    List<FeeAnalysisResult> feeAnalyses = acquirerSales.stream().map(feeAnalysisService::analyze).toList();

    BigDecimal erpGross = sum(erpSales.stream().map(TransactionErpEntity::getGrossValue));
    BigDecimal acquirerGross = sum(acquirerSales.stream().map(TransactionAcqEntity::getGrossValue));
    BigDecimal feeAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::appliedFeeValue));
    BigDecimal expectedFeeAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::expectedFeeValue));
    BigDecimal feeDifferenceAmount = sum(feeAnalyses.stream().map(FeeAnalysisResult::feeDifference));
    List<BankSettlementAnalysisModel> bankSettlementItems = buildBankSettlementItems();
    BigDecimal bankSettled = sum(bankSettlementItems.stream()
      .filter(item -> "LIQUIDATED".equals(item.status()) || "PARTIALLY_LIQUIDATED".equals(item.status()))
      .map(BankSettlementAnalysisModel::settledValue));
    BigDecimal bankPending = sum(bankSettlementItems.stream()
      .filter(item -> "PENDING".equals(item.status()) || "BANK_RELEASE_NOT_RECONCILED".equals(item.status()))
      .map(item -> firstNonNull(item.expectedValue(), item.settledValue())));
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
      bankSettled, bankPending,
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
  public ReconcileErpAcquirerFeesResultModel reconcileErpAcquirerFees() {
    return reconcileErpAcquirerFees("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerFeesResultModel reconcileErpAcquirerFees(String trigger) {
    List<Integer> reconciledStatuses = erpAcquirerReconciledStatusCodes();
    List<Integer> pendingFeeStatuses = pendingFeeReconciliationStatusCodes();
    List<UUID> erpIds = transactionErpRepository.findIdsForErpAcquirerFeeReconciliation(
      reconciledStatuses,
      EXCLUDED_CARD_RECONCILIATION_MODALITY,
      pendingFeeStatuses
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
      "📌 Iniciando conciliação de taxas ERP x Adquirente. trigger={}, totalErpPendentesTaxa={}, batchSize={}, " +
        "totalBatches={}, statusVenda={}, statusTaxaPendente={}",
      trigger,
      erpIds.size(),
      ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE,
      totalBatches,
      reconciledStatuses,
      pendingFeeStatuses
    );

    for (int start = 0; start < erpIds.size(); start += ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE) {
      batchNumber++;
      int end = Math.min(start + ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE, erpIds.size());
      List<UUID> batchIds = erpIds.subList(start, end);

      List<TransactionErpEntity> erpBatch = transactionErpRepository.findBatchForErpAcquirerFeeReconciliation(
        batchIds,
        reconciledStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY,
        pendingFeeStatuses
      );
      if (erpBatch.isEmpty()) {
        continue;
      }

      List<TransactionErpEntity> changedErpSales = new ArrayList<>();
      int batchAnalyzed = 0;
      int batchUpdated = 0;
      int batchDivergentRates = 0;
      int batchMissingContracts = 0;

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

      if (!changedErpSales.isEmpty()) {
        transactionErpRepository.saveAll(changedErpSales);
      }

      entityManager.flush();
      entityManager.clear();

      log.info(
        "🔄 Conciliação de taxas ERP x Adquirente: batch={}/{}, analisadas={}, erpAtualizadas={}, divergenciasTaxa={}, " +
          "semContratoValido={}, totalAnalisadas={}",
        batchNumber,
        totalBatches,
        batchAnalyzed,
        batchUpdated,
        batchDivergentRates,
        batchMissingContracts,
        analyzed
      );
    }

    log.info(
      "✅ Conciliação de taxas ERP x Adquirente finalizada. trigger={}, analisadas={}, erpAtualizadas={}, divergenciasTaxa={}, " +
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
  public ReconcileErpAcquirerResultModel reconcileErpWithAcquirerBusinessContext() {
    return reconcileErpWithAcquirerBusinessContext("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileErpWithAcquirerBusinessContext(String trigger) {
    boolean reconcileAlreadyReconciled = shouldReconcileAlreadyReconciledErpAcquirerSales();
    List<Integer> pendingStatuses = erpAcquirerPendingStatusCodes();

    List<UUID> erpIds = transactionErpRepository.findIdsForErpAcquirerReconciliation(
      reconcileAlreadyReconciled,
      pendingStatuses,
      EXCLUDED_CARD_RECONCILIATION_MODALITY
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
      "📌 Iniciando conciliação ERP x Adquirente em lotes. trigger={}, totalErp={}, batchSize={}, totalBatches={}, reconcileAlreadyReconciled={}",
      trigger,
      erpIds.size(),
      ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE,
      totalBatches,
      reconcileAlreadyReconciled
    );

    for (int start = 0; start < erpIds.size(); start += ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE) {
      batchNumber++;
      int end = Math.min(start + ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE, erpIds.size());
      List<UUID> batchIds = erpIds.subList(start, end);

      List<TransactionErpEntity> erpBatch = transactionErpRepository.findBatchForErpAcquirerReconciliation(batchIds);
      if (erpBatch.isEmpty()) {
        continue;
      }

      List<TransactionAcqEntity> acquirerCandidates = findAcquirerCandidatesForBatch(
        erpBatch,
        reconcileAlreadyReconciled,
        pendingStatuses
      );
      Map<ErpAcquirerIdentityKey, List<TransactionAcqEntity>> acquirersByIdentity = indexAcquirerCandidates(acquirerCandidates);

      List<TransactionErpEntity> changedErpSales = new ArrayList<>();
      List<TransactionAcqEntity> changedAcquirerSales = new ArrayList<>();
      Set<UUID> changedAcquirerIds = new HashSet<>();

      int batchAnalyzed = 0;
      int batchMatched = 0;
      int batchUpdated = 0;

      for (TransactionErpEntity erp : erpBatch) {
        if (isExcludedFromCardReconciliation(erp)) {
          continue;
        }

        if (!reconcileAlreadyReconciled && !isPendingForErpAcquirerReconciliation(erp)) {
          continue;
        }

        analyzed++;
        batchAnalyzed++;

        List<TransactionAcqEntity> identityCandidates = acquirersByIdentity.getOrDefault(
          ErpAcquirerIdentityKey.fromErp(erp),
          List.of()
        );

        ErpAcquirerMatchResult matchResult = findBestAcquirerMatchForReconciliation(erp, identityCandidates);

        if (matchResult.status() == ErpAcquirerMatchStatus.NOT_MATCHED) {
          notMatched++;
          if (applyErpReconciliationStatus(
            erp,
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ
          )) {
            updated++;
            batchUpdated++;
            changedErpSales.add(erp);
          }
          continue;
        }

        if (matchResult.status() == ErpAcquirerMatchStatus.VALUE_DIVERGENCE) {
          skippedDivergent++;
          valueDivergences++;
          if (applyErpReconciliationStatus(
            erp,
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.VALUE_MISMATCH
          )) {
            updated++;
            batchUpdated++;
            changedErpSales.add(erp);
          }

          int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
            matchResult.acquirerSales(),
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.VALUE_MISMATCH,
            changedAcquirerIds,
            changedAcquirerSales
          );
          updated += acquirerUpdated;
          batchUpdated += acquirerUpdated;
          continue;
        }

        if (matchResult.status() == ErpAcquirerMatchStatus.ACQUIRER_DIVERGENCE) {
          skippedDivergent++;
          acquirerDivergences++;
          if (applyErpReconciliationStatus(
            erp,
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.ACQUIRER_MISMATCH
          )) {
            updated++;
            batchUpdated++;
            changedErpSales.add(erp);
          }

          int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
            matchResult.acquirerSales(),
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.ACQUIRER_MISMATCH,
            changedAcquirerIds,
            changedAcquirerSales
          );
          updated += acquirerUpdated;
          batchUpdated += acquirerUpdated;
          continue;
        }

        if (matchResult.status() == ErpAcquirerMatchStatus.AMBIGUOUS) {
          skippedDivergent++;
          ambiguousMatches++;
          if (applyErpReconciliationStatus(
            erp,
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.AMBIGUOUS_MATCH
          )) {
            updated++;
            batchUpdated++;
            changedErpSales.add(erp);
          }

          int acquirerUpdated = applyAcquirerReconciliationStatusToCandidates(
            matchResult.acquirerSales(),
            StatusTransactionEnum.NOT_RECONCILED,
            StatusTransactionReasonEnum.AMBIGUOUS_MATCH,
            changedAcquirerIds,
            changedAcquirerSales
          );
          updated += acquirerUpdated;
          batchUpdated += acquirerUpdated;
          continue;
        }

        TransactionAcqEntity acq = matchResult.acquirerSale();
        matched++;
        batchMatched++;

        ErpAcquirerApplyResult applyResult = applyAcquirerBusinessContext(erp, acq);
        if (applyResult.changed()) {
          updated++;
          batchUpdated++;
          changedErpSales.add(erp);
        }
        if (applyResult.flagUpdated()) {
          flagUpdated++;
        }
        if (applyResult.businessContextUpdated()) {
          businessContextUpdated++;
        }

        // A conciliação ERP x Adquirente deve apenas parear a venda e atualizar o contexto comercial.
        // A análise/atualização de taxas e o feeReconciliationStatus são exclusivos do fluxo
        // reconcileErpAcquirerFees(...), para evitar reprocessamento indevido ou bloqueio prematuro.

        if (acq.getId() != null && changedAcquirerIds.add(acq.getId())) {
          changedAcquirerSales.add(acq);
        }
      }

      if (!changedErpSales.isEmpty()) {
        transactionErpRepository.saveAll(changedErpSales);
      }

      if (!changedAcquirerSales.isEmpty()) {
        transactionAcqRepository.saveAll(changedAcquirerSales);
      }

      entityManager.flush();
      entityManager.clear();

      log.info(
        "🔄 Conciliação ERP x Adquirente: batch={}/{}, erpAnalisadas={}, acqCandidatas={}, conciliadas={}, " +
          "atualizadas={}, totalAnalisadas={}, totalConciliadas={}",
        batchNumber,
        totalBatches,
        batchAnalyzed,
        acquirerCandidates.size(),
        batchMatched,
        batchUpdated,
        analyzed,
        matched
      );
    }

    int acquirerMissingErpUpdated = classifyAcquirerSalesMissingInErp(
      reconcileAlreadyReconciled,
      pendingStatuses
    );

    if (acquirerMissingErpUpdated > 0) {
      updated += acquirerMissingErpUpdated;
    }

    log.info(
      "✅ Conciliação ERP x Adquirente finalizada. trigger={}, analisadas={}, conciliadas={}, atualizadas={}, divergentes={}, " +
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
  public Page<DebitAnalysisModel> listDebits(Pageable pageable) {
    List<DebitAnalysisModel> items = new ArrayList<>();
    pendingDebtRepository.findAll().stream().map(this::toPendingDebitModel).forEach(items::add);
    settledDebtRepository.findAll().stream().map(this::toSettledDebitModel).forEach(items::add);
    adjustmentRepository.findAll().stream()
      .filter(debitChargebackClassifier::contributesToDebitAnalysis)
      .map(this::toAdjustmentDebitModel)
      .forEach(items::add);
    items.sort(Comparator.comparing(DebitAnalysisModel::debitDate, Comparator.nullsLast(Comparator.reverseOrder())));
    return page(items, pageable);
  }

  @Transactional(readOnly = true)
  public Page<BankSettlementAnalysisModel> listBankSettlement(Pageable pageable) {
    List<BankSettlementAnalysisModel> items = buildBankSettlementItems();
    items.sort(Comparator
      .comparing((BankSettlementAnalysisModel item) -> firstNonNull(item.settlementDate(), item.expectedDate()), Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(BankSettlementAnalysisModel::sourceType, Comparator.nullsLast(String::compareTo)));
    return page(items, pageable);
  }

  @Transactional(readOnly = true)
  public Page<DivergenceAnalysisModel> listDivergences(Pageable pageable) {
    List<TransactionErpEntity> erpSales = transactionErpRepository.findAll();
    List<TransactionAcqEntity> acquirerSales = transactionAcqRepository.findAll();
    List<DivergenceAnalysisModel> items = new ArrayList<>();

    erpSales.stream()
      .map(this::toErpVsAcquirerModel)
      .filter(item -> !"MATCHED".equals(item.status()))
      .map(this::toErpVsAcquirerDivergence)
      .forEach(items::add);

    acquirerSales.stream()
      .filter(acq -> !hasErpMatch(acq, erpSales))
      .map(this::toMissingInErpDivergence)
      .forEach(items::add);

    acquirerSales.stream()
      .map(feeAnalysisService::analyze)
      .filter(fee -> !"OK".equals(fee.status()))
      .map(this::toFeeDivergence)
      .forEach(items::add);

    buildBankSettlementItems().stream()
      .filter(item -> !"LIQUIDATED".equals(item.status()))
      .map(this::toBankSettlementDivergence)
      .forEach(items::add);

    transactionErpRepository.findAll().stream()
      .filter(t -> t.getCommercialStatus() != null && t.getCommercialStatus() != ErpCommercialStatusEnum.OK)
      .map(this::toCommercialPendingDivergence)
      .forEach(items::add);

    pendingDebtRepository.findAll().stream()
      .filter(debt -> !debitChargebackClassifier.isChargeback(debt))
      .map(this::toPendingDebtDivergence)
      .forEach(items::add);

    pendingDebtRepository.findAll().stream()
      .filter(debitChargebackClassifier::isChargeback)
      .map(this::toChargebackDivergence)
      .forEach(items::add);

    adjustmentRepository.findAll().stream()
      .filter(debitChargebackClassifier::contributesToDebitAnalysis)
      .map(this::toAdjustmentDivergence)
      .forEach(items::add);

    items.sort(Comparator
      .comparing(DivergenceAnalysisModel::referenceDate, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(DivergenceAnalysisModel::severity, Comparator.nullsLast(String::compareTo))
      .thenComparing(DivergenceAnalysisModel::type, Comparator.nullsLast(String::compareTo)));

    return page(items, pageable);
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

  private ErpVsAcquirerAnalysisModel toErpVsAcquirerModel(TransactionErpEntity erp) {
    Optional<TransactionAcqEntity> acq = findAcquirerMatch(erp);
    TransactionAcqEntity matched = acq.orElse(null);
    BigDecimal erpGross = nz(erp.getGrossValue());
    BigDecimal acqGross = matched != null ? nz(matched.getGrossValue()) : ZERO;
    return new ErpVsAcquirerAnalysisModel(
      erp.getId(),
      erp.getId(),
      matched != null ? matched.getId() : null,
      erp.getSaleDate(), matched != null ? matched.getSaleDate() : null,
      companyName(firstNonNull(erp.getCompany(), matched != null ? matched.getCompany() : null)),
      establishmentName(firstNonNull(erp.getEstablishment(), matched != null ? matched.getEstablishment() : null)),
      acquirerName(firstNonNull(erp.getAcquirer(), matched != null ? matched.getAcquirer() : null)),
      flagName(erp.getFlag()), matched != null ? flagName(matched.getFlag()) : null,
      modalityName(erp.getModality()), matched != null ? modalityName(matched.getModality()) : null,
      erp.getNsu(), matched != null ? matched.getNsu() : null,
      erp.getAuthorization(), matched != null ? matched.getAuthorization() : null,
      erp.getGrossValue(), matched != null ? matched.getGrossValue() : null,
      matched != null ? erpGross.subtract(acqGross) : erpGross,
      erp.getInstallment(), matched != null ? matched.getInstallment() : null,
      comparisonStatus(erp, matched)
    );
  }

  private DebitAnalysisModel toPendingDebitModel(PendingDebtEntity entity) {
    return new DebitAnalysisModel(
      entity.getId(), entity.getDateDebitOrder(), entity.getPaymentDate(), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), debitChargebackClassifier.type(entity).name(),
      code(entity.getReasonCode()), entity.getReasonDescription(), firstNonNull(entity.getPendingValue(), entity.getValueDebitOrder()),
      entity.getCompensatedValue(), debitChargebackClassifier.status(entity), fileName(entity.getProcessedFile())
    );
  }

  private DebitAnalysisModel toSettledDebitModel(SettledDebtEntity entity) {
    return new DebitAnalysisModel(
      entity.getId(), entity.getDateDebitOrder(), entity.getLiquidatedDate(), null, null,
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), debitChargebackClassifier.type(entity).name(), code(entity.getReasonCode()),
      entity.getReasonDescription(), entity.getValueDebitOrder(), entity.getLiquidatedValue(), debitChargebackClassifier.status(entity),
      fileName(entity.getProcessedFile())
    );
  }

  private DebitAnalysisModel toAdjustmentDebitModel(AdjustmentEntity entity) {
    return new DebitAnalysisModel(
      entity.getId(), debitChargebackClassifier.debitDate(entity), debitChargebackClassifier.settlementDate(entity),
      companyName(entity.getCompany()), establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()),
      flagName(firstNonNull(entity.getRvFlagAdjustment(), entity.getRvFlagOrigin())), debitChargebackClassifier.type(entity).name(),
      firstNonBlank(code(entity.getAdjustmentReason()), code(entity.getAdjustmentReason2()), entity.getRawAdjustmentCode()),
      firstNonBlank(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), entity.getSourceRecordIdentifier()),
      debitChargebackClassifier.debitValue(entity), debitChargebackClassifier.settledValue(entity),
      debitChargebackClassifier.status(entity), fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toErpVsAcquirerDivergence(ErpVsAcquirerAnalysisModel item) {
    String type = item.status();
    BigDecimal difference = abs(item.differenceValue());
    return new DivergenceAnalysisModel(
      item.id(), type, severityFor(type), "OPEN", "ERP_X_ACQUIRER", localDate(item.saleDateErp()),
      item.company(), item.establishment(), item.acquirer(), firstNonBlank(item.flagErp(), item.flagAcquirer()),
      firstNonBlank(item.modalityErp(), item.modalityAcquirer()), firstNonBlank(code(item.nsuErp()), item.authorizationErp(), item.authorizationAcquirer()),
      item.erpGrossValue(), item.acquirerGrossValue(), difference,
      messageForErpVsAcquirer(item), actionFor(type), null
    );
  }

  private DivergenceAnalysisModel toMissingInErpDivergence(TransactionAcqEntity entity) {
    return new DivergenceAnalysisModel(
      entity.getId(), "MISSING_IN_ERP", "HIGH", "OPEN", "ACQUIRER", localDate(entity.getSaleDate()),
      companyName(entity.getCompany()), establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()),
      flagName(entity.getFlag()), modalityName(entity.getModality()), firstNonBlank(code(entity.getNsu()), entity.getAuthorization(), entity.getTid()),
      null, entity.getGrossValue(), entity.getGrossValue(),
      "Venda da adquirente sem venda correspondente no ERP", "Verificar importação do ERP, NSU, autorização, TID e data da venda", fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toFeeDivergence(FeeAnalysisResult fee) {
    String type = "MISSING_CONTRACT".equals(fee.status()) ? "MISSING_CONTRACT" : "FEE_DIVERGENCE";
    return new DivergenceAnalysisModel(
      fee.id(), type, severityFor(type), "OPEN", "FEE", localDate(fee.saleDate()),
      fee.company(), fee.establishment(), fee.acquirer(), fee.flag(), fee.modality(), firstNonBlank(code(fee.nsu()), fee.authorization()),
      fee.expectedFeeValue(), fee.appliedFeeValue(), abs(fee.feeDifference()),
      feeMessage(fee), actionFor(type), null
    );
  }

  private DivergenceAnalysisModel toBankSettlementDivergence(BankSettlementAnalysisModel item) {
    String type = switch (item.status()) {
      case "PENDING" -> "BANK_SETTLEMENT_PENDING";
      case "BANK_RELEASE_NOT_RECONCILED" -> "BANK_RELEASE_NOT_RECONCILED";
      case "DATE_DIVERGENCE" -> "BANK_SETTLEMENT_DATE_DIVERGENCE";
      case "VALUE_DIVERGENCE", "PARTIALLY_LIQUIDATED" -> "BANK_SETTLEMENT_VALUE_DIVERGENCE";
      default -> "BANK_SETTLEMENT_DIVERGENCE";
    };
    return new DivergenceAnalysisModel(
      item.id(), type, severityFor(type), "OPEN", item.sourceType(), firstNonNull(item.settlementDate(), item.expectedDate()),
      item.company(), item.establishment(), item.acquirer(), item.flag(), item.modality(), firstNonBlank(code(item.creditOrderNumber()), item.releaseReference()),
      item.expectedValue(), item.settledValue(), abs(item.differenceValue()),
      firstNonBlank(item.detail(), "Divergência na liquidação bancária"), actionFor(type), null
    );
  }

  private DivergenceAnalysisModel toCommercialPendingDivergence(TransactionErpEntity entity) {
    String type = entity.getCommercialStatus() != null ? entity.getCommercialStatus().name() : "ERP_COMMERCIAL_PENDING";
    return new DivergenceAnalysisModel(
      entity.getId(), type, "HIGH", "OPEN", "ERP", localDate(entity.getSaleDate()), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), modalityName(entity.getModality()),
      firstNonBlank(code(entity.getNsu()), entity.getAuthorization(), entity.getTid()), entity.getGrossValue(), null, entity.getGrossValue(),
      firstNonBlank(entity.getCommercialStatusMessage(), "Venda ERP com pendência comercial"), actionFor(type), fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toPendingDebtDivergence(PendingDebtEntity entity) {
    BigDecimal amount = firstNonNull(entity.getPendingValue(), entity.getValueDebitOrder());
    return new DivergenceAnalysisModel(
      entity.getId(), "DEBIT_PENDING", "MEDIUM", "OPEN", "DEBIT", entity.getDateDebitOrder(), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), null,
      firstNonBlank(code(entity.getNumberDebitOrder()), code(entity.getNsu()), entity.getAuthorization(), entity.getTid()), amount, entity.getCompensatedValue(), abs(amount),
      firstNonBlank(entity.getReasonDescription(), "Débito pendente de compensação/liquidação"), actionFor("DEBIT_PENDING"), fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toChargebackDivergence(PendingDebtEntity entity) {
    BigDecimal amount = firstNonNull(entity.getPendingValue(), entity.getValueDebitOrder());
    return new DivergenceAnalysisModel(
      entity.getId(), "CHARGEBACK_OPEN", "CRITICAL", debitChargebackClassifier.chargebackStatus(entity).name(), "CHARGEBACK", entity.getDateDebitOrder(), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), null,
      firstNonBlank(code(entity.getNumberProcessChargeback()), code(entity.getNumberDebitOrder()), code(entity.getNsu()), entity.getAuthorization(), entity.getTid()),
      entity.getOriginalTransactionValue(), amount, abs(amount), firstNonBlank(entity.getReasonDescription(), "Chargeback/contestação em aberto"),
      actionFor("CHARGEBACK_OPEN"), fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toAdjustmentDivergence(AdjustmentEntity entity) {
    boolean chargeback = debitChargebackClassifier.isChargeback(entity);
    String type = chargeback ? "CHARGEBACK_ADJUSTMENT" : debitChargebackClassifier.type(entity).name();
    BigDecimal amount = debitChargebackClassifier.debitValue(entity);
    return new DivergenceAnalysisModel(
      entity.getId(), type, chargeback ? "CRITICAL" : "LOW", debitChargebackClassifier.status(entity), "ADJUSTMENT",
      firstNonNull(entity.getAdjustmentDate(), entity.getTransactionDate(), entity.getReleaseDate()), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(firstNonNull(entity.getRvFlagAdjustment(), entity.getRvFlagOrigin())), null,
      firstNonBlank(code(entity.getNumberDebitOrder()), code(entity.getNsu()), entity.getAuthorization(), entity.getTid(), code(entity.getRvNumberAdjustment())),
      firstNonNull(entity.getTransactionValue(), entity.getOriginalGrossSalesSummaryValue()), amount, abs(amount),
      firstNonBlank(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), "Ajuste da adquirente classificado para análise"),
      actionFor(type), fileName(entity.getProcessedFile())
    );
  }

  private boolean hasErpMatch(TransactionAcqEntity acq, List<TransactionErpEntity> erpSales) {
    return erpSales.stream().anyMatch(erp -> sameSaleKey(erp, acq));
  }

  private boolean sameSaleKey(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (erp == null || acq == null) return false;
    boolean sameNsu = erp.getNsu() != null && Objects.equals(erp.getNsu(), acq.getNsu());
    boolean sameAuth = erp.getAuthorization() != null && !erp.getAuthorization().isBlank()
      && acq.getAuthorization() != null && erp.getAuthorization().equalsIgnoreCase(acq.getAuthorization());
    boolean sameTid = erp.getTid() != null && !erp.getTid().isBlank()
      && acq.getTid() != null && erp.getTid().equalsIgnoreCase(acq.getTid());
    return (sameNsu && sameAuth) || (sameNsu && sameTid) || (sameAuth && sameTid) || (sameNsu && erp.getGrossValue() != null && acq.getGrossValue() != null && erp.getGrossValue().compareTo(acq.getGrossValue()) == 0);
  }

  private String messageForErpVsAcquirer(ErpVsAcquirerAnalysisModel item) {
    return switch (item.status()) {
      case "MISSING_IN_ACQUIRER" -> "Venda ERP sem venda correspondente na adquirente";
      case "VALUE_DIVERGENCE" -> "Valor ERP diferente do valor informado pela adquirente";
      case "FLAG_DIVERGENCE" -> "Bandeira ERP diferente da bandeira informada pela adquirente";
      case "MODALITY_DIVERGENCE" -> "Modalidade ERP diferente da modalidade informada pela adquirente";
      default -> "Divergência entre ERP e adquirente";
    };
  }

  private String feeMessage(FeeAnalysisResult fee) {
    if ("MISSING_CONTRACT".equals(fee.status())) return "Venda da adquirente sem contrato/taxa vigente encontrada";
    if ("RATE_DIVERGENCE".equals(fee.status())) return "Taxa aplicada pela adquirente diferente da taxa contratada";
    return "Valor de taxa cobrado diferente do valor esperado";
  }

  private String severityFor(String type) {
    if (type == null) return "LOW";
    if (type.contains("CHARGEBACK") || type.contains("MISSING_IN_ACQUIRER") || type.contains("BANK_RELEASE_NOT_RECONCILED")) return "CRITICAL";
    if (type.contains("MISSING_CONTRACT") || type.contains("PENDING_CONTRACT") || type.contains("VALUE_DIVERGENCE") || type.contains("BANK_SETTLEMENT")) return "HIGH";
    if (type.contains("DEBIT") || type.contains("FEE") || type.contains("RATE_DIVERGENCE")) return "MEDIUM";
    return "LOW";
  }

  private String actionFor(String type) {
    if (type == null) return "Revisar ocorrência";
    if (type.contains("MISSING_IN_ACQUIRER")) return "Verificar arquivo EEVC/arquivo da adquirente, NSU, autorização e data da venda";
    if (type.contains("MISSING_IN_ERP")) return "Verificar importação ERP, filtros de data e dados comerciais da venda";
    if (type.contains("MISSING_CONTRACT") || type.contains("PENDING_CONTRACT")) return "Cadastrar ou corrigir contrato/taxa vigente para empresa, PV, adquirente, bandeira, modalidade e parcelas";
    if (type.contains("BANK")) return "Reexecutar conciliação bancária ou revisar domicílio bancário, data e valor";
    if (type.contains("CHARGEBACK")) return "Acompanhar prazo de defesa/representação e vincular venda original";
    if (type.contains("DEBIT")) return "Verificar compensação/liquidação do débito e vínculo com ajuste ou venda original";
    if (type.contains("FEE") || type.contains("RATE")) return "Comparar taxa contratada, taxa aplicada e regra de captura/e-commerce";
    return "Revisar dados de origem e vínculos de conciliação";
  }

  private LocalDate localDate(OffsetDateTime date) {
    return date != null ? date.toLocalDate() : null;
  }

  private List<BankSettlementAnalysisModel> buildBankSettlementItems() {
    List<BankSettlementAnalysisModel> items = new ArrayList<>();

    creditOrderRepository.findAll().stream()
      .map(this::toCreditOrderBankSettlementModel)
      .forEach(items::add);

    installmentAcqRepository.findAll().stream()
      .filter(installment -> installment.getCreditOrder() == null)
      .map(this::toInstallmentBankSettlementModel)
      .forEach(items::add);

    releasesBankRepository.findAll().stream()
      .filter(release -> !isReconciled(release))
      .map(this::toUnmatchedBankReleaseSettlementModel)
      .forEach(items::add);

    return items;
  }

  private BankSettlementAnalysisModel toCreditOrderBankSettlementModel(CreditOrderEntity entity) {
    ReleasesBankEntity release = entity.getReleaseBank();
    BigDecimal expected = nz(entity.getReleaseValue());
    BigDecimal settled = release != null ? nz(release.getReleaseValue()) : null;
    BigDecimal difference = settled != null ? expected.subtract(settled) : expected;
    String status = bankSettlementStatus(expected, settled, entity.getReleaseDate(), release != null ? release.getReleaseDate() : null);

    return new BankSettlementAnalysisModel(
      entity.getId(), "CREDIT_ORDER", entity.getReleaseDate(), release != null ? release.getReleaseDate() : null,
      companyName(entity.getCompany()), null, acquirerName(entity.getAcquirer()), bankName(entity.getBankingDomicile()),
      flagName(entity.getFlag()), modalityName(entity.getTransactionType()), entity.getCreditOrderNumber(),
      release != null ? firstNonBlank(release.getDocumentComplementNumber(), release.getComplementRelease(), code(release.getSequentialNumber())) : null,
      entity.getReleaseValue(), settled, difference, daysBetween(entity.getReleaseDate(), release != null ? release.getReleaseDate() : null),
      status, bankSettlementDetail(status)
    );
  }

  private BankSettlementAnalysisModel toInstallmentBankSettlementModel(InstallmentAcqEntity entity) {
    ReleasesBankEntity release = entity.getReleaseBank();
    TransactionAcqEntity transaction = entity.getTransaction();
    BigDecimal expected = netInstallmentValue(entity);
    BigDecimal settled = release != null ? expected : null;
    BigDecimal difference = settled != null ? expected.subtract(settled) : expected;
    LocalDate settlementDate = firstNonNull(entity.getPaymentDate(), release != null ? release.getReleaseDate() : null);
    String status = bankSettlementStatus(expected, settled, entity.getExpectedPaymentDate(), settlementDate);

    return new BankSettlementAnalysisModel(
      entity.getId(), "INSTALLMENT", entity.getExpectedPaymentDate(), settlementDate,
      transaction != null ? companyName(transaction.getCompany()) : null, transaction != null ? establishmentName(transaction.getEstablishment()) : null,
      transaction != null ? acquirerName(transaction.getAcquirer()) : null, release != null ? bankName(release) : null,
      transaction != null ? flagName(transaction.getFlag()) : null, transaction != null ? modalityName(transaction.getModality()) : null,
      null, release != null ? firstNonBlank(release.getDocumentComplementNumber(), release.getComplementRelease(), code(release.getSequentialNumber())) : null,
      expected, settled, difference, daysBetween(entity.getExpectedPaymentDate(), settlementDate),
      status, bankSettlementDetail(status)
    );
  }

  private BankSettlementAnalysisModel toUnmatchedBankReleaseSettlementModel(ReleasesBankEntity entity) {
    return new BankSettlementAnalysisModel(
      entity.getId(), "BANK_RELEASE", null, entity.getReleaseDate(), companyName(entity.getCompany()), establishmentName(entity.getEstablishment()),
      acquirerName(entity.getAcquirer()), bankName(entity), flagName(entity.getFlag()), modalityName(entity.getModalityPaymentBank()),
      null, firstNonBlank(entity.getDocumentComplementNumber(), entity.getComplementRelease(), code(entity.getSequentialNumber())),
      null, entity.getReleaseValue(), entity.getReleaseValue(), null, "BANK_RELEASE_NOT_RECONCILED",
      "Lançamento bancário sem vínculo com ordem de crédito ou parcela"
    );
  }

  private String bankSettlementStatus(BigDecimal expected, BigDecimal settled, LocalDate expectedDate, LocalDate settlementDate) {
    if (settled == null) return "PENDING";

    BigDecimal difference = abs(nz(expected).subtract(nz(settled)));
    boolean valueOk = difference.compareTo(VALUE_TOLERANCE) <= 0;
    boolean dateOk = expectedDate == null || settlementDate == null || !settlementDate.isBefore(expectedDate);

    if (valueOk && dateOk) return "LIQUIDATED";
    if (!dateOk) return "DATE_DIVERGENCE";
    if (settled.compareTo(ZERO) > 0 && settled.compareTo(nz(expected)) < 0) return "PARTIALLY_LIQUIDATED";
    return "VALUE_DIVERGENCE";
  }

  private String bankSettlementDetail(String status) {
    if ("PENDING".equals(status)) return "Liquidação bancária ainda não vinculada";
    if ("DATE_DIVERGENCE".equals(status)) return "Data de liquidação anterior à data prevista";
    if ("PARTIALLY_LIQUIDATED".equals(status)) return "Valor liquidado parcialmente";
    if ("VALUE_DIVERGENCE".equals(status)) return "Diferença de valor acima da tolerância";
    if ("LIQUIDATED".equals(status)) return "Liquidação conciliada";
    return null;
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

  private ErpAcquirerApplyResult applyAcquirerBusinessContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
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
      StatusTransactionEnum.AUTOMATICALLY_RECONCILED,
      StatusTransactionReasonEnum.SCHEDULED
    );

    applyAcquirerReconciliationStatus(
      acq,
      StatusTransactionEnum.AUTOMATICALLY_RECONCILED,
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

  private boolean applyErpReconciliationStatus(
    TransactionErpEntity erp, StatusTransactionEnum status, StatusTransactionReasonEnum reason) {
    if (erp == null || status == null || isFinalErpStatusTransaction(erp)) {
      return false;
    }

    StatusTransactionReasonEnum normalizedReason = normalizeReasonForStatus(status, reason);

    boolean changed = false;
    changed |= setIfDifferent(erp::getStatusTransaction, erp::setStatusTransaction, status.getCode());
    changed |= setIfDifferent(erp::getStatusTransactionReason, erp::setStatusTransactionReason, reasonCode(normalizedReason));
    return changed;
  }

  private boolean applyAcquirerReconciliationStatus(
    TransactionAcqEntity acq, StatusTransactionEnum status, StatusTransactionReasonEnum reason) {
    if (acq == null || status == null || isFinalAcquirerStatusTransaction(acq)) {
      return false;
    }

    StatusTransactionReasonEnum normalizedReason = normalizeReasonForStatus(status, reason);

    boolean changed = false;
    changed |= setIfDifferent(acq::getStatusTransaction, acq::setStatusTransaction, status.getCode());
    changed |= setIfDifferent(acq::getStatusTransactionReason, acq::setStatusTransactionReason, reasonCode(normalizedReason));
    return changed;
  }

  private int applyAcquirerReconciliationStatusToCandidates(
    List<TransactionAcqEntity> candidates, StatusTransactionEnum status, StatusTransactionReasonEnum reason,
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
    StatusTransactionEnum status, StatusTransactionReasonEnum reason) {
    if (status == StatusTransactionEnum.AUTOMATICALLY_RECONCILED
      || status == StatusTransactionEnum.MANUALLY_RECONCILED) {
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

  private boolean isFinalStatusTransaction(Integer status) {
    return Objects.equals(status, StatusTransactionEnum.CANCELED.getCode())
      || Objects.equals(status, StatusTransactionEnum.DELETED.getCode());
  }

  private int classifyAcquirerSalesMissingInErp(boolean reconcileAlreadyReconciled, List<Integer> pendingStatuses) {
    int totalUpdated = 0;
    int batchNumber = 0;

    while (true) {
      List<UUID> acquirerIds = transactionAcqRepository.findIdsForMissingInErpStatusClassification(
        PageRequest.of(0, ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE),
        reconcileAlreadyReconciled,
        pendingStatuses,
        StatusTransactionEnum.NOT_RECONCILED.getCode(),
        StatusTransactionReasonEnum.NULL.getCode(),
        EXCLUDED_CARD_RECONCILIATION_MODALITY
      );

      if (acquirerIds.isEmpty()) {
        break;
      }

      batchNumber++;

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
          StatusTransactionEnum.NOT_RECONCILED,
          StatusTransactionReasonEnum.CV_NOT_FOUND_ERP
        )) {
          changedAcquirerSales.add(acq);
        }
      }

      if (changedAcquirerSales.isEmpty()) {
        break;
      }

      transactionAcqRepository.saveAll(changedAcquirerSales);
      totalUpdated += changedAcquirerSales.size();

      entityManager.flush();
      entityManager.clear();

      log.info(
        "🔎 Classificação de vendas da adquirente sem ERP: batch={}, analisadas={}, atualizadas={}",
        batchNumber,
        acquirerBatch.size(),
        changedAcquirerSales.size()
      );
    }

    return totalUpdated;
  }

  private boolean isExcludedFromCardReconciliation(TransactionErpEntity erp) {
    return erp == null
      || erp.getModality() == null
      || Objects.equals(erp.getModality(), EXCLUDED_CARD_RECONCILIATION_MODALITY);
  }

  private boolean isExcludedFromCardReconciliation(TransactionAcqEntity acq) {
    return acq == null
      || acq.getModality() == null
      || Objects.equals(acq.getModality(), EXCLUDED_CARD_RECONCILIATION_MODALITY);
  }

  private boolean shouldReconcileAlreadyReconciledErpAcquirerSales() {
    return fileProcessingProperties != null
      && fileProcessingProperties.getReconciliation() != null
      && fileProcessingProperties.getReconciliation().isReconcileAlreadyReconciledErpAcquirerSales();
  }

  private boolean isPendingForErpAcquirerReconciliation(TransactionErpEntity erp) {
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

  private boolean isPendingStatusTransaction(Integer status) {
    if (status == null) {
      return true;
    }

    return Objects.equals(status, StatusTransactionEnum.NULL.getCode())
      || Objects.equals(status, StatusTransactionEnum.PENDING.getCode())
      || Objects.equals(status, StatusTransactionEnum.NOT_RECONCILED.getCode());
  }

  private Integer reasonCode(StatusTransactionReasonEnum reason) {
    return reason != null ? reason.getCode() : StatusTransactionReasonEnum.NULL.getCode();
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

    CompanyEntity company = firstNonNull(acq.getCompany(), acq.getEstablishment() != null ? acq.getEstablishment().getCompany() : null);
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

  private List<Integer> erpAcquirerPendingStatusCodes() {
    return List.of(
      StatusTransactionEnum.NULL.getCode(),
      StatusTransactionEnum.PENDING.getCode(),
      StatusTransactionEnum.NOT_RECONCILED.getCode()
    );
  }

  private List<TransactionAcqEntity> findAcquirerCandidatesForBatch(
    List<TransactionErpEntity> erpBatch, boolean reconcileAlreadyReconciled, List<Integer> pendingStatuses) {
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

    if (!nsus.isEmpty()) {
      transactionAcqRepository.findCandidatesForErpAcquirerReconciliationByNsus(
        nsus,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    if (!authorizations.isEmpty()) {
      transactionAcqRepository.findCandidatesForErpAcquirerReconciliationByAuthorizations(
        authorizations,
        reconcileAlreadyReconciled,
        pendingStatuses,
        EXCLUDED_CARD_RECONCILIATION_MODALITY
      ).forEach(acq -> candidates.put(acq.getId(), acq));
    }

    return candidates.values().stream()
      .filter(acq -> !isExcludedFromCardReconciliation(acq))
      .toList();
  }

  private Map<ErpAcquirerIdentityKey, List<TransactionAcqEntity>> indexAcquirerCandidates(List<TransactionAcqEntity> acquirerCandidates) {
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
    if (erp == null || acquirerSales == null || acquirerSales.isEmpty()) {
      return ErpAcquirerMatchResult.notMatched();
    }

    List<TransactionAcqEntity> sameIdentity = acquirerSales.stream()
      .filter(acq -> sameText(erp.getAuthorization(), acq.getAuthorization()))
      .filter(acq -> erp.getNsu() != null && Objects.equals(erp.getNsu(), acq.getNsu()))
      .toList();

    if (sameIdentity.isEmpty()) {
      return ErpAcquirerMatchResult.notMatched();
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

  private boolean sameAcquirerForReconciliation(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq.getAcquirer() == null) return false;
    if (erp.getAcquirer() == null) return true;
    return sameId(erp.getAcquirer(), acq.getAcquirer());
  }

  private BigDecimal reconciliationValueTolerance() {
    FileProcessingProperties.Reconciliation reconciliation = fileProcessingProperties.getReconciliation();
    return reconciliation != null ? reconciliation.valueToleranceAsBigDecimal() : VALUE_TOLERANCE;
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

  private boolean sameId(AuditableEntityBase left, AuditableEntityBase right) {
    if (left == null || right == null) return false;
    return Objects.equals(left.getId(), right.getId());
  }

  private record ErpAcquirerIdentityKey(Long nsu, String authorization) {
    static ErpAcquirerIdentityKey fromErp(TransactionErpEntity erp) {
      if (erp == null) {
        return new ErpAcquirerIdentityKey(null, null);
      }
      return new ErpAcquirerIdentityKey(erp.getNsu(), normalizeKeyText(erp.getAuthorization()));
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
      return value.trim().toLowerCase(Locale.ROOT);
    }
  }

  private enum ErpAcquirerMatchStatus {
    MATCHED,
    NOT_MATCHED,
    VALUE_DIVERGENCE,
    ACQUIRER_DIVERGENCE,
    AMBIGUOUS
  }

  private record ErpAcquirerMatchResult(
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

  private record ErpAcquirerApplyResult(boolean changed, boolean flagUpdated, boolean businessContextUpdated) {}

  private Optional<TransactionAcqEntity> findAcquirerMatch(TransactionErpEntity erp) {
    if (erp == null) {
      return Optional.empty();
    }

    /*
     * Caminho rápido: após a conciliação, a própria venda ERP guarda o vínculo
     * exato com a venda da adquirente. Isso evita novas buscas por NSU/autorização
     * nas listagens e análises.
     */
    if (erp.getTransactionAcq() != null) {
      return Optional.of(erp.getTransactionAcq());
    }

    if (erp.getNsu() == null && (erp.getAuthorization() == null || erp.getAuthorization().isBlank())) {
      return Optional.empty();
    }

    return transactionAcqRepository.findFirstByNsuAndAuthorization(erp.getNsu(), erp.getAuthorization())
      .or(() -> transactionAcqRepository.findFirstByNsu(erp.getNsu()))
      .or(() -> transactionAcqRepository.findFirstByAuthorization(erp.getAuthorization()));
  }

  private String comparisonStatus(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq == null) return "MISSING_IN_ACQUIRER";
    if (erp.getGrossValue() != null && acq.getGrossValue() != null && erp.getGrossValue().compareTo(acq.getGrossValue()) != 0) return "VALUE_DIVERGENCE";

    UUID erpFlagId = erp.getFlag() != null ? erp.getFlag().getId() : null;
    UUID acqFlagId = acq.getFlag() != null ? acq.getFlag().getId() : null;
    if (erpFlagId != null && acqFlagId != null && !Objects.equals(erpFlagId, acqFlagId)) return "FLAG_DIVERGENCE";

    if (erp.getModality() != null && acq.getModality() != null && !Objects.equals(erp.getModality(), acq.getModality())) return "MODALITY_DIVERGENCE";
    return "MATCHED";
  }

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

  private String modalityName(Integer modality) {
    try {
      ModalityEnum value = ModalityEnum.fromCode(modality);
      return value != null ? value.name() : null;
    } catch (RuntimeException ex) {
      return code(modality);
    }
  }

  private BigDecimal netInstallmentValue(InstallmentAcqEntity installment) {
    if (installment == null) return ZERO;
    if (installment.getLiquidValue() != null) return installment.getLiquidValue();
    BigDecimal gross = nz(installment.getGrossValue());
    BigDecimal discount = nz(installment.getDiscountValue());
    BigDecimal adjustment = nz(installment.getAdjustmentValue());
    return gross.subtract(discount).add(adjustment);
  }

  private Long daysBetween(LocalDate expectedDate, LocalDate settlementDate) {
    if (expectedDate == null || settlementDate == null) return null;
    return ChronoUnit.DAYS.between(expectedDate, settlementDate);
  }

  private String bankName(BankingDomicileEntity bankingDomicile) {
    return bankingDomicile != null && bankingDomicile.getBank() != null ? bankingDomicile.getBank().getName() : null;
  }

  private String bankName(ReleasesBankEntity release) {
    if (release == null) return null;
    if (release.getBank() != null) return release.getBank().getName();
    return bankName(release.getBankingDomicile());
  }

  private String code(Integer value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String code(Long value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String flagName(FlagEntity flag) {
    return flag != null ? flag.getName() : null;
  }

  private String acquirerName(AcquirerEntity acquirer) {
    return acquirer != null ? acquirer.getFantasyName() : null;
  }

  private String companyName(CompanyEntity company) {
    return company != null ? firstNonBlank(company.getFantasyName(), company.getSocialReason(), company.getCnpj()) : null;
  }

  private String establishmentName(EstablishmentEntity establishment) {
    return establishment != null ? String.valueOf(establishment.getPvNumber()) : null;
  }

  private String fileName(ProcessedFileEntity processedFile) {
    return processedFile != null ? processedFile.getFile() : null;
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

  private <T> Page<T> page(List<T> items, Pageable pageable) {
    int start = Math.toIntExact(Math.min(pageable.getOffset(), items.size()));
    int end = Math.min(start + pageable.getPageSize(), items.size());
    return new PageImpl<>(items.subList(start, end), pageable, items.size());
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
