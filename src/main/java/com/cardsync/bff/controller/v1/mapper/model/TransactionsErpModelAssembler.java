package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.TransactionErpSalesController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpModel;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.PaymentStatusEnum;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class TransactionsErpModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull TransactionErpEntity,
  @NonNull TransactionsErpModel
  > {

  public TransactionsErpModelAssembler() {
    super(TransactionErpSalesController.class, TransactionsErpModel.class);
  }

  @Override
  public @NonNull TransactionsErpModel toModel(@NonNull TransactionErpEntity entity) {
    TransactionsErpModel model = createModelWithId(entity.getId(), entity);

    List<InstallmentErpEntity> installments = getInstallments(entity);
    InstallmentErpEntity firstInstallment = installments.isEmpty() ? null : installments.getFirst();

    model.setId(entity.getId());
    model.setTid(entity.getTid());
    model.setCvNsu(entity.getNsu());
    model.setCapture(entity.getCapture());
    model.setCardName(entity.getCardName());
    model.setSaleDate(entity.getSaleDate());
    model.setModality(entity.getModality());
    model.setCardNumber(entity.getCardNumber());
    model.setGrossValue(entity.getGrossValue());
    model.setLineNumber(entity.getLineNumber());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setContractedFee(entity.getContractedFee());
    model.setDiscountValue(entity.getDiscountValue());
    model.setAuthorization(entity.getAuthorization());
    model.setAdjustmentValue(getAdjustmentValue(entity));
    model.setTransactionStatus(entity.getTransactionStatus());
    model.setSaleReconciliationDate(entity.getSaleReconciliationDate());
    model.setTransactionStatusReason(entity.getTransactionStatusReason());
    model.setExpectedPaymentDate(firstInstallment == null ? null : firstInstallment.getExpectedPaymentDate());
    model.setInstallments(installments.stream()
      .map(TransactionsErpModelAssembler::toInstallmentModel)
      .toList());

    if (entity.getAcquirer() != null) {
      model.setAcquirer(AcquirerMinimalModel.builder()
        .id(entity.getAcquirer().getId())
        .cnpj(entity.getAcquirer().getCnpj())
        .fantasyName(entity.getAcquirer().getFantasyName())
        .socialReason(entity.getAcquirer().getSocialReason())
        .status(entity.getAcquirer().getStatus() == null ? null : entity.getAcquirer().getStatus().name())
        .build());
    }

    if (entity.getFlag() != null) {
      model.setFlag(FlagMinimalModel.builder()
        .id(entity.getFlag().getId())
        .name(entity.getFlag().getName())
        .erpCode(entity.getFlag().getErpCode())
        .status(entity.getFlag().getStatus() == null ? null : entity.getFlag().getStatus().name())
        .build());
    }

    if (entity.getCompany() != null) {
      model.setCompany(CompanyMinimalModel.builder()
        .id(entity.getCompany().getId())
        .cnpj(entity.getCompany().getCnpj())
        .fantasyName(entity.getCompany().getFantasyName())
        .socialReason(entity.getCompany().getSocialReason())
        .type(entity.getCompany().getType() == null ? null : entity.getCompany().getType().name())
        .status(entity.getCompany().getStatus() == null ? null : entity.getCompany().getStatus().name())
        .build());
    }

    if (entity.getEstablishment() != null) {
      model.setEstablishment(EstablishmentMinimalModel.builder()
        .id(entity.getEstablishment().getId())
        .pvNumber(entity.getEstablishment().getPvNumber())
        .type(entity.getEstablishment().getType() == null ? null : entity.getEstablishment().getType().name())
        .status(entity.getEstablishment().getStatus() == null ? null : entity.getEstablishment().getStatus().name())
        .build());
    }

    if (entity.getBankingDomicile() != null) {
      model.setBankingDomicile(BankingDomicileMinimalModel.builder()
          .id(entity.getBankingDomicile().getId())
          .agency(entity.getBankingDomicile().getAgency())
          .currentAccount(entity.getBankingDomicile().getCurrentAccount())
          .bank(entity.getBankingDomicile().getBank() == null ? null: toBank(entity.getBankingDomicile().getBank()))
        .build());
    }

    if (entity.getProcessedFile() != null) {
      model.setProcessedFile(ProcessedFileMinimalModel.builder()
        .id(entity.getProcessedFile().getId())
        .file(entity.getProcessedFile().getFile())
        .build());
    }

    return model;
  }

  private static BankMinimalModel toBank(BankEntity bank) {
    return BankMinimalModel.builder()
      .id(bank.getId())
      .name(bank.getName())
      .code(bank.getCode())
      .build();
  }

  private static InstallmentErpModel toInstallmentModel(InstallmentErpEntity entity) {
    PaymentStatusEnum paymentStatus = statusPayment(entity.getPaymentStatus());
    StatusInstallmentEnum installmentStatus = statusInstallment(entity.getInstallmentStatus());

    InstallmentErpModel model = new InstallmentErpModel();
    model.setId(entity.getId());
    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setPaymentStatus(paymentStatus.getCode());
    model.setDiscountValue(entity.getDiscountValue());
    model.setCancellationDate(entity.getCancellationDate());
    model.setInstallmentStatus(installmentStatus.getCode());
    model.setExpectedPaymentDate(entity.getExpectedPaymentDate());
    model.setReconciliationBankLine(entity.getReconciliationBankLine());
    model.setReconciliationPaymentLine(entity.getReconciliationPaymentLine());
    model.setReconciliationBankProcessedAt(entity.getReconciliationBankProcessedAt());
    model.setReconciliationPaymentProcessedAt(entity.getReconciliationPaymentProcessedAt());
    return model;
  }

  private static BigDecimal getAdjustmentValue(TransactionErpEntity entity) {
    return entity.getAdjustment() == null || entity.getAdjustment().getAdjustmentValue() == null
      ? BigDecimal.ZERO
      : entity.getAdjustment().getAdjustmentValue();
  }

  private static List<InstallmentErpEntity> getInstallments(TransactionErpEntity entity) {
    return entity.getInstallments() == null
      ? List.of()
      : entity.getInstallments().stream()
      .sorted(Comparator.comparing(
        InstallmentErpEntity::getInstallment,
        Comparator.nullsLast(Integer::compareTo)
      ))
      .toList();
  }

  private static PaymentStatusEnum statusPayment(Integer code) {
    return Arrays.stream(PaymentStatusEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(PaymentStatusEnum.NULL);
  }

  private static StatusInstallmentEnum statusInstallment(Integer code) {
    return Arrays.stream(StatusInstallmentEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusInstallmentEnum.NULL);
  }

}
