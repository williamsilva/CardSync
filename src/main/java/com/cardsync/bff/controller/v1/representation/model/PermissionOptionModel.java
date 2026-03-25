package com.cardsync.bff.controller.v1.representation.model;

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
public class PermissionOptionModel extends RepresentationModel<PermissionOptionModel> {
  private UUID id;
  private String name;
  private String description;
}
