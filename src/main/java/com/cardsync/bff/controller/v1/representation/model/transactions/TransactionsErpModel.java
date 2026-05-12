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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class TransactionsErpModel extends RepresentationModel<@NonNull TransactionsErpModel> {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;
  private Integer lineNumber;
  private Integer installment;
  private Integer transactionStatus;
  private Integer transactionStatusReason;

  private String tid;
  private String cardName;
  private String cardNumber;
  private String authorization;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal contractedFee;
  private BigDecimal adjustmentValue;

  private OffsetDateTime saleDate;
  private LocalDate expectedPaymentDate;
  private OffsetDateTime saleReconciliationDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
  private ProcessedFileMinimalModel processedFile;
  private BankingDomicileMinimalModel bankingDomicile;

  private List<TransactionErpInstallmentModel> installments = new ArrayList<>();
}
