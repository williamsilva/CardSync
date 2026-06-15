package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AnticipationController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.*;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AnticipationModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull AnticipationEntity,
  @NonNull AnticipationModel
  > {

  public AnticipationModelAssembler() {
    super(AnticipationController.class, AnticipationModel.class);
  }

  @Override
  public @NonNull AnticipationModel toModel(@NonNull AnticipationEntity entity) {
    AnticipationModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setReleaseDate(entity.getReleaseDate());
    model.setOriginalDueDate(entity.getOriginalDueDate());
    model.setInstallmentNumber(entity.getInstallmentNumber());
    model.setNumberRvCorresponding(entity.getNumberRvCorresponding());

    model.setGrossValue(entity.getGrossValue());
    model.setReleaseValue(entity.getReleaseValue());
    model.setDiscountRateValue(entity.getDiscountRateValue());
    model.setOriginalCreditValue(entity.getOriginalCreditValue());

    model.setFlag(toFlag(entity.getFlag()));
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setSalesSummary(toSalesSummary(entity.getSalesSummary()));
    model.setProcessedFile(toProcessedFile(entity.getProcessedFile()));
    model.setEstablishment(toEstablishment(entity.getEstablishment()));
    model.setBankingDomicile(toBankingDomicile(entity.getBankingDomicile()));

    return model;
  }

  private static SalesSummaryMinimalModel toSalesSummary(SalesSummaryEntity  entity) {
    if (entity == null) {
      return null;
    }

    return SalesSummaryMinimalModel.builder()
      .id(entity.getId())
      .agency(entity.getAgency())
      .pvNumber(entity.getPvNumber())
      .lineNumber(entity.getLineNumber())
      .numberCvNsu(entity.getNumberCvNsu())
      .currentAccount(entity.getCurrentAccount())
      .statusPaymentBank(entity.getStatusPaymentBank().name())
      .transactionsStatus(entity.getTransactionsStatus().name())
      .bankingDomicile(toBankingDomicile(entity.getBankingDomicile()))
      .build();
  }

  private static BankingDomicileMinimalModel toBankingDomicile(BankingDomicileEntity entity) {
    if (entity == null) {
      return null;
    }

    return BankingDomicileMinimalModel.builder()
      .id(entity.getId())
      .agency(entity.getAgency())
      .statusDate(entity.getStatusDate())
      .currentAccount(entity.getCurrentAccount())
      .bank(entity.getBank() == null ? null : toBank(entity.getBank()))
      .build();
  }

  private static BankMinimalModel toBank(BankEntity entity) {
    if (entity == null) {
      return null;
    }

    return BankMinimalModel.builder()
      .id(entity.getId())
      .name(entity.getName())
      .code(entity.getCode())
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
}
