package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.BankController;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.domain.model.BankEntity;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class BankMinimalModelAssembler extends RepresentationModelAssemblerSupport<BankEntity, BankMinimalModel> {

  public BankMinimalModelAssembler() {
    super(BankController.class, BankMinimalModel.class);
  }

  @Override
  public BankMinimalModel toModel(BankEntity entity) {
    BankMinimalModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setCode(entity.getCode());
    model.setStatus(entity.getStatus());

    return model;
  }

  @Override
  public CollectionModel<BankMinimalModel> toCollectionModel(Iterable<? extends BankEntity> entities) {
    return super.toCollectionModel(entities);
  }

}