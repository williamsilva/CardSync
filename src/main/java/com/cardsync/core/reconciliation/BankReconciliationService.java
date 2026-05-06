package com.cardsync.core.reconciliation;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

  private static final int STATUS_PENDING = StatusPaymentEnum.PENDING.getCode();
  private static final int STATUS_LIQUIDATED = StatusPaymentEnum.PAID.getCode();
  private static final int STATUS_RECONCILED = StatusInstallmentEnum.RECONCILED.getCode();
  private static final int STATUS_PARTIALLY_LIQUIDATED = 4;
  private static final int STATUS_NOT_RECONCILED = StatusPaymentEnum.NOT_PAID.getCode();

  private static final int CREDIT_ORDER_STATUS_RECONCILED = 2;
  private static final int CREDIT_ORDER_STATUS_PENDING = 1;

  private final ReleasesBankRepository releasesBankRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final BankReconciliationMatcher matcher;
  private final FileProcessingProperties properties;

  @Transactional
  public BankReconciliationResult reconcilePending() {
    FileProcessingProperties.Reconciliation config = properties.getReconciliation();
    List<ReleasesBankEntity> releases = releasesBankRepository.findPendingForBankReconciliation(STATUS_PENDING);
    BankReconciliationResult.Builder result = BankReconciliationResult.builder();

    for (ReleasesBankEntity release : releases) {
      result.releaseAnalyzed();

      if (!hasRequiredContext(release)) {
        markReleaseNotReconciled(release);
        result.releaseSkippedMissingContext();
        continue;
      }

      BankReconciliationMatcher.MatchResult creditOrderResult = reconcileByCreditOrders(release, config);
      if (creditOrderResult.skippedBySafetyCap()) result.candidateGroupSkippedBySafetyCap();
      if (creditOrderResult.matched()) {
        result.releaseReconciled(release.getReleaseValue());
        result.matchedByCreditOrders(creditOrderResult.itemsMatched(), creditOrderResult.matchedValue());
        result.transactionsUpdated(propagateReleaseTransactionStatuses(release));
        continue;
      }

      BankReconciliationMatcher.MatchResult installmentResult = reconcileByInstallments(release, config);
      if (installmentResult.skippedBySafetyCap()) result.candidateGroupSkippedBySafetyCap();
      if (installmentResult.matched()) {
        result.releaseReconciled(release.getReleaseValue());
        result.matchedByInstallments(installmentResult.itemsMatched(), installmentResult.matchedValue());
        result.transactionsUpdated(propagateReleaseTransactionStatuses(release));
      } else {
        markReleaseNotReconciled(release);
        result.releaseWithoutMatch();
      }
    }

    BankReconciliationResult built = result.build();
    log.info("✅ Conciliação bancária finalizada: releasesAnalisados={}, releasesConciliados={}, " +
        "porOrdensCredito={}, porParcelas={}, semMatch={}, semContexto={}, ordensConciliadas={}, parcelasConciliadas={}",
      built.releasesAnalyzed(),
      built.releasesReconciled(),
      built.releasesMatchedByCreditOrders(),
      built.releasesMatchedByInstallments(),
      built.releasesWithoutMatch(),
      built.releasesSkippedMissingContext(),
      built.creditOrdersReconciled(),
      built.installmentsReconciled()
    );
    return built;
  }

  private BankReconciliationMatcher.MatchResult reconcileByCreditOrders(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int toleranceDays = config.getDateToleranceDays();
    BigDecimal valueTolerance = config.valueToleranceAsBigDecimal();
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDays);
    LocalDate dateTo = release.getReleaseDate();

    List<CreditOrderEntity> candidates = creditOrderRepository.findPendingForBankRelease(
        CREDIT_ORDER_STATUS_PENDING,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        release.getBankingDomicile().getId(),
        dateFrom,
        dateTo
      ).stream()
      .filter(order -> isCreditOrderCandidateCompatible(release, order, toleranceDays))
      .toList();

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      candidates,
      CreditOrderEntity::getReleaseValue,
      release.getReleaseValue(),
      valueTolerance,
      config.getRecursiveLimit(),
      config.getSafeCapCents()
    );

    if (!selected.matched()) return selected;

    List<CreditOrderEntity> orders = selected.typedItems();
    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(release);
      order.setStatusPaymentBank(STATUS_LIQUIDATED);
      order.setReconciliationStatus(CREDIT_ORDER_STATUS_RECONCILED);
      order.setCreditStatus(CREDIT_ORDER_STATUS_RECONCILED);
      updateSalesSummaryFromCreditOrder(order);
      propagateCreditOrderToInstallments(order, release);
    }

    release.setNumberCreditOrders(orders.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + orders.size());
    release.setReconciliationStatus(CREDIT_ORDER_STATUS_RECONCILED);

    creditOrderRepository.saveAll(orders);
    releasesBankRepository.save(release);

    log.info("✅ Release bancário conciliado por ordem de crédito. releaseBank={}, ordens={}, valorRelease={}, valorOrdens={}",
      release.getId(), orders.size(), release.getReleaseValue(), selected.matchedValue());
    return selected;
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallments(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int toleranceDays = config.getDateToleranceDays();
    BigDecimal valueTolerance = config.valueToleranceAsBigDecimal();
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDays);
    LocalDate dateTo = release.getReleaseDate().plusDays(toleranceDays);

    List<InstallmentAcqEntity> candidates = installmentAcqRepository.findPendingForBankRelease(
        STATUS_PENDING,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        idOrNull(release.getEstablishment()),
        dateFrom,
        dateTo
      ).stream()
      .filter(installment -> isInstallmentCandidateCompatible(release, installment, toleranceDays))
      .toList();

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      candidates,
      this::netInstallmentValue,
      release.getReleaseValue(),
      valueTolerance,
      config.getRecursiveLimit(),
      config.getSafeCapCents()
    );

    if (!selected.matched()) return selected;

    List<InstallmentAcqEntity> installments = selected.typedItems();
    applyReleaseToInstallments(installments, release);

    release.setNumberParcels(installments.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + installments.size());
    release.setReconciliationStatus(CREDIT_ORDER_STATUS_RECONCILED);

    installmentAcqRepository.saveAll(installments);
    releasesBankRepository.save(release);

    log.info("✅ Release bancário conciliado por parcelas. releaseBank={}, parcelas={}, valorRelease={}, valorParcelas={}",
      release.getId(), installments.size(), release.getReleaseValue(), selected.matchedValue());
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
      installment.setInstallmentStatus(STATUS_RECONCILED);
      installment.setReconciliationBankLine(release.getLineNumber());
      installment.setReconciliationBankProcessedAt(now);
      installment.setReconciliationBankFile(release.getProcessedFile());
      if (installment.getCreditOrder() != null) {
        installment.getCreditOrder().setReleaseBank(release);
        installment.getCreditOrder().setStatusPaymentBank(STATUS_LIQUIDATED);
        installment.getCreditOrder().setReconciliationStatus(CREDIT_ORDER_STATUS_RECONCILED);
      }
    }
  }

  private int propagateReleaseTransactionStatuses(ReleasesBankEntity release) {
    if (release.getId() == null) return 0;
    List<InstallmentAcqEntity> linkedInstallments = installmentAcqRepository.findByReleaseBank_Id(release.getId());
    Set<UUID> updatedTransactions = new HashSet<>();

    for (InstallmentAcqEntity linked : linkedInstallments) {
      TransactionAcqEntity transaction = linked.getTransaction();
      if (transaction == null || transaction.getId() == null || updatedTransactions.contains(transaction.getId())) continue;
      updateTransactionStatus(transaction);
      updatedTransactions.add(transaction.getId());
    }
    return updatedTransactions.size();
  }

  private void updateTransactionStatus(TransactionAcqEntity transaction) {
    if (transaction == null || transaction.getId() == null) return;
    List<InstallmentAcqEntity> installments = installmentAcqRepository.findByTransaction_Id(transaction.getId());
    if (installments.isEmpty()) return;

    boolean allLiquidated = installments.stream().allMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));
    boolean anyLiquidated = installments.stream().anyMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));

    if (allLiquidated) {
      transaction.setStatusPaymentBank(STATUS_LIQUIDATED);
      transaction.setTransactionStatus(StatusTransactionEnum.AUTOMATICALLY_RECONCILED.getCode());
    } else if (anyLiquidated) {
      transaction.setStatusPaymentBank(STATUS_PARTIALLY_LIQUIDATED);
    } else {
      transaction.setStatusPaymentBank(STATUS_NOT_RECONCILED);
      transaction.setTransactionStatus(StatusTransactionEnum.NOT_RECONCILED.getCode());
    }

    updateSalesSummaryFromTransaction(transaction);
  }

  private void updateSalesSummaryFromTransaction(TransactionAcqEntity transaction) {
    SalesSummaryEntity summary = transaction.getSalesSummary();
    if (summary == null) return;
    summary.setStatusPaymentBank(transaction.getStatusPaymentBank());
    summary.setTransactionsStatus(transaction.getTransactionStatus());
  }

  private void updateSalesSummaryFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null) return;
    summary.setCreditOrderStatus(CREDIT_ORDER_STATUS_RECONCILED);
    summary.setStatusPaymentBank(STATUS_LIQUIDATED);
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
      idOrNull(release.getBankingDomicile()),
      idOrNull(release.getFlag()),
      paymentKindFromBank(release.getModalityPaymentBank(), release.getDescriptionHistoricalBank(), release.getComplementRelease())
    );
  }

  private ReconciliationMatchContext contextOf(CreditOrderEntity order) {
    return new ReconciliationMatchContext(
      idOrNull(order.getCompany()),
      idOrNull(order.getAcquirer()),
      null,
      idOrNull(order.getBankingDomicile()),
      idOrNull(order.getFlag()),
      paymentKindFromCreditOrder(order)
    );
  }

  private ReconciliationMatchContext contextOf(InstallmentAcqEntity installment) {
    TransactionAcqEntity tx = installment.getTransaction();
    if (tx == null) {
      return new ReconciliationMatchContext(null, null, null, null, null, ReconciliationMatchContext.PaymentKind.UNKNOWN);
    }
    return new ReconciliationMatchContext(
      idOrNull(tx.getCompany()),
      idOrNull(tx.getAcquirer()),
      idOrNull(tx.getEstablishment()),
      null,
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
      || modality == ModalityEnum.INSTALLMENT_CREDIT_13_18) {
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
    if (text.contains("DEBIT") || text.contains("DEB ") || text.contains("ELECTRON") || text.contains("MAESTRO")) {
      return ReconciliationMatchContext.PaymentKind.DEBIT;
    }
    if (text.contains("CRED") || text.contains("VISA") || text.contains("MASTER") || text.contains("ELO") || text.contains("AMEX")) {
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

  private void markReleaseNotReconciled(ReleasesBankEntity release) {
    if (release.getReconciliationStatus() == null || Objects.equals(release.getReconciliationStatus(), STATUS_PENDING)) {
      release.setReconciliationStatus(STATUS_NOT_RECONCILED);
      releasesBankRepository.save(release);
    }
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
