package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.EmailLogController;
import com.cardsync.bff.controller.v1.representation.model.EmailLogModel;
import com.cardsync.domain.model.EmailLogEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class EmailLogModelAssembler extends RepresentationModelAssemblerSupport<EmailLogEntity, EmailLogModel> {

  @Autowired
  private ModelMapper modelMapper;

  public EmailLogModelAssembler() {
    super(EmailLogController.class, EmailLogModel.class);
  }

  @Override
  public EmailLogModel toModel(EmailLogEntity entity) {
    EmailLogModel emailLogModel = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, emailLogModel);

    return emailLogModel;
  }

  @Override
  public CollectionModel<EmailLogModel> toCollectionModel(Iterable<? extends EmailLogEntity> entities) {
    CollectionModel<EmailLogModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}