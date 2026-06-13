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
public class AdjustmentChargeBackRequestsModel extends RepresentationModel<AdjustmentTariffsModel> {

  private UUID id;

  // Identificação da transação
  private Long nsu;
  private Integer rvNumber;
  private Integer requestCode;
  private String authorization;
  private Integer requestStatus;

  // Datas
  private LocalDate saleDate;
  private LocalDate deadline;

  // Valores
  private BigDecimal transactionValue;

  // Relacionamentos resumidos
  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private SalesSummaryMinimalModel salesSummary;
  private EstablishmentMinimalModel establishment;
}