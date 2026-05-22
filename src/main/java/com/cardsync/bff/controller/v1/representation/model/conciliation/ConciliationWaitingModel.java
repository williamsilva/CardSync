package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class ConciliationWaitingModel extends RepresentationModel<@NonNull ConciliationWaitingModel> {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;

  private Integer installment;
  private Integer statusTransactionReason;

  private String authorization;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;

  private OffsetDateTime saleDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;

  /**
   * Lado ERP do par divergente. Usado principalmente em other-divergences.
   */
  private ConciliationWaitingSideModel erp;

  /**
   * Lado adquirente do par divergente. Usado principalmente em other-divergences.
   */
  private ConciliationWaitingSideModel acquirerTransaction;
}
