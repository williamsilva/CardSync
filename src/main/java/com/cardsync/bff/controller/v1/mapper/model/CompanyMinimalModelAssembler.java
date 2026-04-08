package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.CompanyController;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.domain.model.CompanyEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class CompanyMinimalModelAssembler extends RepresentationModelAssemblerSupport<CompanyEntity, CompanyMinimalModel> {

  @Autowired
  private ModelMapper modelMapper;

  public CompanyMinimalModelAssembler() {
    super(CompanyController.class, CompanyMinimalModel.class);
  }

  @Override
  public CompanyMinimalModel toModel(CompanyEntity entity) {
    CompanyMinimalModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<CompanyMinimalModel> toCollectionModel(Iterable<? extends CompanyEntity> entities) {
    CollectionModel<CompanyMinimalModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}