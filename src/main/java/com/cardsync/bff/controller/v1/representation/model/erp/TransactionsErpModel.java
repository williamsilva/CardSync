package com.cardsync.bff.controller.v1.representation.model.erp;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
public class TransactionsErpModel extends RepresentationModel<TransactionsErpModel> {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;
  private Integer installment;

  private String authorization;

  private BigDecimal feeValue;
  private BigDecimal netValue;
  private BigDecimal grossValue;
  private BigDecimal adjustmentValue;

  private OffsetDateTime saleDate;

  private LocalDate expectedPaymentDate;

  private Integer conciliationStatus;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;

  private List<TransactionErpInstallmentModel> installments = new ArrayList<>();
}
