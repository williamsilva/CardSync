package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractRateEntity;

import java.math.BigDecimal;

public record ContractedAcquirerRate(
  ContractEntity contract,
  ContractRateEntity contractRate,
  BigDecimal rate,
  Integer paymentTermDays,
  boolean ecommerce
) {}
