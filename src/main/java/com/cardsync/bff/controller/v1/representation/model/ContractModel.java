package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class ContractModel extends RepresentationModel<ContractModel> {

  private UUID id;

  private String status;
  private String description;

  private LocalDate endDate;
  private LocalDate startDate;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  private UserMinimalModel createdBy;
  private UserMinimalModel updatedBy;

  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;

  private List<ContractFlagModel> contractFlags;
}
