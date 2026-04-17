package com.cardsync.bff.controller.v1.representation.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationCompanyModel {

  private UUID companyId;

  private String cnpj;
  private String type;
  private String status;
  private String fantasyName;
  private String socialReason;
}