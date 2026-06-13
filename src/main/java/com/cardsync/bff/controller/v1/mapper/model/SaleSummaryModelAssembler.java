package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.SaleSummaryController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.*;
import com.cardsync.domain.model.*;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaleSummaryModelAssembler extends RepresentationModelAssemblerSupport<
  @NonNull SalesSummaryEntity,
  @NonNull SaleSummaryModel
  > {

  public SaleSummaryModelAssembler() {
    super(SaleSummaryController.class, SaleSummaryModel.class);
  }

  @Override
  public @NonNull SaleSummaryModel toModel(@NonNull SalesSummaryEntity entity) {
    SaleSummaryModel model = createModelWithId(entity.getId(), entity);

    List<AdjustmentEntity> adjustments = getAdjustments(entity);
    List<CreditOrderEntity> creditOrders = getCreditOrders(entity);

    model.setId(entity.getId());
    model.setRvDate(entity.getRvDate());
    model.setModality(entity.getModality());
    model.setRvNumber(entity.getRvNumber());
    model.setPvNumber(entity.getPvNumber());
    model.setLineNumber(entity.getLineNumber());
    model.setNumberCvNsu(entity.getNumberCvNsu());
    model.setStatusPaymentBank(entity.getStatusPaymentBank().name());
    model.setCreditOrderStatus(entity.getCreditOrderStatus().name());
    model.setTransactionsStatus(entity.getTransactionsStatus().name());

    model.setGrossValue(entity.getGrossValue());
    model.setLiquidValue(entity.getLiquidValue());
    model.setAdjustedValue(entity.getAdjustedValue());
    model.setDiscountValue(entity.getDiscountValue());

    model.setFlag(toFlag(entity.getFlag()));
    model.setCompany(toCompany(entity.getCompany()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setProcessedFile(toProcessedFile(entity.getProcessedFile()));

    model.setAdjustments(adjustments.stream()
      .map(SaleSummaryModelAssembler::toAdjustment)
      .toList());

    model.setCreditOrders(creditOrders.stream()
      .map(SaleSummaryModelAssembler::toCreditOrder)
      .toList());

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

  private static AdjustmentMinimalModel toAdjustment(AdjustmentEntity entity) {
    if (entity == null) {
      return null;
    }

    return AdjustmentMinimalModel.builder()
      .id(entity.getId())
      .nsu(entity.getNsu())
      .creditDate(entity.getCreditDate())
      .adjustmentValue(entity.getAdjustmentValue())
      .rvNumberOriginal(entity.getRvNumberOriginal())
      .installmentTotal(entity.getInstallmentTotal())
      .installmentNumber(entity.getInstallmentNumber())
      .adjustmentReason(entity.getAdjustmentReason().getCode())
      .build();
  }

  private static CreditOrderMinimalModel toCreditOrder(CreditOrderEntity entity) {
    if (entity == null) {
      return null;
    }
    return CreditOrderMinimalModel.builder()
      .id(entity.getId())
      .releaseDate(entity.getReleaseDate())
      .grossRvValue(entity.getGrossRvValue())
      .releaseValue(entity.getReleaseValue())
      .creditOrderDate(entity.getCreditOrderDate())
      .installmentTotal(entity.getInstallmentTotal())
      .creditOrderNumber(entity.getCreditOrderNumber())
      .installmentNumber(entity.getInstallmentNumber())
      .releasesBank(toReleasesBank(entity.getReleaseBank()))
      .statusPaymentBank(entity.getStatusPaymentBank().name())
      .salesSummaryStatus(entity.getSalesSummaryStatus().name())
      .build();
  }

  private static ReleasesBankMinimalModel toReleasesBank(ReleasesBankEntity entity) {
    if (entity == null) {
      return null;
    }
   return ReleasesBankMinimalModel.builder()
     .id(entity.getId())
     .releaseDate(entity.getReleaseDate())
     .build();
  }

  private static List<AdjustmentEntity> getAdjustments(SalesSummaryEntity entity) {
    return entity.getAdjustments() == null
      ? List.of()
      : entity.getAdjustments().stream()
      .toList();
  }

  private static List<CreditOrderEntity> getCreditOrders(SalesSummaryEntity entity) {
    return entity.getCreditOrders() == null
      ? List.of()
      : entity.getCreditOrders().stream()
      .toList();
  }
}
