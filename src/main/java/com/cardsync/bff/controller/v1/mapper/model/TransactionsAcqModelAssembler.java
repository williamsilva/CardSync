package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.TransactionAcqSalesController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.*;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class TransactionsAcqModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull TransactionAcqEntity,
  @NonNull TransactionsAcqModel
  > {

  public TransactionsAcqModelAssembler() {
    super(TransactionAcqSalesController.class, TransactionsAcqModel.class);
  }

  @Override
  public @NonNull TransactionsAcqModel toModel(@NonNull TransactionAcqEntity entity) {
    TransactionsAcqModel model = createModelWithId(entity.getId(), entity);

    BigDecimal adjustmentValue = getAdjustmentValue(entity);
    List<InstallmentAcqEntity> installments = getInstallments(entity);
    InstallmentAcqEntity firstInstallment = installments.isEmpty() ? null : installments.getFirst();

    model.setId(entity.getId());
    model.setTid(entity.getTid());
    model.setCvNsu(entity.getNsu());
    model.setMdrRate(entity.getMdrRate());
    model.setCapture(entity.getCapture());
    model.setSaleDate(entity.getSaleDate());
    model.setFlexRate(entity.getFlexRate());
    model.setModality(entity.getModality());
    model.setAdjustmentValue(adjustmentValue);
    model.setLineNumber(entity.getLineNumber());
    model.setCardNumber(entity.getCardNumber());
    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setDiscountValue(entity.getDiscountValue());
    model.setAuthorization(entity.getAuthorization());
    model.setStatusTransaction(entity.getStatusTransaction().name());
    model.setSaleReconciliationDate(entity.getSaleReconciliationDate());
    model.setStatusTransactionReason(entity.getStatusTransactionReason());
    model.setExpectedPaymentDate(firstInstallment == null ? null : firstInstallment.getExpectedPaymentDate());

    model.setInstallments(installments.stream()
      .map(TransactionsAcqModelAssembler::toInstallmentModel)
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

    if(entity.getSalesSummary() != null) {
      model.setSalesSummary(SalesSummaryMinimalModel.builder()
        .id(entity.getSalesSummary().getId())
        .agency(entity.getSalesSummary().getAgency())
        .currentAccount(entity.getSalesSummary().getCurrentAccount())
        .pvNumber(entity.getSalesSummary().getPvNumber())
        .bankingDomicile(entity.getSalesSummary().getBankingDomicile() == null ?
          null : toBankingDomicile(entity.getSalesSummary().getBankingDomicile()))
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

  private static BankingDomicileMinimalModel toBankingDomicile(BankingDomicileEntity bankingDomicile) {
    return BankingDomicileMinimalModel.builder()
      .id(bankingDomicile.getId())
      .agency(bankingDomicile.getAgency())
      .currentAccount(bankingDomicile.getCurrentAccount())
      .bank(bankingDomicile.getBank() == null ? null : toBank(bankingDomicile.getBank()))
      .build();
  }

  private static BankMinimalModel toBank(BankEntity bank) {
    return BankMinimalModel.builder()
      .id(bank.getId())
      .name(bank.getName())
      .code(bank.getCode())
      .build();
  }

  private static TransactionAcqInstallmentModel toInstallmentModel(InstallmentAcqEntity entity) {
    StatusInstallmentEnum installmentStatus = statusInstallment(entity.getInstallmentStatus());

    TransactionAcqInstallmentModel model = new TransactionAcqInstallmentModel();
    model.setId(entity.getId());
    model.setGrossValue(entity.getGrossValue());
    model.setInstallment(entity.getInstallment());
    model.setLiquidValue(entity.getLiquidValue());
    model.setPaymentDate(entity.getPaymentDate());
    model.setDiscountValue(entity.getDiscountValue());
    model.setMdrRate(entity.getTransaction().getMdrRate());
    model.setCancellationDate(entity.getCancellationDate());
    model.setInstallmentStatus(installmentStatus.getCode());
    model.setStatusPaymentBank(entity.getInstallmentStatus());
    model.setExpectedPaymentDate(entity.getExpectedPaymentDate());
    model.setReconciliationBankLine(entity.getReconciliationBankLine());
    model.setReconciliationBankProcessedAt(entity.getReconciliationBankProcessedAt());
    return model;
  }

  private static BigDecimal getAdjustmentValue(TransactionAcqEntity entity) {
    return entity.getAdjustment() == null || entity.getAdjustment().getAdjustmentValue() == null
      ? BigDecimal.ZERO
      : entity.getAdjustment().getAdjustmentValue();
  }

  private static List<InstallmentAcqEntity> getInstallments(TransactionAcqEntity entity) {
    return entity.getInstallments() == null
      ? List.of()
      : entity.getInstallments().stream()
      .sorted(Comparator.comparing(
        InstallmentAcqEntity::getInstallment,
        Comparator.nullsLast(Integer::compareTo)
      ))
      .toList();
  }

  private static StatusPaymentBankEnum statusPayment(Integer code) {
    return Arrays.stream(StatusPaymentBankEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusPaymentBankEnum.NULL);
  }

  private static StatusInstallmentEnum statusInstallment(Integer code) {
    return Arrays.stream(StatusInstallmentEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusInstallmentEnum.NULL);
  }

}
