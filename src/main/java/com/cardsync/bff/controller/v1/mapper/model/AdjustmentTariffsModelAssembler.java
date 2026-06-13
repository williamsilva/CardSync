package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AdjustmentTariffsController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTariffsModel;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentTariffsModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull AdjustmentEntity,
  @NonNull AdjustmentTariffsModel
  > {

  public AdjustmentTariffsModelAssembler() {
    super(AdjustmentTariffsController.class, AdjustmentTariffsModel.class);
  }

  @Override
  public @NonNull AdjustmentTariffsModel toModel(@NonNull AdjustmentEntity entity) {
    AdjustmentTariffsModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setNsu(entity.getNsu());
    model.setCardNumber(entity.getCardNumber());
    model.setAuthorization(entity.getAuthorization());
    model.setAdjustmentReason(entity.getAdjustmentReason().getCode());
    model.setAdjustmentStatus(entity.getAdjustmentStatus().getCode());
    model.setRvNumberAdjustment(entity.getRvNumberAdjustment());
    model.setAdjustmentDescription(entity.getAdjustmentDescription());

    model.setCreditDate(entity.getCreditDate());
    model.setReleaseDate(entity.getReleaseDate());
    model.setAdjustmentDate(entity.getAdjustmentDate());

    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setDiscountValue(entity.getDiscountValue());
    model.setAdjustmentValue(entity.getAdjustmentValue());

    model.setCompany(toCompany(entity.getCompany()));
    model.setFlag(toFlag(entity.getRvFlagAdjustment()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));

    return model;
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

  private static CompanyMinimalModel toCompany(CompanyEntity company) {
    if (company == null) return null;
    return CompanyMinimalModel.builder()
      .id(company.getId())
      .cnpj(company.getCnpj())
      .fantasyName(company.getFantasyName())
      .socialReason(company.getSocialReason())
      .build();
  }

  private static AcquirerMinimalModel toAcquirer(AcquirerEntity acquirer) {
    if (acquirer == null) return null;
    return AcquirerMinimalModel.builder()
      .id(acquirer.getId())
      .cnpj(acquirer.getCnpj())
      .fantasyName(acquirer.getFantasyName())
      .socialReason(acquirer.getSocialReason())
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