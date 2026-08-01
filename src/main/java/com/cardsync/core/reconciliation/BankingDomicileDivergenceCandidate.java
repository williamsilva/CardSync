package com.cardsync.core.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Um lançamento bancário pendente que só fecha o valor quando o subconjunto de ordens de
 * crédito ignora banco — indício de que uma ou mais dessas ordens está com
 * banking_domicile apontando pro banco errado (ver BankingDomicileDivergenceService).
 */
public record BankingDomicileDivergenceCandidate(
  UUID releaseBankId,
  String companyName,
  String acquirerName,
  String releaseBankName,
  LocalDate releaseDate,
  BigDecimal releaseValue,
  List<BankingDomicileMismatchOrder> mismatchedOrders
) {}
