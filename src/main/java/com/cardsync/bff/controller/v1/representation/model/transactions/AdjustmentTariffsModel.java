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
public class AdjustmentTariffsModel extends RepresentationModel<AdjustmentTariffsModel> {

  private UUID id;

  // Identificação da transação
  private Long nsu;
  private String cardNumber;
  private String authorization;
  private Integer adjustmentReason;
  private String adjustmentDescription;
  private Integer adjustmentStatus;
  private Integer rvNumberAdjustment;

  // Datas
  private LocalDate adjustmentDate;
  private LocalDate creditDate;
  private LocalDate releaseDate;

  // Valores
  private BigDecimal adjustmentValue;
  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;

  // Relacionamentos resumidos
  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
}