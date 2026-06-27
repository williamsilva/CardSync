package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.input.SalesSummaryManualInput;
import com.cardsync.bff.controller.v1.representation.input.SalesSummaryManualInput.TransactionInput;
import com.cardsync.core.file.service.FileLookupService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSummaryManualService {

  private static final int STATUS_AUDIT_PENDING = 1;

  private final FileLookupService fileLookupService;
  private final AcquirerRepository acquirerRepository;
  private final CompanyRepository companyRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final CreditOrderOrphanLinkingService creditOrderOrphanLinkingService;

  @Transactional
  public SalesSummaryEntity create(SalesSummaryManualInput input) {
    AcquirerEntity acquirer = acquirerRepository.findById(UUID.fromString(input.acquirerId()))
      .orElseThrow(() -> new IllegalStateException("Adquirente não encontrada: " + input.acquirerId()));
    EstablishmentEntity establishment = safeEstablishment(input.pvNumber());
    CompanyEntity company = resolveCompany(input, establishment);

    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setPvNumber(input.pvNumber());
    summary.setRvNumber(input.rvNumber());
    summary.setRvDate(input.rvDate());
    summary.setGrossValue(input.grossValue());
    summary.setDiscountValue(orZero(input.discountValue()));
    summary.setLiquidValue(orZero(input.liquidValue()));
    summary.setTipValue(orZero(input.tipValue()));
    summary.setRejectedValue(orZero(input.rejectedValue()));
    summary.setAdjustedValue(orZero(input.adjustedValue()));
    summary.setNumberCvNsu(input.numberCvNsu());
    summary.setFirstInstallmentCreditDate(input.firstInstallmentCreditDate());
    summary.setSummaryType(input.summaryType());
    summary.setAcquirer(acquirer);
    summary.setCompany(company);
    summary.setManualGenerated(true);
    summary.setCreditOrderStatus(StatusReconciliationEnum.PENDING);
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    summary.setTransactionsStatus(StatusTransactionEnum.PENDING);

    SalesSummaryEntity saved = salesSummaryRepository.save(summary);

    List<TransactionInput> txInputs = input.transactions();
    List<TransactionAcqEntity> txList = new ArrayList<>();

    if (txInputs == null || txInputs.isEmpty()) {
      txList.add(buildTransaction(null, input, saved, acquirer, establishment, company));
    } else {
      for (TransactionInput t : txInputs) {
        txList.add(buildTransaction(t, input, saved, acquirer, establishment, company));
      }
    }

    transactionAcqRepository.saveAll(txList);

    List<InstallmentAcqEntity> installments = new ArrayList<>();
    for (TransactionAcqEntity tx : txList) {
      installments.addAll(buildInstallments(tx, saved));
    }
    if (!installments.isEmpty()) {
      installmentAcqRepository.saveAll(installments);
    }

    int linked = creditOrderOrphanLinkingService.linkOrphanedCreditOrdersForSummary(saved);

    log.info(
      "✅ Resumo de vendas manual criado: id={}, pv={}, rv={}, rvDate={}, grossValue={}, txCount={}, installmentCount={}, creditOrdersVinculadas={}",
      saved.getId(), saved.getPvNumber(), saved.getRvNumber(), saved.getRvDate(), saved.getGrossValue(),
      txList.size(), installments.size(), linked
    );

    return saved;
  }

  private TransactionAcqEntity buildTransaction(
    TransactionInput t,
    SalesSummaryManualInput input,
    SalesSummaryEntity summary,
    AcquirerEntity acquirer,
    EstablishmentEntity establishment,
    CompanyEntity company
  ) {

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setSalesSummary(summary);
    tx.setRvNumber(input.rvNumber());
    tx.setAcquirer(acquirer);
    tx.setCompany(company);
    tx.setEstablishment(establishment);

    tx.setNsu(t != null ? t.nsu() : null);
    tx.setCardNumber(t != null ? t.cardNumber() : null);
    tx.setAuthorization(t != null ? t.authorization() : null);
    tx.setReferenceNumber(t != null ? t.referenceNumber() : null);

    BigDecimal txGrossValue = (t != null && t.grossValue() != null) ? t.grossValue() : input.grossValue();
    tx.setGrossValue(txGrossValue);
    tx.setDiscountValue(orZero(t != null ? t.discountValue() : null));
    tx.setLiquidValue(orZero(t != null ? t.liquidValue() : null));
    tx.setTipValue(orZero(t != null ? t.tipValue() : null));

    if (t != null && t.saleDate() != null) {
      tx.setSaleDate(t.saleDate().atOffset(ZoneOffset.UTC));
    }
    tx.setCreditDate(t != null ? t.creditDate() : null);

    int installment = (t != null && t.installment() != null) ? t.installment() : 1;
    tx.setInstallment(installment);

    Integer modality = (t != null && t.modality() != null) ? t.modality() : ModalityEnum.CASH_CREDIT.getCode();
    tx.setModality(modality);

    tx.setFlag(safeFlag(t != null ? t.flagName() : null));
    tx.setTid(t != null ? t.tid() : null);
    tx.setCapture(t != null ? t.capture() : null);

    tx.setStatusAudit(STATUS_AUDIT_PENDING);
    tx.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING);
    tx.setStatusTransactionReason(StatusTransactionReasonEnum.NULL);

    return tx;
  }

  private List<InstallmentAcqEntity> buildInstallments(TransactionAcqEntity tx, SalesSummaryEntity summary) {
    int total = (tx.getInstallment() != null && tx.getInstallment() > 0) ? tx.getInstallment() : 1;
    BigDecimal gross = orZero(tx.getGrossValue());
    BigDecimal discount = orZero(tx.getDiscountValue());
    BigDecimal liquid = orZero(tx.getLiquidValue());

    BigDecimal grossPer = total > 1 ? gross.divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN) : gross;
    BigDecimal discountPer = total > 1 ? discount.divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN) : discount;
    BigDecimal liquidPer = total > 1 ? liquid.divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN) : liquid;

    LocalDate baseDate = tx.getCreditDate();
    if (baseDate == null) baseDate = summary.getFirstInstallmentCreditDate();

    List<InstallmentAcqEntity> result = new ArrayList<>(total);
    for (int i = 1; i <= total; i++) {
      InstallmentAcqEntity inst = new InstallmentAcqEntity();
      inst.setTransaction(tx);
      inst.setInstallment(i);
      inst.setGrossValue(grossPer);
      inst.setDiscountValue(discountPer);
      inst.setLiquidValue(liquidPer);
      inst.setAdjustmentValue(BigDecimal.ZERO);
      inst.setStatusPaymentBank(StatusPaymentBankEnum.PENDING.getCode());
      inst.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
      inst.setExpectedPaymentDate(baseDate != null ? baseDate.plusMonths(i - 1) : null);
      result.add(inst);
    }
    return result;
  }

  private FlagEntity safeFlag(String flagName) {
    if (flagName == null || flagName.isBlank()) return null;
    try {
      return fileLookupService.flagByName(flagName);
    } catch (IllegalStateException e) {
      log.warn("⚠️ Bandeira não encontrada para '{}': transação criada sem vínculo de bandeira.", flagName);
      return null;
    }
  }

  private CompanyEntity resolveCompany(SalesSummaryManualInput input, EstablishmentEntity establishment) {
    if (input.companyId() != null && !input.companyId().isBlank()) {
      return companyRepository.findById(UUID.fromString(input.companyId()))
        .orElseGet(() -> establishment != null ? establishment.getCompany() : null);
    }
    return establishment != null ? establishment.getCompany() : null;
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    try {
      return fileLookupService.establishmentByPvNumber(pvNumber);
    } catch (IllegalStateException e) {
      log.warn("⚠️ Estabelecimento não encontrado para PV {}: resumo criado sem vínculo de empresa.", pvNumber);
      return null;
    }
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
