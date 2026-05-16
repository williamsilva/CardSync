package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.TransactionAcqSalesController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.*;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InstallmentsAcqModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull InstallmentAcqEntity,
  @NonNull InstallmentAcqModel
  > {

  public InstallmentsAcqModelAssembler() {
    super(TransactionAcqSalesController.class, InstallmentAcqModel.class);
  }

  @Override
  public @NonNull InstallmentAcqModel toModel(@NonNull InstallmentAcqEntity entity) {
    InstallmentAcqModel model = createModelWithId(entity.getId(), entity);

    model.setGrossValue(entity.getGrossValue());
    model.setPaymentDate(entity.getPaymentDate());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setDiscountValue(entity.getDiscountValue());
    model.setExpectedPaymentDate(entity.getExpectedPaymentDate());
    model.setAdjustmentValue(getAdjustmentValue(entity.getTransaction()));

    if (entity.getTransaction() != null) {
      TransactionAcqEntity transaction = entity.getTransaction();

      model.setTransaction(
        TransactionsAcqMinimalModel.builder()
          .id(transaction.getId())
          .tid(transaction.getTid())
          .cvNsu(transaction.getNsu())
          .saleDate(transaction.getSaleDate())
          .lineNumber(transaction.getLineNumber())
          .cardNumber(transaction.getCardNumber())
          .installment(transaction.getInstallment())
          .authorization(transaction.getAuthorization())
          .statusTransaction(transaction.getStatusTransaction())
          .saleReconciliationDate(transaction.getSaleReconciliationDate())
          .statusTransactionReason(transaction.getStatusTransactionReason())
          .capture(transaction.getCapture() == null ? null : transaction.getCapture())
          .modality(transaction.getModality() == null ? null : transaction.getModality())

          .flag(toFlag(transaction.getFlag()))
          .company(toCompany(transaction.getCompany()))
          .acquirer(toAcquirer(transaction.getAcquirer()))
          .processedFile(toFile(transaction.getProcessedFile()))
          .salesSummary(toSummary(transaction.getSalesSummary()))
          .establishment(toEstablishment(transaction.getEstablishment()))
          .build()
      );
    }

    return model;
  }

  private static ProcessedFileMinimalModel toFile(ProcessedFileEntity entity) {
    if (entity == null) {
      return null;
    }

    return ProcessedFileMinimalModel.builder()
      .id(entity.getId())
      .file(entity.getFile())
      .build();
  }

  private static SalesSummaryMinimalModel toSummary(SalesSummaryEntity entity) {
    if (entity == null) {
      return null;
    }

    return SalesSummaryMinimalModel.builder()
      .id(entity.getId())
      .agency(entity.getAgency())
      .pvNumber(entity.getPvNumber())
      .currentAccount(entity.getCurrentAccount())
      .bankingDomicile(toDomicile(entity.getBankingDomicile()))
      .build();
  }

  private static BankingDomicileMinimalModel toDomicile(BankingDomicileEntity domicile) {
    if (domicile == null) {
      return null;
    }

    return BankingDomicileMinimalModel.builder()
      .id(domicile.getId())
      .agency(domicile.getAgency())
      .currentAccount(domicile.getCurrentAccount())
      .bank(toBank(domicile.getBank()))
      .build();
  }

  private static EstablishmentMinimalModel toEstablishment(EstablishmentEntity establishment) {
    if (establishment == null) {
      return null;
    }

    return EstablishmentMinimalModel.builder()
      .id(establishment.getId())
      .pvNumber(establishment.getPvNumber())
      .type(establishment.getType() == null ? null : establishment.getType().name())
      .status(establishment.getStatus() == null ? null : establishment.getStatus().name())
      .build();
  }

  private static CompanyMinimalModel toCompany(CompanyEntity company) {
    if (company == null) {
      return null;
    }

    return CompanyMinimalModel.builder()
      .id(company.getId())
      .cnpj(company.getCnpj())
      .fantasyName(company.getFantasyName())
      .socialReason(company.getSocialReason())
      .type(company.getType() == null ? null : company.getType().name())
      .status(company.getStatus() == null ? null : company.getStatus().name())
      .build();
  }

  private static FlagMinimalModel toFlag(FlagEntity flag) {
    if (flag == null) {
      return null;
    }

    return FlagMinimalModel.builder()
      .id(flag.getId())
      .name(flag.getName())
      .status(flag.getStatus() == null ? null : flag.getStatus().name())
      .build();
  }

  private static AcquirerMinimalModel toAcquirer(AcquirerEntity acquirer) {
    if (acquirer == null) {
      return null;
    }

    return AcquirerMinimalModel.builder()
      .id(acquirer.getId())
      .cnpj(acquirer.getCnpj())
      .fantasyName(acquirer.getFantasyName())
      .socialReason(acquirer.getSocialReason())
      .status(acquirer.getStatus() == null ? null : acquirer.getStatus().name())
      .build();
  }

  private static BankMinimalModel toBank(BankEntity bank) {
    if (bank == null) {
      return null;
    }

    return BankMinimalModel.builder()
      .id(bank.getId())
      .name(bank.getName())
      .code(bank.getCode())
      .build();
  }

  private static BigDecimal getAdjustmentValue(TransactionAcqEntity entity) {
    if (entity == null || entity.getAdjustment() == null || entity.getAdjustment().getAdjustmentValue() == null) {
      return BigDecimal.ZERO;
    }

    return entity.getAdjustment().getAdjustmentValue();
  }
}