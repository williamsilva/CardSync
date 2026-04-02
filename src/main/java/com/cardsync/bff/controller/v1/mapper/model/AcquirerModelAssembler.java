package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AcquirerController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerModel;
import com.cardsync.domain.model.AcquirerEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

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
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<AcquirerModel> toCollectionModel(Iterable<? extends AcquirerEntity> entities) {
    CollectionModel<AcquirerModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}