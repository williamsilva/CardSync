package com.cardsync.bff.controller.v1.representation.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class GroupModel extends RepresentationModel<GroupModel>{
  private UUID id;
  private String name;
  private String description;
  private Integer usersCount;
  private Integer permissionsCount;

  private OffsetDateTime createdAt;
  private UserMinimalModel createdBy;

  private List<UserOptionModel> users;
  private List<PermissionOptionModel> permissions;
}
