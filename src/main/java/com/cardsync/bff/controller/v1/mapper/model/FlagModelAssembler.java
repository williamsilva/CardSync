package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.FlagController;
import com.cardsync.bff.controller.v1.representation.model.RelationAcquirerModel;
import com.cardsync.bff.controller.v1.representation.model.RelationCompanyModel;
import com.cardsync.bff.controller.v1.representation.model.FlagModel;
import com.cardsync.domain.model.RelationFlagAcquirerEntity;
import com.cardsync.domain.model.RelationFlagCompanyEntity;
import com.cardsync.domain.model.FlagEntity;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class FlagModelAssembler extends RepresentationModelAssemblerSupport<FlagEntity, FlagModel> {

  public FlagModelAssembler() {
    super(FlagController.class, FlagModel.class);
  }

  @Override
  public FlagModel toModel(FlagEntity entity) {
    FlagModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setErpCode(entity.getErpCode());
    model.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);

    model.setCompanies(
      entity.getFlagCompanies().stream()
        .filter(item -> item.getCompany() != null)
        .map(this::toCompanyRelationModel)
        .sorted(Comparator.comparing(
          RelationCompanyModel::getFantasyName,
          Comparator.nullsLast(String::compareToIgnoreCase)
        ))
        .toList()
    );

    model.setAcquirers(
      entity.getFlagAcquirers().stream()
        .filter(item -> item.getAcquirer() != null)
        .map(this::toAcquirerRelationModel)
        .sorted(Comparator.comparing(
          RelationAcquirerModel::getFantasyName,
          Comparator.nullsLast(String::compareToIgnoreCase)
        ))
        .toList()
    );

    return model;
  }

  @Override
  public CollectionModel<FlagModel> toCollectionModel(Iterable<? extends FlagEntity> entities) {
    return super.toCollectionModel(entities);
  }

  private RelationCompanyModel toCompanyRelationModel(RelationFlagCompanyEntity item) {
    var company = item.getCompany();

    return RelationCompanyModel.builder()
      .cnpj(company.getCnpj())
      .companyId(company.getId())
      .fantasyName(company.getFantasyName())
      .socialReason(company.getSocialReason())
      .type(company.getType() != null ? company.getType().name() : null)
      .status(company.getStatus() != null ? company.getStatus().name() : null)
      .build();
  }

  private RelationAcquirerModel toAcquirerRelationModel(RelationFlagAcquirerEntity item) {
    var acquirer = item.getAcquirer();

    return RelationAcquirerModel.builder()
      .cnpj(acquirer.getCnpj())
      .acquirerId( acquirer.getId())
      .acquirerCode(item.getAcquirerCode())
      .fantasyName( acquirer.getFantasyName())
      .socialReason(acquirer.getSocialReason())
      .status(acquirer.getStatus() != null ? acquirer.getStatus().name() : null)
      .build();
  }
}