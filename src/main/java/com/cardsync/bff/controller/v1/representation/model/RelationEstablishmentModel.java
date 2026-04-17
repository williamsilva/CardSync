package com.cardsync.bff.controller.v1.representation.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationEstablishmentModel {

  private UUID establishmentId;

  private String type;
  private String status;
  private Integer pvNumber;

  private CompanyMinimalModel company;
}