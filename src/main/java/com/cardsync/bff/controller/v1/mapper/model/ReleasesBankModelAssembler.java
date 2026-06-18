package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ReleasesBanksController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankModel;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class ReleasesBankModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull ReleasesBankEntity,
  @NonNull ReleasesBankModel
  > {

  public ReleasesBankModelAssembler() {
    super(ReleasesBanksController.class, ReleasesBankModel.class);
  }

  @Override
  public @NonNull ReleasesBankModel toModel(@NonNull ReleasesBankEntity entity) {
    ReleasesBankModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setLineNumber(entity.getLineNumber());
    model.setReleaseDate(entity.getReleaseDate());
    model.setReleaseCategory(entity.getReleaseCategory());
    model.setModalityPaymentBank(entity.getModalityPaymentBank());

    model.setFlag(toFlag(entity.getFlag()));
    model.setBank(toBank(entity.getBank()));
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));
    model.setProcessedFile(toProcessedFile(entity.getProcessedFile()));
    model.setBankingDomicile(toBankingDomicile(entity.getBankingDomicile()));

    return model;
  }

  private BankMinimalModel toBank(BankEntity entity) {
    if (entity == null) {
      return null;
    }

    return BankMinimalModel.builder()
      .id(entity.getId())
      .code(entity.getCode())
      .name(entity.getName())
      .status(entity.getStatus())
      .build();
  }

  private BankingDomicileMinimalModel toBankingDomicile(BankingDomicileEntity entity) {
    if (entity == null) {
      return null;
    }

    return BankingDomicileMinimalModel.builder()
      .id(entity.getId())
      .agency(entity.getAgency())
      .agencyDigit(entity.getAgencyDigit())
      .accountDigit(entity.getAccountDigit())
      .currentAccount(entity.getCurrentAccount())
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

  private ProcessedFileMinimalModel toProcessedFile(ProcessedFileEntity entity) {
    if (entity == null) {
      return null;
    }

    return ProcessedFileMinimalModel.builder()
      .id(entity.getId())
      .file(entity.getFile())
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
