package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AcquirerController;
import com.cardsync.bff.controller.v1.representation.model.*;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.RelationAcquirerEstablishmentEntity;
import com.cardsync.domain.model.RelationAcquirerCompanyEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class AcquirerModelAssembler extends RepresentationModelAssemblerSupport<AcquirerEntity, AcquirerModel> {

  @Autowired
  private ModelMapper modelMapper;

  public AcquirerModelAssembler() {
    super(AcquirerController.class, AcquirerModel.class);
  }

  @Override
  public AcquirerModel toModel(AcquirerEntity entity) {
    AcquirerModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setCnpj(entity.getCnpj());
    model.setCreatedAt(entity.getCreatedAt());
    model.setFantasyName(entity.getFantasyName());
    model.setSocialReason(entity.getSocialReason());
    model.setFileIdentifier(entity.getFileIdentifier());
    model.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);

    if (entity.getCreatedBy() != null) {
      model.setCreatedBy(new UserMinimalModel(
        entity.getCreatedBy().getId(),
        entity.getCreatedBy().getName(),
        entity.getCreatedBy().getUserName()
      ));
    }

    model.setCompanies(
      entity.getAcquirerCompanies().stream()
        .filter(item -> item.getCompany() != null)
        .map(this::toCompanyRelationModel)
        .sorted(Comparator.comparing(
          RelationCompanyModel::getFantasyName,
          Comparator.nullsLast(String::compareToIgnoreCase)
        ))
        .toList()
    );

    model.setEstablishments(
      entity.getAcquirerEstablishments().stream()
        .filter(item -> item.getEstablishment() != null)
        .map(this::toEstablishmentRelationModel)
        .sorted(Comparator.comparing(
          RelationEstablishmentModel::getPvNumber,
          Comparator.nullsLast(Integer::compareTo)
        ))
        .toList()
    );

    return model;
  }

  @Override
  public CollectionModel<AcquirerModel> toCollectionModel(Iterable<? extends AcquirerEntity> entities) {
    CollectionModel<AcquirerModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }

  private RelationCompanyModel toCompanyRelationModel(RelationAcquirerCompanyEntity item) {
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

  private RelationEstablishmentModel toEstablishmentRelationModel(RelationAcquirerEstablishmentEntity item) {
    var establishment = item.getEstablishment();

    return RelationEstablishmentModel.builder()
      .establishmentId( establishment.getId())
      .pvNumber( establishment.getPvNumber())
      .company(toCompanyMinimal(establishment.getCompany()))
      .type(establishment.getType() != null ? establishment.getType().name(): null)
      .status(establishment.getStatus() != null ? establishment.getStatus().name() : null)
      .build();
  }

  private CompanyMinimalModel toCompanyMinimal(CompanyEntity entity) {
    if (entity != null) {
       return CompanyMinimalModel.builder()
         .id(entity.getId())
         .cnpj( entity.getCnpj())
         .fantasyName( entity.getFantasyName())
         .socialReason(entity.getSocialReason())
         .type(entity.getType()!=null ? entity.getType().name():null)
         .status(entity.getStatus()!=null ? entity.getStatus().name():null)
         .build();
    } else {
      return null;
    }
  }
}
