package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.BankController;
import com.cardsync.bff.controller.v1.representation.model.bank.BankModel;
import com.cardsync.domain.model.BankEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class BankModelAssembler extends RepresentationModelAssemblerSupport<BankEntity,@NonNull BankModel> {

  public BankModelAssembler() {
    super(BankController.class, BankModel.class);
  }

  @Override
  public BankModel toModel(@NonNull BankEntity entity) {
    BankModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setCode(entity.getCode());
    model.setStatus(entity.getStatus());
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    model.setStatusDate(entity.getStatusDate());

    return model;
  }

}