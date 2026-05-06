package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.TransactionErpSalesController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.erp.TransactionErpInstallmentModel;
import com.cardsync.bff.controller.v1.representation.model.erp.TransactionsErpModel;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
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

    BigDecimal adjustmentValue = getAdjustmentValue(entity);
    List<InstallmentErpEntity> installments = getInstallments(entity);
    InstallmentErpEntity firstInstallment = installments.isEmpty() ? null : installments.getFirst();
    StatusTransactionEnum conciliationStatus = resolveConciliationStatus(entity, installments);

    model.setId(entity.getId());
    model.setCvNsu(entity.getNsu());
    model.setCapture(entity.getCapture());
    model.setSaleDate(entity.getSaleDate());
    model.setModality(entity.getModality());
    model.setAdjustmentValue(adjustmentValue);
    model.setNetValue(entity.getLiquidValue());
    model.setGrossValue(entity.getGrossValue());
    model.setFeeValue(entity.getDiscountValue());
    model.setInstallment(entity.getInstallment());
    model.setAuthorization(entity.getAuthorization());
    model.setConciliationStatus(conciliationStatus.getCode());
    model.setExpectedPaymentDate(firstInstallment == null ? null : firstInstallment.getCreditDate());
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

    return model;
  }

  private static TransactionErpInstallmentModel toInstallmentModel(InstallmentErpEntity entity) {
    StatusPaymentEnum paymentStatus = statusPayment(entity.getPaymentStatus());
    StatusInstallmentEnum installmentStatus = statusInstallment(entity.getInstallmentStatus());

    TransactionErpInstallmentModel model = new TransactionErpInstallmentModel();
    model.setId(entity.getId());
    model.setNetValue(entity.getLiquidValue());
    model.setGrossValue(entity.getGrossValue());
    model.setFeeValue(entity.getDiscountValue());
    model.setInstallment(entity.getInstallment());
    model.setPaymentStatus(paymentStatus.getCode());
    model.setExpectedPaymentDate(entity.getCreditDate());
    model.setCancellationDate(entity.getCancellationDate());
    model.setInstallmentStatus(installmentStatus.getCode());
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

  private static StatusTransactionEnum resolveConciliationStatus(TransactionErpEntity entity, List<InstallmentErpEntity> installments) {
    StatusTransactionEnum explicitStatus = statusTransaction(entity.getTransactionStatus());
    if (explicitStatus != StatusTransactionEnum.NULL) {
      return explicitStatus;
    }

    if (entity.getCanceledDate() != null) {
      return StatusTransactionEnum.CANCELED;
    }

    if (entity.getDeletedDate() != null) {
      return StatusTransactionEnum.DELETED;
    }

    if (entity.getSaleReconciliationDate() != null) {
      return StatusTransactionEnum.AUTOMATICALLY_RECONCILED;
    }

    if (installments == null || installments.isEmpty()) {
      return StatusTransactionEnum.PENDING;
    }

    boolean anyDivergent = installments.stream()
      .anyMatch(item -> Objects.equals(item.getPaymentStatus(), StatusPaymentEnum.DIVERGENT.getCode())
        || Objects.equals(item.getInstallmentStatus(), StatusInstallmentEnum.DIVERGENT.getCode()));
    if (anyDivergent) {
      return StatusTransactionEnum.NOT_RECONCILED;
    }

    boolean allReconciled = installments.stream()
      .allMatch(item -> Objects.equals(item.getInstallmentStatus(), StatusInstallmentEnum.RECONCILED.getCode())
        || Objects.equals(item.getPaymentStatus(), StatusPaymentEnum.PAID.getCode()));
    if (allReconciled) {
      return StatusTransactionEnum.AUTOMATICALLY_RECONCILED;
    }

    return StatusTransactionEnum.PENDING;
  }

  private static StatusTransactionEnum statusTransaction(Integer code) {
    return Arrays.stream(StatusTransactionEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusTransactionEnum.NULL);
  }

  private static StatusPaymentEnum statusPayment(Integer code) {
    return Arrays.stream(StatusPaymentEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusPaymentEnum.NULL);
  }

  private static StatusInstallmentEnum statusInstallment(Integer code) {
    return Arrays.stream(StatusInstallmentEnum.values())
      .filter(item -> Objects.equals(item.getCode(), code))
      .findFirst()
      .orElse(StatusInstallmentEnum.NULL);
  }

}
