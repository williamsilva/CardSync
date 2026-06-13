package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AdjustmentTariffsController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentChargeBackRequestsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.SalesSummaryMinimalModel;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentChargeBackRequestsModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull RequestNoticeEntity,
  @NonNull AdjustmentChargeBackRequestsModel
  > {

  public AdjustmentChargeBackRequestsModelAssembler() {
    super(AdjustmentTariffsController.class, AdjustmentChargeBackRequestsModel.class);
  }

  @Override
  public @NonNull AdjustmentChargeBackRequestsModel toModel(@NonNull RequestNoticeEntity entity) {
    AdjustmentChargeBackRequestsModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setNsu(entity.getNsu());
    model.setDeadline(entity.getDeadline());
    model.setSaleDate(entity.getSaleDate());
    model.setRequestCode(entity.getRequestCode());
    model.setRequestStatus(entity.getRequestStatus());
    model.setAuthorization(entity.getAuthorization());
    model.setTransactionValue(entity.getTransactionValue());

    model.setFlag(toFlag(entity.getFlag()));
    model.setRvNumber(entity.getRvNumber());
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setSalesSummary(toSalesSummary(entity.getSalesSummary()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));

    return model;
  }

  private FlagMinimalModel toFlag(FlagEntity entity) {
    if (entity == null) return null;
    return FlagMinimalModel.builder()
      .id(entity.getId())
      .name(entity.getName())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }

  private static CompanyMinimalModel toCompany(CompanyEntity entity) {
    if (entity == null) return null;
    return CompanyMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .build();
  }

  private static AcquirerMinimalModel toAcquirer(AcquirerEntity entity) {
    if (entity == null) return null;
    return AcquirerMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .build();
  }

  private static SalesSummaryMinimalModel toSalesSummary(SalesSummaryEntity entity) {
    if (entity == null) return null;
    return SalesSummaryMinimalModel.builder()
      .id(entity.getId())
      .modality(entity.getModality())
      .build();
  }

  private static EstablishmentMinimalModel toEstablishment(EstablishmentEntity establishment) {
    if (establishment == null) return null;
    return EstablishmentMinimalModel.builder()
      .id(establishment.getId())
      .pvNumber(establishment.getPvNumber())
      .build();
  }
}