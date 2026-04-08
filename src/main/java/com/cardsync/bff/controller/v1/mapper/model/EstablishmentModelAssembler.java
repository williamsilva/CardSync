package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.EstablishmentController;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentModel;
import com.cardsync.domain.model.EstablishmentEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class EstablishmentModelAssembler extends RepresentationModelAssemblerSupport<EstablishmentEntity, EstablishmentModel> {

  @Autowired
  private ModelMapper modelMapper;

  public EstablishmentModelAssembler() {
    super(EstablishmentController.class, EstablishmentModel.class);
  }

  @Override
  public EstablishmentModel toModel(EstablishmentEntity entity) {
    EstablishmentModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<EstablishmentModel> toCollectionModel(Iterable<? extends EstablishmentEntity> entities) {
    CollectionModel<EstablishmentModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}