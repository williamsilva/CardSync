package com.cardsync.bff.controller.v1.representation.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationAcquirerModel {

  private UUID acquirerId;

  private String cnpj;
  private String status;
  private String fantasyName;
  private String socialReason;
  private String acquirerCode;
}