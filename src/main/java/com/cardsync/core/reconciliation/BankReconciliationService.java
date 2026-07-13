package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.repository.TransactionErpRepository;

import java.util.Map;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

  private static final int PAYMENT_PENDING = StatusPaymentBankEnum.PENDING.getCode();
  private static final int STATUS_PENDING = BankReconciliationStatus.PENDING.getCode();
  private static final int PAYMENT_PARTIAL = StatusPaymentBankEnum.PARTIALLY_PAID.getCode();
  private static final int STATUS_LIQUIDATED = BankReconciliationStatus.RECONCILED.getCode();
  private static final int STATUS_INSTALLMENT_RECONCILED = BankReconciliationStatus.INSTALLMENT_RECONCILED.getCode();

  /**
   * Gate da etapa 6: só ordens cujo resumo já foi conciliado com a ordem (etapa 5)
   * participam da conciliação com o extrato bancário.
   */
  private static final int SUMMARY_RECONCILED_STATUS = StatusReconciliationEnum.RECONCILED.getCode();

  private final EntityManager entityManager;
  private final BankReconciliationMatcher matcher;
  private final FileProcessingProperties properties;
  private final CreditOrderRepository creditOrderRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final TransactionErpRepository transactionErpRepository;
  private final ReconciliationSettingsService reconciliationSettingsService;

  @Transactional
  public BankReconciliationResult reconcilePending() {
    return reconcilePending(BankReconciliationTriggerType.MANUAL);
  }

  @Transactional
  public BankReconciliationResult reconcilePending(BankReconciliationTriggerType trigger) {
    FileProcessingProperties.Reconciliation config = properties.getReconciliation();
    BankReconciliationMode mode = BankReconciliationMode.CREDIT_ORDER_ONLY;
    BankReconciliationResult.Counter result = BankReconciliationResult.counter(trigger, mode);

    List<UUID> eligibleOrderIds = creditOrderRepository.findEligibleIdsForBankReconciliation(
      SUMMARY_RECONCILED_STATUS,
      PAYMENT_PENDING,
      PAYMENT_PARTIAL,
      reconciliationSettingsService.isReprocessBankAcquirer()
    );

    int batchSize = Math.max(config.getBankBatchSize(), 1);
    int totalBatches = (eligibleOrderIds.size() + batchSize - 1) / batchSize;

    log.info(
      "📌 Iniciando conciliação Banco x Adquirente dirigida por ordens: trigger={}, ordensElegiveis={}, tamanhoLote={}, " +
        "totalLotes={}, toleranciaAntes={}, toleranciaDepois={}, toleranciaValor={}",
      trigger.getCode(),
      eligibleOrderIds.size(),
      batchSize,
      totalBatches,
      reconciliationSettingsService.getDateToleranceDaysBefore(),
      reconciliationSettingsService.getDateToleranceDaysAfter(),
      reconciliationSettingsService.getValueTolerance()
    );

    int zeroValueCount = reconcileZeroValueOrders();
    if (zeroValueCount > 0) {
      log.info("✅ Conciliação automática: {} ordem(ns) com releaseValue zero conciliada(s).", zeroValueCount);
    }

    Set<UUID> reconciledOrderIds = new HashSet<>();
    Set<UUID> analyzedReleaseIds = new HashSet<>();

    for (int offset = 0, batchNumber = 1; offset < eligibleOrderIds.size(); offset += batchSize, batchNumber++) {
      int endIndex = Math.min(offset + batchSize, eligibleOrderIds.size());
      List<UUID> batchIds = eligibleOrderIds.subList(offset, endIndex);

      List<CreditOrderEntity> batchOrders = creditOrderRepository.findEligibleByIdsForBankReconciliation(
        batchIds,
        SUMMARY_RECONCILED_STATUS,
        PAYMENT_PENDING,
        PAYMENT_PARTIAL,
        reconciliationSettingsService.isReprocessBankAcquirer()
      );

      java.util.Map<UUID, List<CreditOrderEntity>> ordersByCompany = new java.util.LinkedHashMap<>();
      for (CreditOrderEntity order : batchOrders) {
        UUID companyId = idOrNull(order.getCompany());
        if (companyId == null) {
          log.warn(
            "⚠ Ordem ignorada por falta de empresa. creditOrder={}, releaseDate={}, releaseValue={}",
            order.getId(), order.getReleaseDate(), order.getReleaseValue()
          );
          continue;
        }
        ordersByCompany.computeIfAbsent(companyId, ignored -> new java.util.ArrayList<>()).add(order);
      }

      int reconciledBeforeBatch = result.toResult().getCreditOrdersReconciled();
      int releasesBeforeBatch = result.toResult().getReleasesReconciled();

      for (var entry : ordersByCompany.entrySet()) {
        UUID companyId = entry.getKey();
        List<CreditOrderEntity> companyOrders = entry.getValue();

        LocalDate minOrderDate = companyOrders.stream()
          .map(CreditOrderEntity::getReleaseDate)
          .filter(Objects::nonNull)
          .min(LocalDate::compareTo)
          .orElse(null);
        LocalDate maxOrderDate = companyOrders.stream()
          .map(CreditOrderEntity::getReleaseDate)
          .filter(Objects::nonNull)
          .max(LocalDate::compareTo)
          .orElse(null);

        if (minOrderDate == null || maxOrderDate == null) continue;

        int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
        int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
        List<ReleasesBankEntity> companyReleases = releasesBankRepository.findAvailableForCreditOrderBatch(
          STATUS_PENDING,
          reconciliationSettingsService.isReprocessBankAcquirer(),
          companyId,
          minOrderDate.minusDays(toleranceDaysBefore),
          maxOrderDate.plusDays(toleranceDaysAfter)
        );

        reconcileEligibleCreditOrders(
          companyOrders,
          companyReleases,
          reconciledOrderIds,
          analyzedReleaseIds,
          config,
          result
        );
      }

      entityManager.flush();
      entityManager.clear();

      BankReconciliationResult partial = result.toResult();
      log.info(
        "📦 Lote {}/{} concluído: ordensCarregadas={}, ordensConciliadasNoLote={}, releasesConciliadosNoLote={}, totalOrdensConciliadas={}",
        batchNumber,
        totalBatches,
        batchOrders.size(),
        partial.getCreditOrdersReconciled() - reconciledBeforeBatch,
        partial.getReleasesReconciled() - releasesBeforeBatch,
        partial.getCreditOrdersReconciled()
      );
    }

    BankReconciliationResult partialResult = result.toResult();
    result.setReleasesWithoutMatch(Math.max(0, eligibleOrderIds.size() - partialResult.getCreditOrdersReconciled()));

    BankReconciliationResult built = result.toResult();
    BigDecimal reconciledDifference = built.getTotalReleaseValueReconciled()
      .subtract(built.getTotalCreditOrderValueReconciled())
      .abs();

    log.info(
      "📘 RESUMO FINAL CONCILIAÇÃO BANCO x ADQUIRENTE: trigger={}, modo={}, ordensElegiveis={}, ordensConciliadas={}, " +
        "ordensSemMatch={}, releasesAnalisados={}, releasesConciliados={}, gruposIgnoradosLimite={}, " +
        "valorBancoConciliado={}, valorOrdensConciliado={}, diferença={}",
      built.getTrigger().getCode(),
      built.getMode(),
      eligibleOrderIds.size(),
      built.getCreditOrdersReconciled(),
      built.getReleasesWithoutMatch(),
      built.getReleasesAnalyzed(),
      built.getReleasesReconciled(),
      built.getCandidateGroupsSkippedBySafetyCap(),
      built.getTotalReleaseValueReconciled(),
      built.getTotalCreditOrderValueReconciled(),
      reconciledDifference
    );
    return built;
  }

  private void reconcileEligibleCreditOrders(
    List<CreditOrderEntity> eligibleOrders,
    List<ReleasesBankEntity> candidateReleases,
    Set<UUID> reconciledOrderIds,
    Set<UUID> analyzedReleaseIds,
    FileProcessingProperties.Reconciliation config,
    BankReconciliationResult.Counter result
  ) {
    boolean reprocess = reconciliationSettingsService.isReprocessBankAcquirer();
    BigDecimal tolerance = reconciliationSettingsService.getValueTolerance();
    int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
    int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
    Set<UUID> reconciledReleaseIds = new HashSet<>();

    List<CreditOrderEntity> validOrders = eligibleOrders.stream()
      .filter(order -> {
        if (hasRequiredContext(order)) return true;
        log.warn(
          "⚠ Ordem ignorada por falta de contexto. creditOrder={}, company={}, bankingDomicile={}",
          order.getId(), idOrNull(order.getCompany()), idOrNull(order.getBankingDomicile())
        );
        return false;
      })
      .toList();

    List<ReleasesBankEntity> validReleases = candidateReleases.stream()
      .filter(this::hasRequiredContext)
      .toList();

    for (ReleasesBankEntity release : validReleases) {
      if (release.getId() != null && reconciledReleaseIds.contains(release.getId())) continue;

      if (release.getId() != null && analyzedReleaseIds.add(release.getId())) {
        result.releaseAnalyzed();
      }

      List<CreditOrderEntity> compatible = validOrders.stream()
        .filter(order -> isOrderStillEligible(order, reconciledOrderIds, reprocess))
        .filter(order -> isCreditOrderCandidateCompatible(release, order, toleranceDaysBefore, toleranceDaysAfter))
        .toList();

      if (compatible.isEmpty()) continue;

      BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
        compatible,
        CreditOrderEntity::getReleaseValue,
        release.getReleaseValue(),
        tolerance,
        config.getSafeCapCents(),
        config.getSubsetDpMaxCents()
      );

      if (selected.skippedBySafetyCap()) {
        result.candidateGroupSkippedBySafetyCap();
      }
      if (!selected.matched()) continue;

      List<CreditOrderEntity> orders = selected.typedItems();
      applyCreditOrderMatch(release, orders, selected, result, reprocess);
      orders.stream().map(CreditOrderEntity::getId).filter(Objects::nonNull).forEach(reconciledOrderIds::add);
      if (release.getId() != null) reconciledReleaseIds.add(release.getId());
    }
  }

  private void applyCreditOrderMatch(
    ReleasesBankEntity release,
    List<CreditOrderEntity> orders,
    BankReconciliationMatcher.MatchResult selected,
    BankReconciliationResult.Counter result,
    boolean reprocess
  ) {
    BankReconciliationMatchType matchType = BankReconciliationMatchType.creditOrderByCount(orders.size());

    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(release);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      updateSalesSummaryFromCreditOrder(order);
    }
    propagateCreditOrdersToInstallments(orders, release, reprocess);

    release.setNumberCreditOrders(orders.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + orders.size());
    release.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    creditOrderRepository.saveAll(orders);
    releasesBankRepository.save(release);

    result.matchedByCreditOrders(orders.size(), selected.matchedValue());
    result.releaseReconciled(release.getReleaseValue());
    result.transactionsUpdated(propagateReleaseStatusTransactions(release));

    log.debug(
      "✅ Ordem(ns) de pagamento conciliada(s) com lançamento bancário. ordemInicial={}, releaseBank={}, tipoMatch={}, ordens={}, valorRelease={}, valorOrdens={}",
      orders.getFirst().getId(), release.getId(), matchType, orders.size(), release.getReleaseValue(), selected.matchedValue()
    );
  }

  private void reconcilePendingReleasesByInstallments(
    FileProcessingProperties.Reconciliation config,
    Set<UUID> analyzedReleaseIds,
    BankReconciliationResult.Counter result
  ) {
    List<ReleasesBankEntity> releases = releasesBankRepository.findForBankReconciliation(
      STATUS_PENDING,
      reconciliationSettingsService.isReprocessBankAcquirer()
    );

    for (ReleasesBankEntity release : releases) {
      if (release.getId() != null && analyzedReleaseIds.add(release.getId())) {
        result.releaseAnalyzed();
      }
      if (!hasRequiredContext(release)) {
        markReleaseNotReconciledWhenExpired(release, config, "contexto bancário obrigatório ausente", result);
        result.releaseSkippedMissingContext();
        continue;
      }

      BankReconciliationMatcher.MatchResult installmentResult = reconcileByInstallmentsWithStats(release, config, result);
      if (installmentResult.matched()) {
        result.releaseReconciled(release.getReleaseValue());
        result.transactionsUpdated(propagateReleaseStatusTransactions(release));
      } else {
        markReleaseNotReconciledWhenExpired(release, config, "nenhuma parcela compatível encontrada", result);
      }
    }
  }

  private int reconcileZeroValueOrders() {
    List<CreditOrderEntity> orders = creditOrderRepository
      .findPendingZeroValueOrders(PAYMENT_PENDING, BigDecimal.ZERO);
    if (orders.isEmpty()) return 0;
    for (CreditOrderEntity order : orders) {
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      updateSalesSummaryFromCreditOrder(order);
    }
    creditOrderRepository.saveAll(orders);
    return orders.size();
  }

  /**
   * Desfaz a conciliação de um lançamento bancário: as ordens de crédito e
   * parcelas vinculadas voltam ao estado anterior (pendentes), o próprio
   * lançamento volta a PENDING, e as transações/resumos de venda afetados têm
   * seu status recalculado a partir do que sobrar reconciliado (caso o resumo
   * ou a transação tenham outras ordens/parcelas ligadas a lançamentos
   * diferentes). Não altera o vínculo resumo↔ordem (salesSummaryStatus da
   * ordem, etapa 6) nem nenhum dado importado do arquivo — só o que a
   * conciliação bancária (etapa 7) escreveu.
   */
  @Transactional
  public UndoBankReconciliationResult undoReconciliation(UUID releaseBankId) {
    ReleasesBankEntity release = releasesBankRepository.findById(releaseBankId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "bank.release.not.found"));

    List<CreditOrderEntity> orders = creditOrderRepository.findByReleaseBank_Id(releaseBankId);
    List<InstallmentAcqEntity> installments = installmentAcqRepository.findByReleaseBank_Id(releaseBankId);

    if (orders.isEmpty() && installments.isEmpty()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "bank.release.not.reconciled");
    }

    Set<UUID> affectedSummaryIds = new HashSet<>();
    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(null);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      order.setReconciliationStatus(BankReconciliationStatus.PENDING.getCode());
      order.setCreditStatus(BankReconciliationStatus.PENDING.getCode());
      if (order.getSalesSummary() != null && order.getSalesSummary().getId() != null) {
        affectedSummaryIds.add(order.getSalesSummary().getId());
      }
    }
    creditOrderRepository.saveAll(orders);

    for (InstallmentAcqEntity installment : installments) {
      installment.setReleaseBank(null);
      installment.setPaymentDate(null);
      installment.setStatusPaymentBank(BankReconciliationStatus.PENDING.getCode());
      installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
      installment.setReconciliationBankLine(null);
      installment.setReconciliationBankFile(null);
      installment.setReconciliationBankProcessedAt(null);
    }
    installmentAcqRepository.saveAll(installments);

    recomputeTransactionsAfterUndo(installments);
    recomputeSalesSummariesFromCreditOrders(orders, affectedSummaryIds);

    release.setReconciliationStatus(StatusPaymentBankEnum.PENDING);
    release.setNumberCreditOrders(0);
    release.setNumberReconciliations(Math.max(0, safeInt(release.getNumberReconciliations()) - orders.size()));
    releasesBankRepository.save(release);

    log.info(
      "↩ Conciliação desfeita. releaseBank={}, ordensDesvinculadas={}, parcelasDesvinculadas={}",
      releaseBankId, orders.size(), installments.size()
    );

    return new UndoBankReconciliationResult(orders.size(), installments.size());
  }

  /** Recalcula o status das transações ADQ (e do ERP correspondente) afetadas pelas parcelas revertidas. */
  private void recomputeTransactionsAfterUndo(List<InstallmentAcqEntity> resetInstallments) {
    Set<UUID> transactionIds = resetInstallments.stream()
      .map(InstallmentAcqEntity::getTransaction)
      .filter(Objects::nonNull)
      .map(TransactionAcqEntity::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    if (transactionIds.isEmpty()) return;

    Map<UUID, List<InstallmentAcqEntity>> installmentsByTx = installmentAcqRepository
      .findByTransactionIdIn(transactionIds).stream()
      .filter(i -> i.getTransaction() != null && i.getTransaction().getId() != null)
      .collect(Collectors.groupingBy(i -> i.getTransaction().getId()));

    Map<UUID, TransactionErpEntity> erpByTxId = transactionErpRepository
      .findByTransactionAcqIdIn(transactionIds).stream()
      .filter(e -> e.getTransactionAcq() != null && e.getTransactionAcq().getId() != null)
      .collect(Collectors.toMap(e -> e.getTransactionAcq().getId(), e -> e, (a, b) -> a));

    Set<UUID> updatedTransactions = new HashSet<>();
    for (InstallmentAcqEntity installment : resetInstallments) {
      TransactionAcqEntity transaction = installment.getTransaction();
      if (transaction == null || transaction.getId() == null || updatedTransactions.contains(transaction.getId())) continue;
      List<InstallmentAcqEntity> txInstallments = installmentsByTx.getOrDefault(transaction.getId(), List.of());
      updateStatusTransactionBatched(transaction, txInstallments, erpByTxId.get(transaction.getId()));
      updatedTransactions.add(transaction.getId());
    }
  }

  /**
   * Recalcula creditOrderStatus/statusPaymentBank dos resumos de venda afetados,
   * a partir de TODAS as ordens de crédito ligadas a cada resumo (não só as que
   * foram revertidas) — cobre o caso de um resumo com ordens conciliadas via
   * outro lançamento bancário, que devem permanecer intactas.
   */
  private void recomputeSalesSummariesFromCreditOrders(List<CreditOrderEntity> resetOrders, Set<UUID> affectedSummaryIds) {
    if (affectedSummaryIds.isEmpty()) return;

    Map<UUID, SalesSummaryEntity> summaries = new HashMap<>();
    for (CreditOrderEntity order : resetOrders) {
      SalesSummaryEntity summary = order.getSalesSummary();
      if (summary != null && summary.getId() != null && affectedSummaryIds.contains(summary.getId())) {
        summaries.putIfAbsent(summary.getId(), summary);
      }
    }

    for (SalesSummaryEntity summary : summaries.values()) {
      List<CreditOrderEntity> siblings = List.copyOf(summary.getCreditOrders());
      boolean anyPaid = siblings.stream()
        .anyMatch(co -> StatusPaymentBankEnum.PAID.equals(co.getStatusPaymentBank()));
      boolean allPaid = !siblings.isEmpty() && siblings.stream()
        .allMatch(co -> StatusPaymentBankEnum.PAID.equals(co.getStatusPaymentBank()));

      if (allPaid) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      } else if (anyPaid) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.PARTIALLY_RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PARTIALLY_PAID);
      } else {
        summary.setCreditOrderStatus(StatusReconciliationEnum.PENDING);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      }
    }
  }

  private boolean isOrderStillEligible(CreditOrderEntity order, Set<UUID> reconciledOrderIds, boolean reprocess) {
    if (order == null) return false;
    if (!reprocess && order.getReleaseBank() != null) return false;
    if (order.getId() != null && reconciledOrderIds.contains(order.getId())) return false;
    if (order.getReleaseDate() == null || order.getReleaseValue() == null) return false;
    return order.getReleaseValue().compareTo(BigDecimal.ZERO) > 0;
  }

  private boolean hasRequiredContext(CreditOrderEntity order) {
    return order != null
      && order.getReleaseDate() != null
      && order.getReleaseValue() != null
      && order.getCompany() != null
      && order.getCompany().getId() != null
      && order.getAcquirer() != null
      && order.getAcquirer().getId() != null;
  }

  private Comparator<ReleasesBankEntity> candidateReleaseComparator(CreditOrderEntity order) {
    ReconciliationMatchContext orderContext = contextOf(order);
    return Comparator
      .comparingInt((ReleasesBankEntity release) -> orderContext.strength(contextOf(release))).reversed()
      .thenComparingLong(release -> Math.abs(ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate())))
      .thenComparing(release -> nvl(release.getReleaseValue()).subtract(nvl(order.getReleaseValue())).abs());
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallmentsWithStats(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    BankReconciliationResult.Counter result
  ) {
    BankReconciliationMatcher.MatchResult installmentResult = reconcileByInstallments(release, config);
    if (installmentResult.skippedBySafetyCap()) {
      result.candidateGroupSkippedBySafetyCap();
    }
    if (installmentResult.matched()) {
      result.matchedByInstallments(installmentResult.itemsMatched(), installmentResult.matchedValue());
    }
    return installmentResult;
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallments(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
    int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
    BigDecimal valueTolerance = reconciliationSettingsService.getValueTolerance();
    // dateFrom: installments expected up to toleranceDaysAfter before the release (release came after)
    // dateTo:   installments expected up to toleranceDaysBefore after the release (release came before)
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDaysAfter);
    LocalDate dateTo = release.getReleaseDate().plusDays(toleranceDaysBefore);

    ReconciliationMatchContext releaseContext = contextOf(release);
    List<InstallmentAcqEntity> candidates = installmentAcqRepository.findPendingForBankRelease(
        STATUS_PENDING,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        idOrNull(release.getEstablishment()),
        idOrNull(release.getBankingDomicile()),
        idOrNull(release.getFlag()),
        dateFrom,
        dateTo
      ).stream()
      .filter(installment -> isInstallmentCandidateCompatible(release, installment, toleranceDaysBefore, toleranceDaysAfter))
      .sorted(Comparator.comparingInt(
        (InstallmentAcqEntity installment) -> releaseContext.strength(contextOf(installment))).reversed())
      .toList();

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      candidates,
      this::netInstallmentValue,
      release.getReleaseValue(),
      valueTolerance,
      config.getSafeCapCents(),
      config.getSubsetDpMaxCents()
    );

    if (!selected.matched()) return selected;

    List<InstallmentAcqEntity> installments = selected.typedItems();
    BankReconciliationMatchType matchType = BankReconciliationMatchType.installmentByCount(installments.size());
    applyReleaseToInstallments(installments, release);

    release.setNumberParcels(installments.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + installments.size());
    release.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    installmentAcqRepository.saveAll(installments);
    releasesBankRepository.save(release);

    log.info(
      "✅ Release bancário conciliado por parcelas. releaseBank={}, tipoMatch={}, parcelas={}, valorRelease={}, valorParcelas={}",
      release.getId(), matchType, installments.size(), release.getReleaseValue(), selected.matchedValue()
    );
    return selected;
  }

  private void propagateCreditOrdersToInstallments(List<CreditOrderEntity> orders, ReleasesBankEntity release, boolean reprocess) {
    // Agrupa por acquirerId → { rvNumber → installmentNumber } para busca em lote
    Map<UUID, Map<Integer, Integer>> acquirerRvToInstNum = new java.util.LinkedHashMap<>();
    for (CreditOrderEntity order : orders) {
      if (order.getAcquirer() == null || order.getRvNumber() == null || order.getInstallmentNumber() == null) continue;
      acquirerRvToInstNum
        .computeIfAbsent(order.getAcquirer().getId(), k -> new java.util.LinkedHashMap<>())
        .put(order.getRvNumber(), order.getInstallmentNumber());
    }
    if (acquirerRvToInstNum.isEmpty()) return;

    List<InstallmentAcqEntity> allInstallments = new java.util.ArrayList<>();
    for (var entry : acquirerRvToInstNum.entrySet()) {
      UUID acquirerId = entry.getKey();
      Map<Integer, Integer> rvToInstNum = entry.getValue();
      List<InstallmentAcqEntity> batch = installmentAcqRepository
        .findByAcquirerIdAndRvNumbers(acquirerId, rvToInstNum.keySet(), reprocess);
      for (InstallmentAcqEntity ia : batch) {
        if (ia.getTransaction() == null) continue;
        Integer rv = ia.getTransaction().getRvNumber();
        Integer expectedInst = rv != null ? rvToInstNum.get(rv) : null;
        if (expectedInst != null && expectedInst.equals(ia.getInstallment())) {
          allInstallments.add(ia);
        }
      }
    }

    if (allInstallments.isEmpty()) {
      log.debug("⏳ Nenhuma parcela ADQ encontrada para propagar. release={}, ordens={}", release.getId(), orders.size());
      return;
    }

    applyReleaseToInstallments(allInstallments, release);
    installmentAcqRepository.saveAll(allInstallments);
    log.debug("✅ {} parcela(s) ADQ propagada(s) para {} ordem(ns). release={}", allInstallments.size(), orders.size(), release.getId());
  }

  private void applyReleaseToInstallments(List<InstallmentAcqEntity> installments, ReleasesBankEntity release) {
    OffsetDateTime now = OffsetDateTime.now();
    for (InstallmentAcqEntity installment : installments) {
      installment.setReleaseBank(release);
      installment.setPaymentDate(release.getReleaseDate());
      installment.setStatusPaymentBank(STATUS_LIQUIDATED);
      installment.setInstallmentStatus(STATUS_INSTALLMENT_RECONCILED);
      installment.setReconciliationBankLine(release.getLineNumber());
      installment.setReconciliationBankProcessedAt(now);
      installment.setReconciliationBankFile(release.getProcessedFile());
      if (installment.getCreditOrder() != null) {
        installment.getCreditOrder().setReleaseBank(release);
        installment.getCreditOrder().setStatusPaymentBank(StatusPaymentBankEnum.PAID);
        installment.getCreditOrder().setReconciliationStatus(STATUS_LIQUIDATED);
        installment.getCreditOrder().setCreditStatus(STATUS_LIQUIDATED);
      }
    }
  }

  private int propagateReleaseStatusTransactions(ReleasesBankEntity release) {
    if (release.getId() == null) return 0;
    List<InstallmentAcqEntity> linkedInstallments = installmentAcqRepository.findByReleaseBank_Id(release.getId());
    if (linkedInstallments.isEmpty()) return 0;

    Set<UUID> transactionIds = linkedInstallments.stream()
      .map(InstallmentAcqEntity::getTransaction)
      .filter(Objects::nonNull)
      .map(TransactionAcqEntity::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    Map<UUID, List<InstallmentAcqEntity>> installmentsByTx = installmentAcqRepository
      .findByTransactionIdIn(transactionIds).stream()
      .filter(i -> i.getTransaction() != null && i.getTransaction().getId() != null)
      .collect(Collectors.groupingBy(i -> i.getTransaction().getId()));

    Map<UUID, TransactionErpEntity> erpByTxId = transactionErpRepository
      .findByTransactionAcqIdIn(transactionIds).stream()
      .filter(e -> e.getTransactionAcq() != null && e.getTransactionAcq().getId() != null)
      .collect(Collectors.toMap(e -> e.getTransactionAcq().getId(), e -> e, (a, b) -> a));

    Set<UUID> updatedTransactions = new HashSet<>();
    for (InstallmentAcqEntity linked : linkedInstallments) {
      TransactionAcqEntity transaction = linked.getTransaction();
      if (transaction == null || transaction.getId() == null || updatedTransactions.contains(transaction.getId())) continue;
      List<InstallmentAcqEntity> txInstallments = installmentsByTx.getOrDefault(transaction.getId(), List.of());
      updateStatusTransactionBatched(transaction, txInstallments, erpByTxId.get(transaction.getId()));
      updatedTransactions.add(transaction.getId());
    }
    return updatedTransactions.size();
  }

  private void updateStatusTransactionBatched(
    TransactionAcqEntity transaction,
    List<InstallmentAcqEntity> installments,
    TransactionErpEntity erpTx
  ) {
    if (transaction == null || installments.isEmpty()) return;

    boolean allLiquidated = installments.stream().allMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));
    boolean anyLiquidated = installments.stream().anyMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));

    if (allLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      transaction.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
    } else if (anyLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.DIVERGENT);
    } else {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      transaction.setStatusTransaction(StatusTransactionEnum.PENDING);
    }

    updateSalesSummaryFromTransaction(transaction);

    if (erpTx != null) {
      if (allLiquidated) {
        erpTx.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
      } else if (!anyLiquidated) {
        erpTx.setStatusTransaction(StatusTransactionEnum.PENDING);
      }
    }
  }

  private void updateSalesSummaryFromTransaction(TransactionAcqEntity transaction) {
    SalesSummaryEntity summary = transaction.getSalesSummary();
    if (summary == null) return;
    summary.setStatusPaymentBank(transaction.getStatusPaymentBank());
  }

  private void updateSalesSummaryFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null) return;
    summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
  }

  private boolean isCreditOrderCandidateCompatible(ReleasesBankEntity release, CreditOrderEntity order, int toleranceDaysBefore, int toleranceDaysAfter) {
    if (order == null || order.getReleaseValue() == null || order.getReleaseDate() == null) return false;
    // daysDiff > 0: lançamento DEPOIS da ordem (normal); < 0: lançamento ANTES da ordem (suspeito)
    long daysDiff = ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate());
    if (daysDiff > toleranceDaysAfter) return false;
    if (daysDiff < -toleranceDaysBefore) return false;
    if (!contextOf(release).compatible(contextOf(order))) return false;
    // Banco obrigatório: release.bank vs order.bankingDomicile.bank
    UUID releaseBank = idOrNull(release.getBank());
    UUID orderBank = order.getBankingDomicile() != null ? idOrNull(order.getBankingDomicile().getBank()) : null;
    if (releaseBank == null || orderBank == null || !releaseBank.equals(orderBank)) return false;
    return true;
  }

  private boolean isInstallmentCandidateCompatible(ReleasesBankEntity release, InstallmentAcqEntity installment, int toleranceDaysBefore, int toleranceDaysAfter) {
    if (installment == null || installment.getExpectedPaymentDate() == null) return false;
    if (installment.getTransaction() != null && installment.getTransaction().getSaleDate() != null) {
      LocalDate saleDate = installment.getTransaction().getSaleDate().toLocalDate();
      if (release.getReleaseDate().isBefore(saleDate)) return false;
    }
    // daysDiff > 0: lançamento DEPOIS do pagamento esperado (normal); < 0: lançamento ANTES
    long daysDiff = ChronoUnit.DAYS.between(installment.getExpectedPaymentDate(), release.getReleaseDate());
    if (daysDiff > toleranceDaysAfter) return false;
    if (daysDiff < -toleranceDaysBefore) return false;
    return contextOf(release).compatible(contextOf(installment));
  }

  private ReconciliationMatchContext contextOf(ReleasesBankEntity release) {
    return new ReconciliationMatchContext(
      idOrNull(release.getCompany()),
      idOrNull(release.getAcquirer()),
      idOrNull(release.getEstablishment()),
      idOrNull(release.getFlag()),
      paymentKindFromBank(release.getModalityPaymentBank().getCode(), release.getDescriptionHistoricalBank(),
        release.getComplementRelease(), release.getDocumentComplementNumber())
    );
  }

  private ReconciliationMatchContext contextOf(CreditOrderEntity order) {
    return new ReconciliationMatchContext(
      idOrNull(order.getCompany()),
      idOrNull(order.getAcquirer()),
      null,
      idOrNull(order.getFlag()),
      paymentKindFromCreditOrder(order)
    );
  }

  private ReconciliationMatchContext contextOf(InstallmentAcqEntity installment) {
    TransactionAcqEntity tx = installment.getTransaction();
    if (tx == null) {
      return new ReconciliationMatchContext(null, null, null, null, ReconciliationMatchContext.PaymentKind.UNKNOWN);
    }
    return new ReconciliationMatchContext(
      idOrNull(tx.getCompany()),
      idOrNull(tx.getAcquirer()),
      idOrNull(tx.getEstablishment()),
      idOrNull(tx.getFlag()),
      paymentKindFromTransaction(tx)
    );
  }

  /**
   * order.getTransactionType() vem bruto da posição 93-94 do EEFI (Rede) e codifica
   * "à vista (1) vs. parcelado (2-5)" — não débito/crédito. Todo pedido de crédito
   * importado do EEFI é, por natureza, uma transação de CRÉDITO (débito à vista
   * liquida via EEVD, sem passar por CreditOrder). Por isso derivamos o tipo de
   * pagamento da modalidade real do resumo de vendas vinculado, igual ao caminho
   * de geração manual (ver transactionTypeFromSummary), em vez do campo bruto.
   */
  private ReconciliationMatchContext.PaymentKind paymentKindFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null) return ReconciliationMatchContext.PaymentKind.UNKNOWN;
    return paymentKindFromModality(summary.getModality());
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromTransaction(TransactionAcqEntity tx) {
    return paymentKindFromModality(tx.getModality());
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromModality(Integer modalityCode) {
    ModalityEnum modality = ModalityEnum.fromCode(modalityCode);
    if (modality == ModalityEnum.CASH_DEBIT) return ReconciliationMatchContext.PaymentKind.DEBIT;
    if (modality == ModalityEnum.CASH_CREDIT
      || modality == ModalityEnum.INSTALLMENT_CREDIT_2_6
      || modality == ModalityEnum.INSTALLMENT_CREDIT_7_12
      || modality == ModalityEnum.INSTALLMENT_CREDIT_13_21) {
      return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    return ReconciliationMatchContext.PaymentKind.UNKNOWN;
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromBank(Integer modalityPaymentBank, String... textParts) {
    if (modalityPaymentBank != null) {
      if (modalityPaymentBank == 1) return ReconciliationMatchContext.PaymentKind.DEBIT;
      if (modalityPaymentBank == 2) return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    String text = String.join(" ", textParts == null ? new String[0] : textParts).toUpperCase();
    if (text.contains("DEBIT") || text.contains("DÉBIT") || text.contains("DEB ") || text.contains("ELECTRON") || text.contains("MAESTRO")) {
      return ReconciliationMatchContext.PaymentKind.DEBIT;
    }
    if (text.contains("CRED") || text.contains("CRÉD") || text.contains("VISA") || text.contains("MASTER") || text.contains("ELO") || text.contains("AMEX")) {
      return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    return ReconciliationMatchContext.PaymentKind.UNKNOWN;
  }

  private boolean hasRequiredContext(ReleasesBankEntity release) {
    return release.getReleaseDate() != null
      && release.getReleaseValue() != null
      && release.getCompany() != null
      && release.getCompany().getId() != null
      && release.getBank() != null
      && release.getBank().getId() != null;
  }

  private void markReleaseNotReconciledWhenExpired(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    String reason,
    BankReconciliationResult.Counter result
  ) {
    if (!shouldMarkNotReconciled(release, config)) {
      result.releaseKeptPending();
      log.debug(
        "⏳ Release bancário mantido pendente. releaseBank={}, data={}, valor={}, motivo={}",
        release.getId(), release.getReleaseDate(), release.getReleaseValue(), reason
      );
      return;
    }

    if (release.getReconciliationStatus() == null || Objects.equals(release.getReconciliationStatus(), STATUS_PENDING)) {
      release.setReconciliationStatus(StatusPaymentBankEnum.PAID);
      releasesBankRepository.save(release);
    }
    result.releaseWithoutMatch();
    log.info(
      "⚠ Release bancário marcado como não conciliado. releaseBank={}, data={}, valor={}, motivo={}",
      release.getId(), release.getReleaseDate(), release.getReleaseValue(), reason
    );
  }

  private boolean shouldMarkNotReconciled(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int days = Math.max(reconciliationSettingsService.getBankMarkNotReconciledAfterDays(), 0);
    if (release.getReleaseDate() == null) return true;
    LocalDate limitDate = LocalDate.now().minusDays(days);
    return !release.getReleaseDate().isAfter(limitDate);
  }

  private BigDecimal netInstallmentValue(InstallmentAcqEntity installment) {
    BigDecimal value = nvl(installment.getLiquidValue());
    if (installment.getAdjustmentValue() != null) {
      value = value.subtract(installment.getAdjustmentValue());
    }
    return value;
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : value;
  }

  private UUID idOrNull(Object entity) {
    if (entity == null) return null;
    try {
      return (UUID) entity.getClass().getMethod("getId").invoke(entity);
    } catch (Exception ex) {
      return null;
    }
  }
}