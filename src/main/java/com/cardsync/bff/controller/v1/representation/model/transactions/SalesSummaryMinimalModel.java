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
public class SalesSummaryMinimalModel extends RepresentationModel<@NonNull SalesSummaryMinimalModel> {

  private UUID id;

  private Integer agency;
  private Integer pvNumber;
  private Integer currentAccount;

  private BankingDomicileMinimalModel bankingDomicile;
}
