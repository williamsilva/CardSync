package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ContractController;
import com.cardsync.bff.controller.v1.representation.model.*;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractFlagEntity;
import com.cardsync.domain.model.ContractRateEntity;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ContractModelAssembler extends RepresentationModelAssemblerSupport<ContractEntity, ContractModel> {

  public ContractModelAssembler() {
    super(ContractController.class, ContractModel.class);
  }

  @Override
  public ContractModel toModel(ContractEntity entity) {
    ContractModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setEndDate(entity.getEndDate());
    model.setStartDate(entity.getStartDate());
    model.setDescription(entity.getDescription());

    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    model.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);

    if (entity.getCreatedBy() != null) {
      model.setCreatedBy(new UserMinimalModel(
        entity.getCreatedBy().getId(),
        entity.getCreatedBy().getName(),
        entity.getCreatedBy().getUserName()
      ));
    }

    if (entity.getUpdatedBy() != null) {
      model.setUpdatedBy(new UserMinimalModel(
        entity.getUpdatedBy().getId(),
        entity.getUpdatedBy().getName(),
        entity.getUpdatedBy().getUserName()
      ));
    }

    if (entity.getCompany() != null) {
      model.setCompany(CompanyMinimalModel.builder()
        .id(entity.getCompany().getId())
        .cnpj(entity.getCompany().getCnpj())
        .type(entity.getCompany().getType().name())
        .status(entity.getCompany().getStatus().name())
        .fantasyName(entity.getCompany().getFantasyName())
        .socialReason(entity.getCompany().getSocialReason())
        .build());
    }

    if (entity.getAcquirer() != null) {
      model.setAcquirer(AcquirerMinimalModel.builder()
        .id(entity.getAcquirer().getId())
        .cnpj(entity.getAcquirer().getCnpj())
        .fantasyName(entity.getAcquirer().getFantasyName())
        .socialReason(entity.getAcquirer().getSocialReason())
        .status(entity.getAcquirer().getStatus().name())
        .build());
    }

    if (entity.getEstablishment() != null) {
      model.setEstablishment(EstablishmentMinimalModel.builder()
        .id(entity.getEstablishment().getId())
        .type(entity.getEstablishment().getType().name())
        .pvNumber(entity.getEstablishment().getPvNumber())
        .status(entity.getEstablishment().getStatus().name())
        .build());
    }

    List<ContractFlagModel> flags = entity.getContractFlags().stream()
      .sorted(Comparator.comparing(cf -> cf.getFlag() != null ? cf.getFlag().getName() : ""))
      .map(this::toContractFlagModel)
      .toList();

    model.setContractFlags(flags);
    return model;
  }

  private ContractFlagModel toContractFlagModel(ContractFlagEntity entity) {
    ContractFlagModel model = new ContractFlagModel();
    model.setId(entity.getId());

    if (entity.getFlag() != null) {
      model.setFlag(new FlagMinimalModel(
        entity.getFlag().getId(),
        entity.getFlag().getName(),
        entity.getFlag().getStatus() != null ? entity.getFlag().getStatus().name() : null,
        entity.getFlag().getErpCode()
      ));
    }

    List<ContractRateModel> rates = entity.getContractRates().stream()
      .sorted(Comparator.comparing(rate -> rate.getModality() != null ? rate.getModality().getCode() : Integer.MAX_VALUE))
      .map(this::toContractRateModel)
      .toList();

    model.setContractRates(rates);
    return model;
  }

  private ContractRateModel toContractRateModel(ContractRateEntity entity) {
    ContractRateModel model = new ContractRateModel();
    model.setId(entity.getId());
    model.setModality(entity.getModality() != null ? entity.getModality().name() : null);
    model.setRate(entity.getRate());
    model.setPaymentTermDays(entity.getPaymentTermDays());
    model.setRateEcommerce(entity.getRateEcommerce());
    model.setPaymentTermDaysEcommerce(entity.getPaymentTermDaysEcommerce());
    return model;
  }

  @Override
  public CollectionModel<ContractModel> toCollectionModel(Iterable<? extends ContractEntity> entities) {
    return super.toCollectionModel(entities);
  }
}
