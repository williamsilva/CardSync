package com.cardsync.bff.controller.v1.representation.model;

import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class FlagMinimalModel extends RepresentationModel<FlagMinimalModel> {

  private UUID id;
  private String name;
  private String status;
  private Integer erpCode;
}
