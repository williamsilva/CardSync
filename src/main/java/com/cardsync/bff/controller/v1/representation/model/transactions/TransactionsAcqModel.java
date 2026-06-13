package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileSummaryModel;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class TransactionsAcqModel extends RepresentationModel<@NonNull TransactionsAcqModel> {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;
  private Integer lineNumber;
  private Integer installment;
  private Integer statusTransactionReason;

  private String tid;
  private String cardNumber;
  private String authorization;
  private String statusTransaction;

  private BigDecimal mdrRate;
  private BigDecimal flexRate;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal adjustmentValue;

  private OffsetDateTime saleDate;
  private LocalDate expectedPaymentDate;
  private OffsetDateTime saleReconciliationDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private SalesSummaryMinimalModel salesSummary;
  private EstablishmentMinimalModel establishment;
  private ProcessedFileMinimalModel processedFile;

  private List<TransactionAcqInstallmentModel> installments = new ArrayList<>();
}
