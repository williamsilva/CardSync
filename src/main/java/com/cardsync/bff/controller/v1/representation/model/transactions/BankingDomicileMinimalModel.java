package com.cardsync.bff.controller.v1.representation.model.transactions;

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
public class BankingDomicileMinimalModel extends RepresentationModel<@NonNull BankingDomicileMinimalModel> {

  private UUID id;

  private Integer agency;
  private Integer currentAccount;

  private BankMinimalModel bank;
}
