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
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class TransactionsAcqMinimalModel extends RepresentationModel<@NonNull TransactionsAcqMinimalModel> {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;
  private Integer lineNumber;
  private Integer installment;
  private Integer statusTransaction;
  private Integer statusTransactionReason;

  private OffsetDateTime saleDate;
  private OffsetDateTime saleReconciliationDate;

  private BigDecimal grossValue;

  private String tid;
  private String cardNumber;
  private String authorization;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private SalesSummaryMinimalModel salesSummary;
  private EstablishmentMinimalModel establishment;
  private ProcessedFileMinimalModel processedFile;

}
