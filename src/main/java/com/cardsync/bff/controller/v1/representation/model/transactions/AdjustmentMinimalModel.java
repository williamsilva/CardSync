package com.cardsync.bff.controller.v1.representation.model.transactions;

import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class AdjustmentMinimalModel extends RepresentationModel<@NonNull AdjustmentMinimalModel> {

  private UUID id;
  private Long nsu;
  private Integer adjustmentReason;
  private Integer rvNumberOriginal;
  private Integer installmentTotal;
  private Integer installmentNumber;

  private BigDecimal adjustmentValue;

  private LocalDate creditDate;
}
