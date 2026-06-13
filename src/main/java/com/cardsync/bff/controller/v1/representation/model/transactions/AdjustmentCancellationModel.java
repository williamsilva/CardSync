package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class AdjustmentCancellationModel extends RepresentationModel<AdjustmentCancellationModel> {

  private UUID id;

  // Identificação da transação
  private Long cvNsu;
  private String authorization;
  private Integer adjustmentReason;
  private Integer adjustmentStatus;
  private Integer rvNumberAdjustment;

  // Datas
  private LocalDate creditDate;
  private LocalDate adjustmentDate;

  // Valores
  private BigDecimal adjustmentValue;

  // Relacionamentos resumidos
  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
  private TransactionsAcqMinimalModel transaction;
}