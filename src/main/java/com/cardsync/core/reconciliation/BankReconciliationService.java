package com.cardsync.core.reconciliation;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

  private static final int STATUS_PENDING = BankReconciliationStatus.PENDING.getCode();
  private static final int STATUS_LIQUIDATED = BankReconciliationStatus.RECONCILED.getCode();
  private static final int STATUS_NOT_RECONCILED = BankReconciliationStatus.NOT_RECONCILED.getCode();
  private static final int STATUS_INSTALLMENT_RECONCILED = BankReconciliationStatus.INSTALLMENT_RECONCILED.getCode();

  /**
   * Gate da etapa 6: só ordens cujo resumo já foi conciliado com a ordem (etapa 5)
   * participam da conciliação com o extrato bancário.
   */
  private static final int SUMMARY_RECONCILED_STATUS = StatusReconciliationEnum.RECONCILED.getCode();

  private final BankReconciliationMatcher matcher;
  private final FileProcessingProperties properties;
  private final CreditOrderRepository creditOrderRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final InstallmentAcqRepository installmentAcqRepository;

  @Transactional
  public BankReconciliationResult reconcilePending() {
    return reconcilePending(BankReconciliationTriggerType.MANUAL);
  }

  @Transactional
  public BankReconciliationResult reconcilePending(BankReconciliationTriggerType trigger) {
    FileProcessingProperties.Reconciliation config = properties.getReconciliation();
    BankReconciliationMode mode = normalizeMode(config.getBankMode());
    BankReconciliationResult.Counter result = BankReconciliationResult.counter(trigger, mode);

    List<CreditOrderEntity> eligibleOrders = creditOrderRepository.findEligibleForBankReconciliation(
      STATUS_PENDING,
      SUMMARY_RECONCILED_STATUS
    );

    log.info(
      "📌 Iniciando conciliação Banco x Adquirente por ordens de pagamento: trigger={}, modo={}, ordensElegiveis={}, toleranciaDias={}, toleranciaValor={}, reprocessarConciliados={}",
      trigger.getCode(),
      mode,
      eligibleOrders.size(),
      config.getDateToleranceDays(),
      config.valueToleranceAsBigDecimal(),
      config.isReconcileAlreadyReconciledBankAcquirer()
    );

    Set<UUID> reconciledOrderIds = new HashSet<>();
    Set<UUID> analyzedReleaseIds = new HashSet<>();

    if (mode.shouldTryCreditOrders()) {
      reconcileEligibleCreditOrders(
        eligibleOrders,
        reconciledOrderIds,
        analyzedReleaseIds,
        config,
        result
      );
    }

    if (mode.shouldTryInstallmentsAfterCreditOrders() || mode.shouldTryInstallmentsFirst()) {
      reconcilePendingReleasesByInstallments(config, analyzedReleaseIds, result);
    }

    BankReconciliationResult built = result.toResult();
    log.info(
      "📘 RESUMO FINAL CONCILIAÇÃO BANCO x ADQUIRENTE: trigger={}, modo={}, ordensElegiveis={}, ordensConciliadas={}, releasesAnalisados={}, releasesConciliados={}, porOrdensCredito={}, porParcelas={}, parcelasConciliadas={}, transacoesAtualizadas={}, semMatch={}, mantidosPendentes={}, semContexto={}, gruposIgnoradosSafetyCap={}, valorBancoConciliado={}, valorOrdensConciliado={}, valorParcelasConciliado={}",
      built.getTrigger().getCode(),
      built.getMode(),
      eligibleOrders.size(),
      built.getCreditOrdersReconciled(),
      built.getReleasesAnalyzed(),
      built.getReleasesReconciled(),
      built.getReleasesMatchedByCreditOrders(),
      built.getReleasesMatchedByInstallments(),
      built.getInstallmentsReconciled(),
      built.getTransactionsUpdated(),
      built.getReleasesWithoutMatch(),
      built.getReleasesKeptPending(),
      built.getReleasesSkippedMissingContext(),
      built.getCandidateGroupsSkippedBySafetyCap(),
      built.getTotalReleaseValueReconciled(),
      built.getTotalCreditOrderValueReconciled(),
      built.getTotalInstallmentValueReconciled()
    );
    return built;
  }

  private void reconcileEligibleCreditOrders(
    List<CreditOrderEntity> eligibleOrders,
    Set<UUID> reconciledOrderIds,
    Set<UUID> analyzedReleaseIds,
    FileProcessingProperties.Reconciliation config,
    BankReconciliationResult.Counter result
  ) {
    for (CreditOrderEntity seedOrder : eligibleOrders) {
      if (!isOrderStillEligible(seedOrder, reconciledOrderIds)) continue;

      if (!hasRequiredContext(seedOrder)) {
        log.warn(
          "⚠ Ordem de pagamento ignorada por falta de contexto. creditOrder={}, company={}, bankingDomicile={}, releaseDate={}, releaseValue={}",
          seedOrder.getId(),
          idOrNull(seedOrder.getCompany()),
          idOrNull(seedOrder.getBankingDomicile()),
          seedOrder.getReleaseDate(),
          seedOrder.getReleaseValue()
        );
        continue;
      }

      List<ReleasesBankEntity> releases = findCandidateReleases(seedOrder, config).stream()
        .filter(this::hasRequiredContext)
        .filter(release -> isCreditOrderCandidateCompatible(release, seedOrder, config.getDateToleranceDays()))
        .sorted(candidateReleaseComparator(seedOrder))
        .toList();

      boolean reconciled = false;
      for (ReleasesBankEntity release : releases) {
        if (release.getId() != null && analyzedReleaseIds.add(release.getId())) {
          result.releaseAnalyzed();
        }

        BankReconciliationMatcher.MatchResult selected = selectOrdersIncludingSeed(
          seedOrder,
          release,
          eligibleOrders,
          reconciledOrderIds,
          config
        );

        if (selected.skippedBySafetyCap()) {
          result.candidateGroupSkippedBySafetyCap();
        }
        if (!selected.matched()) continue;

        List<CreditOrderEntity> orders = selected.typedItems();
        applyCreditOrderMatch(release, orders, selected, result);
        orders.stream()
          .map(CreditOrderEntity::getId)
          .filter(Objects::nonNull)
          .forEach(reconciledOrderIds::add);
        reconciled = true;
        break;
      }

      if (!reconciled) {
        log.debug(
          "⏳ Nenhum lançamento bancário compatível encontrado para a ordem. creditOrder={}, data={}, valor={}",
          seedOrder.getId(), seedOrder.getReleaseDate(), seedOrder.getReleaseValue()
        );
      }
    }
  }

  private List<ReleasesBankEntity> findCandidateReleases(
    CreditOrderEntity order,
    FileProcessingProperties.Reconciliation config
  ) {
    int toleranceDays = Math.max(config.getDateToleranceDays(), 0);
    LocalDate dateFrom = order.getReleaseDate();
    LocalDate dateTo = order.getReleaseDate().plusDays(toleranceDays);

    return releasesBankRepository.findCandidatesForCreditOrder(
      STATUS_PENDING,
      config.isReconcileAlreadyReconciledBankAcquirer(),
      order.getCompany().getId(),
      idOrNull(order.getAcquirer()),
      order.getBankingDomicile().getId(),
      idOrNull(order.getFlag()),
      modalityPaymentBankFromCreditOrder(order),
      dateFrom,
      dateTo
    );
  }

  private BankReconciliationMatcher.MatchResult selectOrdersIncludingSeed(
    CreditOrderEntity seedOrder,
    ReleasesBankEntity release,
    List<CreditOrderEntity> eligibleOrders,
    Set<UUID> reconciledOrderIds,
    FileProcessingProperties.Reconciliation config
  ) {
    BigDecimal tolerance = config.valueToleranceAsBigDecimal();
    BigDecimal seedValue = nvl(seedOrder.getReleaseValue());
    BigDecimal remainingTarget = nvl(release.getReleaseValue()).subtract(seedValue);

    if (remainingTarget.abs().compareTo(tolerance) <= 0) {
      return BankReconciliationMatcher.MatchResult.matched(List.of(seedOrder), seedValue, false);
    }
    if (remainingTarget.signum() < 0) {
      return matcher.notMatched();
    }

    List<CreditOrderEntity> complementaryOrders = eligibleOrders.stream()
      .filter(order -> order != seedOrder)
      .filter(order -> isOrderStillEligible(order, reconciledOrderIds))
      .filter(order -> isCreditOrderCandidateCompatible(release, order, config.getDateToleranceDays()))
      .toList();

    BankReconciliationMatcher.MatchResult remainder = matcher.selectByValue(
      complementaryOrders,
      CreditOrderEntity::getReleaseValue,
      remainingTarget,
      tolerance,
      config.getSafeCapCents(),
      config.getSubsetDpMaxCents()
    );

    if (!remainder.matched()) return remainder;

    List<CreditOrderEntity> selected = new java.util.ArrayList<>();
    selected.add(seedOrder);
    selected.addAll(remainder.typedItems());
    BigDecimal matchedValue = seedValue.add(remainder.matchedValue());
    return BankReconciliationMatcher.MatchResult.matched(selected, matchedValue, remainder.skippedBySafetyCap());
  }

  private void applyCreditOrderMatch(
    ReleasesBankEntity release,
    List<CreditOrderEntity> orders,
    BankReconciliationMatcher.MatchResult selected,
    BankReconciliationResult.Counter result
  ) {
    BankReconciliationMatchType matchType = BankReconciliationMatchType.creditOrderByCount(orders.size());

    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(release);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      updateSalesSummaryFromCreditOrder(order);
      propagateCreditOrderToInstallments(order, release);
    }

    release.setNumberCreditOrders(orders.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + orders.size());
    release.setReconciliationStatus(STATUS_LIQUIDATED);

    creditOrderRepository.saveAll(orders);
    releasesBankRepository.save(release);

    result.matchedByCreditOrders(orders.size(), selected.matchedValue());
    result.releaseReconciled(release.getReleaseValue());
    result.transactionsUpdated(propagateReleaseStatusTransactions(release));

    log.info(
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
      config.isReconcileAlreadyReconciledBankAcquirer()
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

  private boolean isOrderStillEligible(CreditOrderEntity order, Set<UUID> reconciledOrderIds) {
    if (order == null || order.getReleaseBank() != null) return false;
    if (order.getId() != null && reconciledOrderIds.contains(order.getId())) return false;
    return order.getReleaseDate() != null && order.getReleaseValue() != null;
  }

  private boolean hasRequiredContext(CreditOrderEntity order) {
    return order != null
      && order.getReleaseDate() != null
      && order.getReleaseValue() != null
      && order.getCompany() != null
      && order.getCompany().getId() != null
      && order.getBankingDomicile() != null
      && order.getBankingDomicile().getId() != null;
  }

  private Comparator<ReleasesBankEntity> candidateReleaseComparator(CreditOrderEntity order) {
    ReconciliationMatchContext orderContext = contextOf(order);
    return Comparator
      .comparingInt((ReleasesBankEntity release) -> orderContext.strength(contextOf(release))).reversed()
      .thenComparingLong(release -> Math.abs(ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate())))
      .thenComparing(release -> nvl(release.getReleaseValue()).subtract(nvl(order.getReleaseValue())).abs());
  }

  private Integer modalityPaymentBankFromCreditOrder(CreditOrderEntity order) {
    return switch (paymentKindFromCreditOrder(order)) {
      case DEBIT -> 1;
      case CREDIT -> 2;
      default -> null;
    };
  }

  private BankReconciliationMatcher.MatchResult tryReconcileByConfiguredMode(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    BankReconciliationMode mode,
    BankReconciliationResult.Counter result
  ) {
    if (mode.shouldTryInstallmentsFirst()) {
      return reconcileByInstallmentsWithStats(release, config, result);
    }

    if (mode.shouldTryCreditOrders()) {
      BankReconciliationMatcher.MatchResult creditOrderResult = reconcileByCreditOrdersWithStats(release, config, result);
      if (creditOrderResult.matched() || !mode.shouldTryInstallmentsAfterCreditOrders()) {
        return creditOrderResult;
      }
    }

    if (mode.shouldTryInstallmentsAfterCreditOrders()) {
      return reconcileByInstallmentsWithStats(release, config, result);
    }

    return matcher.notMatched();
  }

  private BankReconciliationMatcher.MatchResult reconcileByCreditOrdersWithStats(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    BankReconciliationResult.Counter result
  ) {
    BankReconciliationMatcher.MatchResult creditOrderResult = reconcileByCreditOrders(release, config);
    if (creditOrderResult.skippedBySafetyCap()) {
      result.candidateGroupSkippedBySafetyCap();
    }
    if (creditOrderResult.matched()) {
      result.matchedByCreditOrders(creditOrderResult.itemsMatched(), creditOrderResult.matchedValue());
    }
    return creditOrderResult;
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

  private BankReconciliationMatcher.MatchResult reconcileByCreditOrders(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int toleranceDays = config.getDateToleranceDays();
    BigDecimal valueTolerance = config.valueToleranceAsBigDecimal();
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDays);
    LocalDate dateTo = release.getReleaseDate();

    ReconciliationMatchContext releaseContext = contextOf(release);
    List<CreditOrderEntity> candidates = creditOrderRepository.findPendingForBankRelease(
        STATUS_PENDING,
        SUMMARY_RECONCILED_STATUS,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        release.getBankingDomicile().getId(),
        idOrNull(release.getFlag()),
        release.getModalityPaymentBank(),
        dateFrom,
        dateTo
      ).stream()
      .filter(order -> isCreditOrderCandidateCompatible(release, order, toleranceDays))
      .sorted(Comparator.comparingInt(
        (CreditOrderEntity order) -> releaseContext.strength(contextOf(order))).reversed())
      .toList();

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      candidates,
      CreditOrderEntity::getReleaseValue,
      release.getReleaseValue(),
      valueTolerance,
      config.getSafeCapCents(),
      config.getSubsetDpMaxCents()
    );

    if (!selected.matched()) return selected;

    List<CreditOrderEntity> orders = selected.typedItems();
    BankReconciliationMatchType matchType = BankReconciliationMatchType.creditOrderByCount(orders.size());

    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(release);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      updateSalesSummaryFromCreditOrder(order);
      propagateCreditOrderToInstallments(order, release);
    }

    release.setNumberCreditOrders(orders.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + orders.size());
    release.setReconciliationStatus(STATUS_LIQUIDATED);

    creditOrderRepository.saveAll(orders);
    releasesBankRepository.save(release);

    log.info(
      "✅ Release bancário conciliado por ordem de crédito. releaseBank={}, tipoMatch={}, ordens={}, valorRelease={}, valorOrdens={}",
      release.getId(), matchType, orders.size(), release.getReleaseValue(), selected.matchedValue()
    );
    return selected;
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallments(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int toleranceDays = config.getDateToleranceDays();
    BigDecimal valueTolerance = config.valueToleranceAsBigDecimal();
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDays);
    LocalDate dateTo = release.getReleaseDate().plusDays(toleranceDays);

    ReconciliationMatchContext releaseContext = contextOf(release);
    List<InstallmentAcqEntity> candidates = installmentAcqRepository.findPendingForBankRelease(
        STATUS_PENDING,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        idOrNull(release.getEstablishment()),
        release.getBankingDomicile().getId(),
        idOrNull(release.getFlag()),
        dateFrom,
        dateTo
      ).stream()
      .filter(installment -> isInstallmentCandidateCompatible(release, installment, toleranceDays))
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
    release.setReconciliationStatus(STATUS_LIQUIDATED);

    installmentAcqRepository.saveAll(installments);
    releasesBankRepository.save(release);

    log.info(
      "✅ Release bancário conciliado por parcelas. releaseBank={}, tipoMatch={}, parcelas={}, valorRelease={}, valorParcelas={}",
      release.getId(), matchType, installments.size(), release.getReleaseValue(), selected.matchedValue()
    );
    return selected;
  }

  private void propagateCreditOrderToInstallments(CreditOrderEntity order, ReleasesBankEntity release) {
    if (order.getId() == null) return;
    List<InstallmentAcqEntity> installments = installmentAcqRepository.findByCreditOrder_Id(order.getId());
    applyReleaseToInstallments(installments, release);
    installmentAcqRepository.saveAll(installments);
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
    Set<UUID> updatedTransactions = new HashSet<>();

    for (InstallmentAcqEntity linked : linkedInstallments) {
      TransactionAcqEntity transaction = linked.getTransaction();
      if (transaction == null || transaction.getId() == null || updatedTransactions.contains(transaction.getId())) continue;
      updateStatusTransaction(transaction);
      updatedTransactions.add(transaction.getId());
    }
    return updatedTransactions.size();
  }

  private void updateStatusTransaction(TransactionAcqEntity transaction) {
    if (transaction == null || transaction.getId() == null) return;
    List<InstallmentAcqEntity> installments = installmentAcqRepository.findByTransaction_Id(transaction.getId());
    if (installments.isEmpty()) return;

    boolean allLiquidated = installments.stream().allMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));
    boolean anyLiquidated = installments.stream().anyMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));

    if (allLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      transaction.setStatusTransaction(StatusReconciliationEnum.RECONCILED);
    } else if (anyLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.DIVERGENT);
    } else {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      transaction.setStatusTransaction(StatusReconciliationEnum.PENDING);
    }

    updateSalesSummaryFromTransaction(transaction);
  }

  private void updateSalesSummaryFromTransaction(TransactionAcqEntity transaction) {
    SalesSummaryEntity summary = transaction.getSalesSummary();
    if (summary == null) return;
    summary.setStatusPaymentBank(transaction.getStatusPaymentBank());
    summary.setTransactionsStatus(transaction.getStatusTransaction());
  }

  private void updateSalesSummaryFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null) return;
    summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
  }

  private boolean isCreditOrderCandidateCompatible(ReleasesBankEntity release, CreditOrderEntity order, int toleranceDays) {
    if (order == null || order.getReleaseValue() == null || order.getReleaseDate() == null) return false;
    if (order.getReleaseDate().isAfter(release.getReleaseDate())) return false;
    if (ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate()) > toleranceDays) return false;
    return contextOf(release).compatible(contextOf(order));
  }

  private boolean isInstallmentCandidateCompatible(ReleasesBankEntity release, InstallmentAcqEntity installment, int toleranceDays) {
    if (installment == null || installment.getExpectedPaymentDate() == null) return false;
    if (installment.getTransaction() != null && installment.getTransaction().getSaleDate() != null) {
      LocalDate saleDate = installment.getTransaction().getSaleDate().toLocalDate();
      if (release.getReleaseDate().isBefore(saleDate)) return false;
    }
    long diff = Math.abs(ChronoUnit.DAYS.between(installment.getExpectedPaymentDate(), release.getReleaseDate()));
    if (diff > toleranceDays) return false;
    return contextOf(release).compatible(contextOf(installment));
  }

  private ReconciliationMatchContext contextOf(ReleasesBankEntity release) {
    return new ReconciliationMatchContext(
      idOrNull(release.getCompany()),
      idOrNull(release.getAcquirer()),
      idOrNull(release.getEstablishment()),
      idOrNull(release.getFlag()),
      paymentKindFromBank(release.getModalityPaymentBank(), release.getDescriptionHistoricalBank(), release.getComplementRelease(), release.getDocumentComplementNumber())
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

  private ReconciliationMatchContext.PaymentKind paymentKindFromCreditOrder(CreditOrderEntity order) {
    Integer type = order.getTransactionType();
    if (type == null) return ReconciliationMatchContext.PaymentKind.UNKNOWN;
    if (type == 1) return ReconciliationMatchContext.PaymentKind.DEBIT;
    if (type == 2 || type == 3 || type == 4 || type == 5) return ReconciliationMatchContext.PaymentKind.CREDIT;
    return ReconciliationMatchContext.PaymentKind.UNKNOWN;
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromTransaction(TransactionAcqEntity tx) {
    ModalityEnum modality = ModalityEnum.fromCode(tx.getModality());
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
      && release.getBankingDomicile() != null
      && release.getBankingDomicile().getId() != null;
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
      release.setReconciliationStatus(STATUS_NOT_RECONCILED);
      releasesBankRepository.save(release);
    }
    result.releaseWithoutMatch();
    log.info(
      "⚠ Release bancário marcado como não conciliado. releaseBank={}, data={}, valor={}, motivo={}",
      release.getId(), release.getReleaseDate(), release.getReleaseValue(), reason
    );
  }

  private boolean shouldMarkNotReconciled(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int days = Math.max(config.getBankMarkNotReconciledAfterDays(), 0);
    if (release.getReleaseDate() == null) return true;
    LocalDate limitDate = LocalDate.now().minusDays(days);
    return !release.getReleaseDate().isAfter(limitDate);
  }

  private BankReconciliationMode normalizeMode(BankReconciliationMode mode) {
    return mode == null ? BankReconciliationMode.CREDIT_ORDER_FIRST : mode;
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