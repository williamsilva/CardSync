package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.CompanyController;
import com.cardsync.bff.controller.v1.representation.model.CompanyModel;
import com.cardsync.domain.model.CompanyEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class CompanyModelAssembler extends RepresentationModelAssemblerSupport<CompanyEntity, CompanyModel> {

  @Autowired
  private ModelMapper modelMapper;

  public CompanyModelAssembler() {
    super(CompanyController.class, CompanyModel.class);
  }

  @Override
  public CompanyModel toModel(CompanyEntity entity) {
    CompanyModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<CompanyModel> toCollectionModel(Iterable<? extends CompanyEntity> entities) {
    CollectionModel<CompanyModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}