package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
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
  private Integer modality;
  private Integer lineNumber;
  private Integer numberCvNsu;
  private Integer currentAccount;

  private String statusPaymentBank;
  private String transactionsStatus;

  private BankingDomicileMinimalModel bankingDomicile;
}
