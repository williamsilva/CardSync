package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AcquirerController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.domain.model.AcquirerEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AcquirerMinimalModelAssembler extends RepresentationModelAssemblerSupport<AcquirerEntity, AcquirerMinimalModel> {

  @Autowired
  private ModelMapper modelMapper;

  public AcquirerMinimalModelAssembler() {
    super(AcquirerController.class, AcquirerMinimalModel.class);
  }

  @Override
  public AcquirerMinimalModel toModel(AcquirerEntity entity) {
    AcquirerMinimalModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<AcquirerMinimalModel> toCollectionModel(Iterable<? extends AcquirerEntity> entities) {
    CollectionModel<AcquirerMinimalModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}