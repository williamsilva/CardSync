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
public class CreditOrderMinimalModel extends RepresentationModel<@NonNull CreditOrderMinimalModel> {

  private UUID id;

  private Long creditOrderNumber;
  private Integer installmentTotal;
  private Integer installmentNumber;

  private String statusPaymentBank;
  private String salesSummaryStatus;

  private LocalDate releaseDate;
  private LocalDate creditOrderDate;

  private BigDecimal grossRvValue;
  private BigDecimal releaseValue;

  private ReleasesBankMinimalModel releasesBank;
}
