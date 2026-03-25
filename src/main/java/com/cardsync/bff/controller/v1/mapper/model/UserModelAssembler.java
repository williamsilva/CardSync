package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.UserController;
import com.cardsync.bff.controller.v1.representation.model.UserModel;
import com.cardsync.domain.model.UserEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class UserModelAssembler extends RepresentationModelAssemblerSupport<UserEntity, UserModel> {

  @Autowired
  private ModelMapper modelMapper;

  public UserModelAssembler() {
    super(UserController.class, UserModel.class);
  }

  @Override
  public UserModel toModel(UserEntity entity) {
    UserModel userModel = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, userModel);

    return userModel;
  }

  @Override
  public CollectionModel<UserModel> toCollectionModel(Iterable<? extends UserEntity> entities) {
    CollectionModel<UserModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}
