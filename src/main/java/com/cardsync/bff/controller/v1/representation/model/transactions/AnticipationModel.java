package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class AnticipationModel extends RepresentationModel<@NonNull AnticipationModel> {

  private UUID id;

  private Integer installmentNumber;
  private Integer numberRvCorresponding;

  private LocalDate releaseDate;
  private LocalDate originalDueDate;

  private BigDecimal grossValue;
  private BigDecimal releaseValue;
  private BigDecimal discountRateValue;
  private BigDecimal originalCreditValue;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private SalesSummaryMinimalModel salesSummary;
  private EstablishmentMinimalModel establishment;
  private ProcessedFileMinimalModel processedFile;
  private BankingDomicileMinimalModel bankingDomicile;
}
