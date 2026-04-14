package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.ContractController;
import com.cardsync.bff.controller.v1.representation.model.ContractModel;
import com.cardsync.domain.model.ContractEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class ContractModelAssembler extends RepresentationModelAssemblerSupport<ContractEntity, ContractModel> {

  @Autowired
  private ModelMapper modelMapper;

  public ContractModelAssembler() {
    super(ContractController.class, ContractModel.class);
  }

  @Override
  public ContractModel toModel(ContractEntity entity) {
    ContractModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<ContractModel> toCollectionModel(Iterable<? extends ContractEntity> entities) {
    CollectionModel<ContractModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}