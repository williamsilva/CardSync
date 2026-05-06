package com.cardsync.core.file.erp.contract;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractRateEntity;

import java.math.BigDecimal;

public record ContractedErpRate(
  ContractEntity contract,
  ContractRateEntity contractRate,
  BigDecimal rate,
  Integer paymentTermDays,
  boolean ecommerceRate
) {
}
