package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class UserModel extends RepresentationModel<UserModel> {
  private UUID id;
  private String name;
  private Integer status;
  private String userName;
  private String document;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastLoginAt;
  private OffsetDateTime blockedUntil;
  private OffsetDateTime passwordExpiresAt;

  private UserMinimalModel createdBy;
  private List<GroupOptionModel> groups;
}
