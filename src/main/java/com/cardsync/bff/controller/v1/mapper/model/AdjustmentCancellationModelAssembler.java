package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AdjustmentCancellationController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentCancellationModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqMinimalModel;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentCancellationModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull AdjustmentEntity,
  @NonNull AdjustmentCancellationModel
  > {

  public AdjustmentCancellationModelAssembler() {
    super(AdjustmentCancellationController.class, AdjustmentCancellationModel.class);
  }

  @Override
  public @NonNull AdjustmentCancellationModel toModel(@NonNull AdjustmentEntity entity) {
    AdjustmentCancellationModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setCvNsu(entity.getNsu());
    model.setAuthorization(entity.getAuthorization());
    model.setRvNumberAdjustment(entity.getRvNumberAdjustment());

    model.setCreditDate(entity.getCreditDate());
    model.setAdjustmentDate(entity.getAdjustmentDate());
    model.setAdjustmentValue(entity.getAdjustmentValue());
    model.setAdjustmentReason(entity.getAdjustmentReason().getCode());
    model.setAdjustmentStatus(entity.getAdjustmentStatus().getCode());

    model.setCompany(toCompany(entity.getCompany()));
    model.setFlag(toFlag(entity.getRvFlagAdjustment()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setTransaction(toTransaction(entity.getTransaction()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));

    return model;
  }

  private TransactionsAcqMinimalModel toTransaction(TransactionAcqEntity entity) {
    if (entity == null) {
      return null;
    }
    return TransactionsAcqMinimalModel.builder()
      .id(entity.getId())
      .saleDate(entity.getSaleDate())
      .grossValue(entity.getGrossValue())
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