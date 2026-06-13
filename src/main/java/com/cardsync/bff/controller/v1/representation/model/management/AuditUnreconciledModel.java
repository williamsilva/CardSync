package com.cardsync.bff.controller.v1.representation.model.management;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Auditoria de transações NÃO conciliadas, agrupadas por adquirente e por dia.

 * Por dia/adquirente:
 * - erpAcq          (ERP_ACQ): conciliadas dos dois lados (ERP x Adquirente).
 * - onlyInErp       (ONLY_IN_ERP): pendentes só no ERP (sem par na adquirente).
 * - onlyInAcquirer  (ONLY_IN_ACQUIRER): pendentes só na adquirente (sem par no ERP).
 */
public record AuditUnreconciledModel(
  long total,
  List<AcquirerGroup> acquirers
) {

  public record AcquirerGroup(
    UUID acquirerId,
    String acquirer,
    long count,
    List<DayDetail> details
  ) {
  }

  public record DayDetail(
    String date,
    @JsonProperty("ERP_ACQ") long erpAcq,
    @JsonProperty("ONLY_IN_ERP") long onlyInErp,
    @JsonProperty("ONLY_IN_ACQUIRER") long onlyInAcquirer
  ) {
  }
}