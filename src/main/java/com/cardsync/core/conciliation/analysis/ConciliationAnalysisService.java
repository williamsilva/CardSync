package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ConciliationAnalysisService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal RATE_TOLERANCE = BigDecimal.valueOf(0.01);
  private static final BigDecimal VALUE_TOLERANCE = BigDecimal.valueOf(0.05);

  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final SettledDebtRepository settledDebtRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final ContractedAcquirerRateLookupService contractedAcquirerRateLookupService;
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
    List<FeeAnalysisResult> feeAnalyses = acquirerSales.stream().map(this::analyzeFee).toList();

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

  @Transactional(readOnly = true)
  public Page<AcquirerSaleAnalysisModel> listAcquirerSales(Pageable pageable) {
    return transactionAcqRepository.findAll(pageable).map(this::toAcquirerSaleModel);
  }

  @Transactional(readOnly = true)
  public Page<ConciliationFeeAnalysisModel> listFees(Pageable pageable) {
    return transactionAcqRepository.findAll(pageable).map(this::toFeeModel);
  }

  @Transactional(readOnly = true)
  public Page<ErpVsAcquirerAnalysisModel> listErpVsAcquirer(Pageable pageable) {
    return transactionErpRepository.findAll(remapErpVsAcquirerPageable(pageable)).map(this::toErpVsAcquirerModel);
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileErpWithAcquirerBusinessContext() {
    List<TransactionErpEntity> erpSales = transactionErpRepository.findAll();
    List<TransactionAcqEntity> acquirerSales = transactionAcqRepository.findAll();

    int analyzed = 0;
    int matched = 0;
    int updated = 0;
    int skippedDivergent = 0;

    List<TransactionErpEntity> changedSales = new ArrayList<>();

    for (TransactionErpEntity erp : erpSales) {
      analyzed++;
      Optional<TransactionAcqEntity> acqMatch = findBestAcquirerMatch(erp, acquirerSales);
      if (acqMatch.isEmpty()) {
        continue;
      }

      TransactionAcqEntity acq = acqMatch.get();
      String status = comparisonStatus(erp, acq);
      if (!"MATCHED".equals(status)) {
        skippedDivergent++;
        continue;
      }

      matched++;
      if (applyAcquirerBusinessContext(erp, acq)) {
        updated++;
        changedSales.add(erp);
      }
    }

    if (!changedSales.isEmpty()) {
      transactionErpRepository.saveAll(changedSales);
    }

    return new ReconcileErpAcquirerResultModel(analyzed, matched, updated, skippedDivergent);
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
  public Page<ChargebackAnalysisModel> listChargebacks(Pageable pageable) {
    List<ChargebackAnalysisModel> items = new ArrayList<>();
    pendingDebtRepository.findAll().stream().filter(debitChargebackClassifier::isChargeback).map(this::toOpenChargebackModel).forEach(items::add);
    settledDebtRepository.findAll().stream().filter(debitChargebackClassifier::isChargeback).map(this::toSettledChargebackModel).forEach(items::add);
    adjustmentRepository.findAll().stream()
      .filter(debitChargebackClassifier::isChargeback)
      .map(this::toAdjustmentChargebackModel)
      .forEach(items::add);
    items.sort(Comparator.comparing(ChargebackAnalysisModel::disputeDate, Comparator.nullsLast(Comparator.reverseOrder())));
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
      .map(this::analyzeFee)
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

  private AcquirerSaleAnalysisModel toAcquirerSaleModel(TransactionAcqEntity entity) {
    return new AcquirerSaleAnalysisModel(
      entity.getId(), entity.getSaleDate(), companyName(entity.getCompany()), establishmentName(entity.getEstablishment()),
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), modalityName(entity.getModality()), entity.getNsu(),
      entity.getAuthorization(), entity.getTid(), entity.getRvNumber(), entity.getGrossValue(), entity.getDiscountValue(),
      entity.getLiquidValue(), entity.getInstallment(), entity.getInstallment(), code(entity.getTransactionStatus()),
      reconciliationStatus(entity), paymentStatus(entity.getStatusPaymentBank()), fileName(entity.getProcessedFile())
    );
  }

  private ConciliationFeeAnalysisModel toFeeModel(TransactionAcqEntity entity) {
    return analyzeFee(entity).toModel();
  }

  private FeeAnalysisResult analyzeFee(TransactionAcqEntity entity) {
    BigDecimal gross = nz(entity.getGrossValue());
    BigDecimal appliedFee = appliedFeeValue(entity);
    BigDecimal appliedRate = entity.getMdrRate() != null ? entity.getMdrRate() : calculateRate(gross, appliedFee);

    Optional<ContractedAcquirerRate> contractedRate = contractedAcquirerRateLookupService.findRate(entity);
    BigDecimal expectedRate = contractedRate.map(ContractedAcquirerRate::rate).orElse(null);
    BigDecimal expectedFee = expectedRate != null ? calculateFee(gross, expectedRate) : null;
    BigDecimal feeDifference = expectedFee != null ? appliedFee.subtract(expectedFee) : null;
    String status = feeStatus(expectedRate, appliedRate, feeDifference);

    return new FeeAnalysisResult(
      entity.getId(), entity.getSaleDate(), companyName(entity.getCompany()), establishmentName(entity.getEstablishment()),
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), modalityName(entity.getModality()), entity.getNsu(),
      entity.getAuthorization(), entity.getGrossValue(), expectedRate, appliedRate, expectedFee, appliedFee, feeDifference, status
    );
  }

  private ErpVsAcquirerAnalysisModel toErpVsAcquirerModel(TransactionErpEntity erp) {
    Optional<TransactionAcqEntity> acq = findAcquirerMatch(erp);
    TransactionAcqEntity matched = acq.orElse(null);
    BigDecimal erpGross = nz(erp.getGrossValue());
    BigDecimal acqGross = matched != null ? nz(matched.getGrossValue()) : ZERO;
    return new ErpVsAcquirerAnalysisModel(
      erp.getId(), erp.getSaleDate(), matched != null ? matched.getSaleDate() : null,
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
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), debitChargebackClassifier.type(entity),
      code(entity.getReasonCode()), entity.getReasonDescription(), firstNonNull(entity.getPendingValue(), entity.getValueDebitOrder()),
      entity.getCompensatedValue(), debitChargebackClassifier.status(entity), fileName(entity.getProcessedFile())
    );
  }

  private DebitAnalysisModel toSettledDebitModel(SettledDebtEntity entity) {
    return new DebitAnalysisModel(
      entity.getId(), entity.getDateDebitOrder(), entity.getLiquidatedDate(), null, null,
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), debitChargebackClassifier.type(entity), code(entity.getReasonCode()),
      entity.getReasonDescription(), entity.getValueDebitOrder(), entity.getLiquidatedValue(), debitChargebackClassifier.status(entity),
      fileName(entity.getProcessedFile())
    );
  }

  private ChargebackAnalysisModel toOpenChargebackModel(PendingDebtEntity entity) {
    return new ChargebackAnalysisModel(
      entity.getId(), entity.getDateOriginalTransaction(), entity.getDateDebitOrder(), null, companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), entity.getNsu(),
      entity.getAuthorization(), entity.getTid(), entity.getOriginalTransactionValue(), firstNonNull(entity.getPendingValue(), entity.getValueDebitOrder()),
      code(entity.getReasonCode()), entity.getReasonDescription(), debitChargebackClassifier.chargebackStatus(entity)
    );
  }

  private ChargebackAnalysisModel toSettledChargebackModel(SettledDebtEntity entity) {
    return new ChargebackAnalysisModel(
      entity.getId(), entity.getDateOriginalTransaction(), entity.getDateDebitOrder(), entity.getLiquidatedDate(), null, null,
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), entity.getNsu(), entity.getAuthorization(), entity.getTid(),
      entity.getOriginalTransactionValue(), entity.getLiquidatedValue(), code(entity.getReasonCode()), entity.getReasonDescription(), debitChargebackClassifier.chargebackStatus(entity)
    );
  }

  private DebitAnalysisModel toAdjustmentDebitModel(AdjustmentEntity entity) {
    return new DebitAnalysisModel(
      entity.getId(), debitChargebackClassifier.debitDate(entity), debitChargebackClassifier.settlementDate(entity),
      companyName(entity.getCompany()), establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()),
      flagName(firstNonNull(entity.getRvFlagAdjustment(), entity.getRvFlagOrigin())), debitChargebackClassifier.type(entity),
      firstNonBlank(code(entity.getAdjustmentReason()), code(entity.getAdjustmentReason2()), entity.getRawAdjustmentCode()),
      firstNonBlank(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), entity.getSourceRecordIdentifier()),
      debitChargebackClassifier.debitValue(entity), debitChargebackClassifier.settledValue(entity),
      debitChargebackClassifier.status(entity), fileName(entity.getProcessedFile())
    );
  }

  private ChargebackAnalysisModel toAdjustmentChargebackModel(AdjustmentEntity entity) {
    return new ChargebackAnalysisModel(
      entity.getId(), entity.getTransactionDate(), debitChargebackClassifier.debitDate(entity), debitChargebackClassifier.settlementDate(entity),
      companyName(entity.getCompany()), establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()),
      flagName(firstNonNull(entity.getRvFlagAdjustment(), entity.getRvFlagOrigin())), entity.getNsu(), entity.getAuthorization(), entity.getTid(),
      firstNonNull(entity.getTransactionValue(), entity.getOriginalGrossSalesSummaryValue(), entity.getGrossValue()),
      debitChargebackClassifier.debitValue(entity), firstNonBlank(code(entity.getAdjustmentReason()), code(entity.getAdjustmentReason2()), entity.getRawAdjustmentCode()),
      firstNonBlank(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), entity.getSourceRecordIdentifier()),
      debitChargebackClassifier.chargebackStatus(entity)
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
      entity.getId(), "CHARGEBACK_OPEN", "CRITICAL", debitChargebackClassifier.chargebackStatus(entity), "CHARGEBACK", entity.getDateDebitOrder(), companyName(entity.getCompany()),
      establishmentName(entity.getEstablishment()), acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), null,
      firstNonBlank(code(entity.getNumberProcessChargeback()), code(entity.getNumberDebitOrder()), code(entity.getNsu()), entity.getAuthorization(), entity.getTid()),
      entity.getOriginalTransactionValue(), amount, abs(amount), firstNonBlank(entity.getReasonDescription(), "Chargeback/contestação em aberto"),
      actionFor("CHARGEBACK_OPEN"), fileName(entity.getProcessedFile())
    );
  }

  private DivergenceAnalysisModel toAdjustmentDivergence(AdjustmentEntity entity) {
    boolean chargeback = debitChargebackClassifier.isChargeback(entity);
    String type = chargeback ? "CHARGEBACK_ADJUSTMENT" : debitChargebackClassifier.type(entity);
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

  private List<ConciliationChartPointModel> divergencesByType(BigDecimal erpGross, BigDecimal acquirerGross, List<PendingDebtEntity> pendingDebts, List<AdjustmentEntity> adjustments, List<CreditOrderEntity> creditOrders, List<ReleasesBankEntity> bankReleases, List<FeeAnalysisResult> fees) {
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

  private boolean applyAcquirerBusinessContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    boolean changed = false;

    if (acq.getCompany() != null && !sameId(erp.getCompany(), acq.getCompany())) {
      erp.setCompany(acq.getCompany());
      changed = true;
    }

    if (acq.getEstablishment() != null && !sameId(erp.getEstablishment(), acq.getEstablishment())) {
      erp.setEstablishment(acq.getEstablishment());
      changed = true;
    }

    if (acq.getAcquirer() != null && !sameId(erp.getAcquirer(), acq.getAcquirer())) {
      erp.setAcquirer(acq.getAcquirer());
      changed = true;
    }

    /*if (acq.getFlag() != null && !sameId(erp.getFlag(), acq.getFlag())) {
      erp.setFlag(acq.getFlag());
      changed = true;
    }*/

    if (erp.getSaleReconciliationDate() == null) {
      erp.setSaleReconciliationDate(OffsetDateTime.now());
      changed = true;
    }

    if (acq.getSaleReconciliationDate() == null) {
      acq.setSaleReconciliationDate(erp.getSaleReconciliationDate());
    }

    if (erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_COMPANY
      || erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_ESTABLISHMENT
      || erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT
      || erp.getCommercialStatus() == null) {
      erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
      erp.setCommercialStatusMessage(null);
      changed = true;
    }

    return changed;
  }

  private Optional<TransactionAcqEntity> findBestAcquirerMatch(TransactionErpEntity erp, List<TransactionAcqEntity> acquirerSales) {
    return acquirerSales.stream()
      .filter(acq -> sameSaleKey(erp, acq))
      .max(Comparator.comparingInt(acq -> matchScore(erp, acq)));
  }

  private int matchScore(TransactionErpEntity erp, TransactionAcqEntity acq) {
    int score = 0;
    if (erp.getNsu() != null && Objects.equals(erp.getNsu(), acq.getNsu())) score += 40;
    if (sameText(erp.getAuthorization(), acq.getAuthorization())) score += 40;
    if (sameText(erp.getTid(), acq.getTid())) score += 30;
    if (erp.getGrossValue() != null && acq.getGrossValue() != null && erp.getGrossValue().compareTo(acq.getGrossValue()) == 0) score += 20;
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


  private Pageable remapErpVsAcquirerPageable(Pageable pageable) {
    if (pageable == null || pageable.getSort().isUnsorted()) {
      return pageable;
    }

    Sort mappedSort = Sort.by(pageable.getSort().stream()
      .map(order -> new Sort.Order(order.getDirection(), erpVsAcquirerSortProperty(order.getProperty()), order.getNullHandling()))
      .map(order -> order.isIgnoreCase() ? order.ignoreCase() : order)
      .toList());

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
  }

  private String erpVsAcquirerSortProperty(String property) {
    if (property == null || property.isBlank()) {
      return "saleDate";
    }

    return switch (property) {
      case "saleDateErp" -> "saleDate";
      case "grossValueErp" -> "grossValue";
      case "nsuErp" -> "nsu";
      case "authorizationErp" -> "authorization";
      case "flagErp" -> "flag.name";
      case "modalityErp" -> "modality";
      case "company" -> "company.name";
      case "establishment" -> "establishment.name";
      default -> property;
    };
  }
  private Optional<TransactionAcqEntity> findAcquirerMatch(TransactionErpEntity erp) {
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
    return entity.getSaleReconciliationDate() != null || entity.getStatusPaymentBank() != null || entity.getTransactionStatus() != null;
  }

  private long countDivergences(BigDecimal erpGross, BigDecimal acquirerGross, List<AdjustmentEntity> adjustments, List<PendingDebtEntity> pendingDebts, List<FeeAnalysisResult> fees) {
    long total = 0;
    if (erpGross.compareTo(acquirerGross) != 0) total++;
    total += adjustments.size();
    total += pendingDebts.size();
    total += fees.stream().filter(fee -> !"OK".equals(fee.status())).count();
    return total;
  }

  private BigDecimal calculateRate(BigDecimal gross, BigDecimal fee) {
    if (gross == null || gross.compareTo(ZERO) == 0 || fee == null) return null;
    return fee.multiply(HUNDRED).divide(gross, 4, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateFee(BigDecimal gross, BigDecimal rate) {
    if (gross == null || rate == null) return null;
    return gross.multiply(rate).divide(HUNDRED, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal appliedFeeValue(TransactionAcqEntity entity) {
    if (entity.getDiscountValue() != null) return entity.getDiscountValue();
    if (entity.getGrossValue() != null && entity.getLiquidValue() != null) {
      return entity.getGrossValue().subtract(entity.getLiquidValue());
    }
    return ZERO;
  }

  private String feeStatus(BigDecimal expectedRate, BigDecimal appliedRate, BigDecimal feeDifference) {
    if (expectedRate == null) return "MISSING_CONTRACT";

    BigDecimal absFeeDifference = abs(feeDifference);
    if (absFeeDifference.compareTo(VALUE_TOLERANCE) <= 0) return "OK";

    BigDecimal absRateDifference = expectedRate.subtract(nz(appliedRate)).abs();
    if (absRateDifference.compareTo(RATE_TOLERANCE) > 0) return "RATE_DIVERGENCE";

    return "VALUE_DIVERGENCE";
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

  private String reconciliationStatus(TransactionAcqEntity entity) {
    return entity.getSaleReconciliationDate() != null ? "RECONCILED" : code(entity.getTransactionStatus());
  }

  private String paymentStatus(Integer status) {
    return code(status);
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

  private boolean containsIgnoreCase(String value, String needle) {
    return value != null && needle != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
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

  private record FeeAnalysisResult(
    UUID id,
    OffsetDateTime saleDate,
    String company,
    String establishment,
    String acquirer,
    String flag,
    String modality,
    Long nsu,
    String authorization,
    BigDecimal grossValue,
    BigDecimal expectedRate,
    BigDecimal appliedRate,
    BigDecimal expectedFeeValue,
    BigDecimal appliedFeeValue,
    BigDecimal feeDifference,
    String status
  ) {
    private ConciliationFeeAnalysisModel toModel() {
      return new ConciliationFeeAnalysisModel(
        id, saleDate, company, establishment, acquirer, flag, modality, nsu, authorization,
        grossValue, expectedRate, appliedRate, expectedFeeValue, appliedFeeValue, feeDifference, status
      );
    }
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
