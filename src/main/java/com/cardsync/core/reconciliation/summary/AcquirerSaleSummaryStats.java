package com.cardsync.core.reconciliation.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AcquirerSaleSummaryStats {

  private UUID salesSummaryId;
  private Long totalTransactions;
  private Long eligibleTransactions;

  public int totalTransactionsAsInt() {
    return totalTransactions != null ? Math.toIntExact(totalTransactions) : 0;
  }

  public int eligibleTransactionsAsInt() {
    return eligibleTransactions != null ? Math.toIntExact(eligibleTransactions) : 0;
  }

  public boolean hasNoEligibleTransactions() {
    return eligibleTransactionsAsInt() == 0;
  }

  public boolean isFullyEligible() {
    int total = totalTransactionsAsInt();
    int eligible = eligibleTransactionsAsInt();
    return total > 0 && eligible == total;
  }

  public boolean isPartiallyEligible() {
    int total = totalTransactionsAsInt();
    int eligible = eligibleTransactionsAsInt();
    return total > 0 && eligible > 0 && eligible < total;
  }
}
