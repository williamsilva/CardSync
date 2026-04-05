package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.GroupsController;
import com.cardsync.bff.controller.v1.representation.model.GroupModel;
import com.cardsync.bff.controller.v1.representation.model.PermissionOptionModel;
import com.cardsync.bff.controller.v1.representation.model.UserMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.UserOptionModel;
import com.cardsync.domain.model.GroupEntity;
import java.util.Comparator;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class GroupModelAssembler extends RepresentationModelAssemblerSupport<GroupEntity, GroupModel> {

  public GroupModelAssembler() {
    super(GroupsController.class, GroupModel.class);
  }

  @Override
  public GroupModel toModel(GroupEntity entity) {
    GroupModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setName(entity.getName());
    model.setCreatedAt(entity.getCreatedAt());
    model.setDescription(entity.getDescription());
    model.setCreatedBy(getUserMinimalModel(entity));

    List<PermissionOptionModel> permissions = entity.getPermissions() == null
      ? List.of()
      : entity.getPermissions().stream()
          .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
          .map(p -> new PermissionOptionModel(p.getId(), p.getName(), p.getDescription()))
          .toList();

    List<UserOptionModel> users = entity.getUsers() == null
      ? List.of()
      : entity.getUsers().stream()
          .sorted(Comparator.comparing(u -> (u.getName() == null ? "" : u.getName()).toLowerCase()))
          .map(u -> new UserOptionModel(u.getId(), u.getName(), u.getUserName()))
          .toList();


    model.setPermissions(permissions);
    model.setUsers(users);
    model.setPermissionsCount(permissions.size());
    model.setUsersCount(users.size());
    return model;
  }

  private static UserMinimalModel getUserMinimalModel(GroupEntity entity) {
    UserMinimalModel userMinimalModel = new UserMinimalModel();
    userMinimalModel.setId(entity.getCreatedBy().getId());
    userMinimalModel.setName(entity.getCreatedBy().getName());
    userMinimalModel.setUserName(entity.getCreatedBy().getUserName());
    return userMinimalModel;
  }

  @Override
  public CollectionModel<GroupModel> toCollectionModel(Iterable<? extends GroupEntity> entities) {
    return super.toCollectionModel(entities);
  }
}
