package com.cardsync.bff.controller.v1.representation.model.transactions;

import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class InstallmentAcqModel extends RepresentationModel<@NonNull InstallmentAcqModel>  {

  private UUID id;

  private Integer installment;
  private Integer paymentStatus;
  private Integer installmentStatus;
  private Integer reconciliationBankLine;
  private Integer reconciliationPaymentLine;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;
  private BigDecimal adjustmentValue;

  private LocalDate paymentDate;
  private LocalDate cancellationDate;
  private LocalDate expectedPaymentDate;

  private OffsetDateTime reconciliationBankProcessedAt;
  private OffsetDateTime reconciliationPaymentProcessedAt;

  private TransactionsAcqMinimalModel transaction;
}
