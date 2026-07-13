package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
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
public class CreditOrderModel extends RepresentationModel<@NonNull CreditOrderModel> {

  private UUID id;
  private UUID releaseBankId;

  private Integer rvNumber;
  private Integer originalPvNumber;
  private Integer installmentTotal;
  private Integer installmentNumber;

  private String statusPaymentBank;
  private String salesSummaryStatus;

  private BigDecimal releaseValue;
  private BigDecimal grossRvValue;
  private BigDecimal discountRateValue;

  private LocalDate rvDate;
  private LocalDate releaseDate;
  private LocalDate creditOrderDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private SalesSummaryMinimalModel salesSummary;
  private ProcessedFileMinimalModel processedFile;
  private BankingDomicileMinimalModel bankingDomicile;
}
