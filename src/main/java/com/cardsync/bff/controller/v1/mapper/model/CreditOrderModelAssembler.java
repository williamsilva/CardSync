package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.CreditOrderController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.CreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.SalesSummaryMinimalModel;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class CreditOrderModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull CreditOrderEntity,
  @NonNull CreditOrderModel
  > {

  public CreditOrderModelAssembler() {
    super(CreditOrderController.class, CreditOrderModel.class);
  }

  @Override
  public @NonNull CreditOrderModel toModel(@NonNull CreditOrderEntity entity) {
    CreditOrderModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setRvDate(entity.getRvDate());
    model.setRvNumber(entity.getRvNumber());
    model.setReleaseDate(entity.getReleaseDate());
    model.setReleaseValue(entity.getReleaseValue());
    model.setGrossRvValue(entity.getGrossRvValue());
    model.setCreditOrderDate(entity.getCreditOrderDate());
    model.setOriginalPvNumber(entity.getOriginalPvNumber());
    model.setInstallmentTotal(entity.getInstallmentTotal());
    model.setDiscountRateValue(entity.getDiscountRateValue());
    model.setInstallmentNumber(entity.getInstallmentNumber());
    model.setStatusPaymentBank(entity.getStatusPaymentBank().name());
    model.setSalesSummaryStatus(entity.getSalesSummaryStatus().name());

    model.setFlag(toFlag(entity.getFlag()));
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setSalesSummary(toSalesSummary(entity.getSalesSummary()));
    model.setProcessedFile(toProcessedFile(entity.getProcessedFile()));
    return model;
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

  private SalesSummaryMinimalModel toSalesSummary(SalesSummaryEntity entity) {
    if (entity == null) {
      return null;
    }
    return SalesSummaryMinimalModel.builder()
      .modality(entity.getModality())
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