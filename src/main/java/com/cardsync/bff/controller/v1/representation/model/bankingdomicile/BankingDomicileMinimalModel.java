package com.cardsync.bff.controller.v1.representation.model.bankingdomicile;

import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.domain.model.enums.StatusEnum;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class BankingDomicileMinimalModel extends RepresentationModel<BankingDomicileMinimalModel> {

  private UUID id;

  private Integer agency;
  private Integer currentAccount;

  private String status;
  private String agencyDigit;
  private String accountDigit;

  private OffsetDateTime statusDate;

  private BankMinimalModel bank;
  private CompanyMinimalModel company;

}