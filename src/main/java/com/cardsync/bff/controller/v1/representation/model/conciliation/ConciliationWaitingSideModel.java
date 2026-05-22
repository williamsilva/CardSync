package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConciliationWaitingSideModel {

  private UUID id;

  private Long cvNsu;
  private Integer capture;
  private Integer modality;
  private Integer installment;
  private Integer statusTransaction;
  private Integer statusTransactionReason;

  private String authorization;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal discountValue;

  private OffsetDateTime saleDate;
  private OffsetDateTime saleReconciliationDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
}
