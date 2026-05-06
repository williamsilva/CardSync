package com.cardsync.core.reconciliation;

import java.math.BigDecimal;

public record BankReconciliationResult(
  int releasesAnalyzed,
  int releasesReconciled,
  int releasesMatchedByCreditOrders,
  int releasesMatchedByInstallments,
  int creditOrdersReconciled,
  int installmentsReconciled,
  int transactionsUpdated,
  int releasesWithoutMatch,
  int releasesSkippedMissingContext,
  int candidateGroupsSkippedBySafetyCap,
  BigDecimal totalReleaseValueReconciled,
  BigDecimal totalCreditOrderValueReconciled,
  BigDecimal totalInstallmentValueReconciled
) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int releasesAnalyzed;
    private int releasesReconciled;
    private int releasesMatchedByCreditOrders;
    private int releasesMatchedByInstallments;
    private int creditOrdersReconciled;
    private int installmentsReconciled;
    private int transactionsUpdated;
    private int releasesWithoutMatch;
    private int releasesSkippedMissingContext;
    private int candidateGroupsSkippedBySafetyCap;
    private BigDecimal totalReleaseValueReconciled = BigDecimal.ZERO;
    private BigDecimal totalCreditOrderValueReconciled = BigDecimal.ZERO;
    private BigDecimal totalInstallmentValueReconciled = BigDecimal.ZERO;

    public void releaseAnalyzed() { releasesAnalyzed++; }
    public void releaseReconciled(BigDecimal value) {
      releasesReconciled++;
      totalReleaseValueReconciled = totalReleaseValueReconciled.add(nvl(value));
    }
    public void matchedByCreditOrders(int count, BigDecimal value) {
      releasesMatchedByCreditOrders++;
      creditOrdersReconciled += count;
      totalCreditOrderValueReconciled = totalCreditOrderValueReconciled.add(nvl(value));
    }
    public void matchedByInstallments(int count, BigDecimal value) {
      releasesMatchedByInstallments++;
      installmentsReconciled += count;
      totalInstallmentValueReconciled = totalInstallmentValueReconciled.add(nvl(value));
    }
    public void transactionsUpdated(int count) { transactionsUpdated += count; }
    public void releaseWithoutMatch() { releasesWithoutMatch++; }
    public void releaseSkippedMissingContext() { releasesSkippedMissingContext++; }
    public void candidateGroupSkippedBySafetyCap() { candidateGroupsSkippedBySafetyCap++; }

    public BankReconciliationResult build() {
      return new BankReconciliationResult(
        releasesAnalyzed,
        releasesReconciled,
        releasesMatchedByCreditOrders,
        releasesMatchedByInstallments,
        creditOrdersReconciled,
        installmentsReconciled,
        transactionsUpdated,
        releasesWithoutMatch,
        releasesSkippedMissingContext,
        candidateGroupsSkippedBySafetyCap,
        totalReleaseValueReconciled,
        totalCreditOrderValueReconciled,
        totalInstallmentValueReconciled
      );
    }

    private BigDecimal nvl(BigDecimal value) {
      return value == null ? BigDecimal.ZERO : value;
    }
  }
}
