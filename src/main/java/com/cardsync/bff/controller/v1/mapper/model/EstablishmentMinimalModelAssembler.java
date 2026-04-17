package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.EstablishmentController;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.domain.model.EstablishmentEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class EstablishmentMinimalModelAssembler extends RepresentationModelAssemblerSupport<EstablishmentEntity, EstablishmentMinimalModel> {

  @Autowired
  private ModelMapper modelMapper;

  public EstablishmentMinimalModelAssembler() {
    super(EstablishmentController.class, EstablishmentMinimalModel.class);
  }

  @Override
  public EstablishmentMinimalModel toModel(EstablishmentEntity entity) {
    EstablishmentMinimalModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<EstablishmentMinimalModel> toCollectionModel(Iterable<? extends EstablishmentEntity> entities) {
    CollectionModel<EstablishmentMinimalModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}