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
public class AcquirerMinimalModel extends RepresentationModel<AcquirerMinimalModel> {

  private UUID id;

  private String cnpj;
  private String status;
  private String fantasyName;
  private String socialReason;

}