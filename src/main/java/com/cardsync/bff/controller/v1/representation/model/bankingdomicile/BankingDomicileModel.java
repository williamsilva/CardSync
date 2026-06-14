package com.cardsync.bff.controller.v1.representation.model.bankingdomicile;

import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.domain.model.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class BankingDomicileModel extends RepresentationModel<BankingDomicileModel> {

  private UUID id;
  private Integer agency;
  private Integer currentAccount;

  private String agencyDigit;
  private String accountDigit;

  private StatusEnum status;
  private OffsetDateTime statusDate;

  private LocalDate accountOpeningDate;
  private LocalDate accountClosingDate;
  private Boolean expectsFile;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  private BankMinimalModel bank;
  private CompanyMinimalModel company;

}