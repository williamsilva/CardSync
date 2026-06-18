package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ConciliationWaitingController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationWaitingModel;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class ConciliationWaitingErpModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull TransactionErpEntity,
  @NonNull ConciliationWaitingModel
  > {

  public ConciliationWaitingErpModelAssembler() {
    super(ConciliationWaitingController.class, ConciliationWaitingModel.class);
  }

  @Override
  public @NonNull ConciliationWaitingModel toModel(@NonNull TransactionErpEntity entity) {
    ConciliationWaitingModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setCvNsu(entity.getNsu());
    model.setCapture(entity.getCapture());
    model.setSaleDate(entity.getSaleDate());
    model.setModality(entity.getModality());;
    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setInstallment(entity.getInstallment());
    model.setAuthorization(entity.getAuthorization());
    model.setStatusTransactionReason(StatusTransactionReasonEnum.toCode(entity.getStatusTransactionReason()));

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

    return model;
  }
}