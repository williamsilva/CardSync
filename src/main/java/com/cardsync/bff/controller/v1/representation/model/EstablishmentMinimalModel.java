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
public class EstablishmentMinimalModel extends RepresentationModel<EstablishmentMinimalModel> {

  private UUID id;
  private String type;
  private String status;
  private String pvNumber;

  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
}
