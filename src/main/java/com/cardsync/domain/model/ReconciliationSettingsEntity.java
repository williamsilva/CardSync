package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cs_reconciliation_settings")
public class ReconciliationSettingsEntity extends AuditableEntityBase {

  @Column(name = "erp_acquirer_previous_days_lookback", nullable = false)
  private int erpAcquirerPreviousDaysLookback = 0;

  @Column(name = "erp_acquirer_future_days_lookback", nullable = false)
  private int erpAcquirerFutureDaysLookback = 0;

  @Column(name = "reconciliation_lookback_months", nullable = false)
  private int reconciliationLookbackMonths = 1;

  @Column(name = "credit_order_pending_days", nullable = false)
  private int creditOrderPendingDays = 30;
}
