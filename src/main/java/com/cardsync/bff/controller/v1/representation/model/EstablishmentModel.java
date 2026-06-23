package com.cardsync.bff.controller.v1.representation.model;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
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

  private LocalDate openingDate;
  private LocalDate closingDate;

  private OffsetDateTime createdAt;

  private UserMinimalModel createdBy;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;

}
