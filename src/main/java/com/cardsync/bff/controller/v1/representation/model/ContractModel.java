package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
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

  private CompanyModel company;
  private AcquirerModel acquirer;

  //private List<ContractFlagModel> contractFlags;
  //private EstablishmentAcquirerModel establishment;

}