package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ConciliationWaitingController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ContractAuditModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqToContractModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpToContractModel;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class ContractAuditModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull ContractAuditEntity,
  @NonNull ContractAuditModel
  > {

  public ContractAuditModelAssembler() {
    super(ConciliationWaitingController.class, ContractAuditModel.class);
  }

  @Override
  public @NonNull ContractAuditModel toModel(@NonNull ContractAuditEntity entity) {
    ContractAuditModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setCvNsu(entity.getNsu());
    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setRateAcquirer(entity.getRateAcquirer());
    model.setRateContract(entity.getRateContract());
    model.setDiscountValue(entity.getDiscountValue());
    model.setAuthorization(entity.getAuthorization());
    model.setDifferenceValue(entity.getDifferenceValue());

    model.setStatus(entity.getStatus().getCode());
    model.setCapture(entity.getCapture().getCode());
    model.setModality(entity.getModality().getCode());

    model.setFlag(toFlag(entity.getFlag()));
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));
    model.setTransactionAcq(toTransaction(entity.getTransactionAcq()));
    model.setTransactionErp(toTransaction(entity.getTransactionErp()));

    return model;
  }

  private TransactionsAcqToContractModel toTransaction(TransactionAcqEntity entity) {
    if (entity == null) {
      return null;
    }
    return TransactionsAcqToContractModel.builder()
      .id(entity.getId())
      .saleDate(entity.getSaleDate())
      .installment(entity.getInstallment())
      .build();
  }

  private TransactionsErpToContractModel toTransaction(TransactionErpEntity entity) {
    if (entity == null) {
      return null;
    }
    return TransactionsErpToContractModel.builder()
      .id(entity.getId())
      .grossValue(entity.getGrossValue())
      .liquidValue(entity.getLiquidValue())
      .build();
  }

  private AcquirerMinimalModel toAcquirer(AcquirerEntity entity) {
    if (entity == null) {
      return null;
    }
    return AcquirerMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }

  private FlagMinimalModel toFlag(FlagEntity entity) {
    if (entity == null) {
      return null;
    }
    return FlagMinimalModel.builder()
      .id(entity.getId())
      .name(entity.getName())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }

  private CompanyMinimalModel toCompany(CompanyEntity entity) {
    if (entity == null) {
      return null;
    }
    return CompanyMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .type(entity.getType() == null ? null : entity.getType().name())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }

  private EstablishmentMinimalModel toEstablishment(EstablishmentEntity entity) {
    if (entity == null) {
      return null;
    }
    return EstablishmentMinimalModel.builder()
      .id(entity.getId())
      .pvNumber(entity.getPvNumber())
      .type(entity.getType() == null ? null : entity.getType().name())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }
}
