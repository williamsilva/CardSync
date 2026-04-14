package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class FlagModel extends RepresentationModel<FlagModel> {

  private UUID id;

  private String name;
  private String status;
  private Integer erpCode;

  private List<FlagCompanyRelationModel> companies;
  private List<FlagAcquirerRelationModel> acquirers;

}