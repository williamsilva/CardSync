package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ConciliationWaitingController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationWaitingModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationWaitingOtherDivergencePair;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationWaitingSideModel;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class ConciliationWaitingOtherDivergenceModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull ConciliationWaitingOtherDivergencePair,
  @NonNull ConciliationWaitingModel
  > {

  public ConciliationWaitingOtherDivergenceModelAssembler() {
    super(ConciliationWaitingController.class, ConciliationWaitingModel.class);
  }

  @Override
  public @NonNull ConciliationWaitingModel toModel(@NonNull ConciliationWaitingOtherDivergencePair pair) {
    TransactionErpEntity erp = pair.erp();
    TransactionAcqEntity acq = pair.acq();

    ConciliationWaitingModel model = createModelWithId(erp.getId(), pair);

    model.setId(erp.getId());

    model.setCvNsu(erp.getNsu());
    model.setCapture(erp.getCapture());
    model.setSaleDate(erp.getSaleDate());
    model.setModality(erp.getModality());
    model.setFlag(toFlag(erp.getFlag()));
    model.setGrossValue(erp.getGrossValue());
    model.setLiquidValue(erp.getLiquidValue());
    model.setInstallment(erp.getInstallment());
    model.setCompany(toCompany(erp.getCompany()));
    model.setAuthorization(erp.getAuthorization());
    model.setAcquirer(toAcquirer(erp.getAcquirer()));
    model.setEstablishment(toEstablishment(erp.getEstablishment()));
    model.setStatusTransactionReason(erp.getStatusTransactionReason());

    model.setErp(toErpSide(erp));
    model.setAcquirerTransaction(toAcqSide(acq));

    return model;
  }

  private ConciliationWaitingSideModel toErpSide(TransactionErpEntity erp) {
    if (erp == null) {
      return null;
    }

    ConciliationWaitingSideModel side = new ConciliationWaitingSideModel();
    side.setId(erp.getId());
    side.setCvNsu(erp.getNsu());
    side.setCapture(erp.getCapture());
    side.setSaleDate(erp.getSaleDate());
    side.setSaleReconciliationDate(erp.getSaleReconciliationDate());
    side.setModality(erp.getModality());
    side.setGrossValue(erp.getGrossValue());
    side.setLiquidValue(erp.getLiquidValue());
    side.setDiscountValue(erp.getDiscountValue());
    side.setInstallment(erp.getInstallment());
    side.setAuthorization(erp.getAuthorization());
    side.setStatusTransaction(erp.getStatusTransaction());
    side.setStatusTransactionReason(erp.getStatusTransactionReason());
    side.setAcquirer(toAcquirer(erp.getAcquirer()));
    side.setFlag(toFlag(erp.getFlag()));
    side.setCompany(toCompany(erp.getCompany()));
    side.setEstablishment(toEstablishment(erp.getEstablishment()));
    return side;
  }

  private ConciliationWaitingSideModel toAcqSide(TransactionAcqEntity acq) {
    if (acq == null) {
      return null;
    }

    ConciliationWaitingSideModel side = new ConciliationWaitingSideModel();
    side.setId(acq.getId());
    side.setCvNsu(acq.getNsu());
    side.setCapture(acq.getCapture());
    side.setSaleDate(acq.getSaleDate());
    side.setSaleReconciliationDate(acq.getSaleReconciliationDate());
    side.setModality(acq.getModality());
    side.setGrossValue(acq.getGrossValue());
    side.setLiquidValue(acq.getLiquidValue());
    side.setDiscountValue(acq.getDiscountValue());
    side.setInstallment(acq.getInstallment());
    side.setAuthorization(acq.getAuthorization());
    side.setStatusTransaction(acq.getStatusTransaction().getCode());
    side.setStatusTransactionReason(acq.getStatusTransactionReason());
    side.setAcquirer(toAcquirer(acq.getAcquirer()));
    side.setFlag(toFlag(acq.getFlag()));
    side.setCompany(toCompany(acq.getCompany()));
    side.setEstablishment(toEstablishment(acq.getEstablishment()));
    return side;
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
