package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.BankController;
import com.cardsync.bff.controller.v1.representation.model.bank.BankModel;
import com.cardsync.domain.model.BankEntity;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class BankModelAssembler extends RepresentationModelAssemblerSupport<BankEntity, BankModel> {

  public BankModelAssembler() {
    super(BankController.class, BankModel.class);
  }

  @Override
  public BankModel toModel(BankEntity entity) {
    BankModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setCode(entity.getCode());
    model.setStatus(entity.getStatus());
    model.setStatusDate(entity.getStatusDate());
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());

    return model;
  }

  @Override
  public CollectionModel<BankModel> toCollectionModel(Iterable<? extends BankEntity> entities) {
    return super.toCollectionModel(entities);
  }

}