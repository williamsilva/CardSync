package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class EstablishmentModel extends RepresentationModel<EstablishmentModel> {

  private UUID id;

  private String type;
  private String status;
  private Integer pvNumber;

  private OffsetDateTime createdAt;

  private UserMinimalModel createdBy;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;

}
