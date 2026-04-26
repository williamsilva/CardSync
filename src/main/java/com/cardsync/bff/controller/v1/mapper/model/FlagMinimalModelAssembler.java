package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.FlagController;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.domain.model.FlagEntity;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class FlagMinimalModelAssembler extends RepresentationModelAssemblerSupport<FlagEntity, FlagMinimalModel> {

  public FlagMinimalModelAssembler() {
    super(FlagController.class, FlagMinimalModel.class);
  }

  @Override
  public FlagMinimalModel toModel(FlagEntity entity) {
    FlagMinimalModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setErpCode(entity.getErpCode());
    model.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);

    return model;
  }

  @Override
  public CollectionModel<FlagMinimalModel> toCollectionModel(Iterable<? extends FlagEntity> entities) {
    return super.toCollectionModel(entities);
  }

}