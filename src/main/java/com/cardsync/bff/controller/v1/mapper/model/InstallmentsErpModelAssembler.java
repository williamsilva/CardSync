package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.TransactionErpSalesController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpMinimalModel;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InstallmentsErpModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull InstallmentErpEntity,
  @NonNull InstallmentErpModel
  > {

  public InstallmentsErpModelAssembler() {
    super(TransactionErpSalesController.class, InstallmentErpModel.class);
  }

  @Override
  public @NonNull InstallmentErpModel toModel(@NonNull InstallmentErpEntity entity) {
    InstallmentErpModel model = createModelWithId(entity.getId(), entity);

    model.setGrossValue(entity.getGrossValue());
    model.setPaymentDate(entity.getPaymentDate());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setDiscountValue(entity.getDiscountValue());
    model.setExpectedPaymentDate(entity.getExpectedPaymentDate());
    model.setAdjustmentValue(getAdjustmentValue(entity.getTransaction()));

    if (entity.getTransaction() != null) {
      TransactionErpEntity transaction = entity.getTransaction();

      model.setTransaction(
        TransactionsErpMinimalModel.builder()
          .id(transaction.getId())
          .tid(transaction.getTid())
          .cvNsu(transaction.getNsu())
          .cardName(transaction.getCardName())
          .saleDate(transaction.getSaleDate())
          .lineNumber(transaction.getLineNumber())
          .cardNumber(transaction.getCardNumber())
          .installment(transaction.getInstallment())
          .authorization(transaction.getAuthorization())
          .statusTransaction(transaction.getStatusTransaction())
          .saleReconciliationDate(transaction.getSaleReconciliationDate())
          .statusTransactionReason(transaction.getStatusTransactionReason())
          .missingContractAtSale(transaction.getMissingContractAtSale())
          .capture(transaction.getCapture() == null ? null : transaction.getCapture())
          .modality(transaction.getModality() == null ? null : transaction.getModality())

          .flag(toFlag(transaction.getFlag()))
          .company(toCompany(transaction.getCompany()))
          .acquirer(toAcquirer(transaction.getAcquirer()))
          .processedFile(toFile(transaction.getProcessedFile()))
          .establishment(toEstablishment(transaction.getEstablishment()))
          .bankingDomicile(toDomicile(transaction.getBankingDomicile()))
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

  private static BankingDomicileMinimalModel toDomicile(BankingDomicileEntity domicile) {
    if (domicile == null) {
      return null;
    }

    return BankingDomicileMinimalModel.builder()
      .id(domicile.getId())
      .agency(domicile.getAgency())
      .statusDate(domicile.getStatusDate())
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
      .erpCode(flag.getErpCode())
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

  private static BigDecimal getAdjustmentValue(TransactionErpEntity entity) {
    if (entity == null || entity.getAdjustment() == null || entity.getAdjustment().getAdjustmentValue() == null) {
      return BigDecimal.ZERO;
    }

    return entity.getAdjustment().getAdjustmentValue();
  }
}