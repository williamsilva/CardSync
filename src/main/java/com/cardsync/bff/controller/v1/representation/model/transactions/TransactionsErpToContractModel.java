package com.cardsync.bff.controller.v1.representation.model.transactions;

import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class TransactionsErpToContractModel extends RepresentationModel<@NonNull TransactionsErpToContractModel> {

  private UUID id;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
}
