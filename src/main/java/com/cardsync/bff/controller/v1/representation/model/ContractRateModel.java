package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractRateModel {

  private UUID id;
  private String modality;
  private BigDecimal rate;
  private Integer paymentTermDays;
  private BigDecimal rateEcommerce;
  private Integer paymentTermDaysEcommerce;
}
