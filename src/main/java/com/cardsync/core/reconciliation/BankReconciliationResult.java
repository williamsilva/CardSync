package com.cardsync.core.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationResult {

  private BankReconciliationTriggerType trigger;
  private BankReconciliationMode mode;

  private int releasesAnalyzed;
  private int releasesReconciled;
  private int releasesMatchedByCreditOrders;
  private int releasesMatchedByInstallments;
  private int creditOrdersReconciled;
  private int installmentsReconciled;
  private int transactionsUpdated;
  private int releasesWithoutMatch;
  private int releasesKeptPending;
  private int releasesSkippedMissingContext;
  private int candidateGroupsSkippedBySafetyCap;

  /**
   * Diagnóstico de impacto do modo estrito (ver ReconciliationSettingsEntity): quantas ordens
   * casadas NESTA execução só casaram porque bandeira/estabelecimento/modalidade estavam
   * nulos/desconhecidos em algum lado (coringa hoje). Responde "se eu ligar a regra X agora,
   * quantos dos matches que acabei de fazer deixariam de acontecer".
   */
  private int ordersMatchedRelyingOnFlagWildcard;
  private int ordersMatchedRelyingOnEstablishmentWildcard;
  private int ordersMatchedRelyingOnPaymentKindWildcard;

  private BigDecimal totalReleaseValueReconciled;
  private BigDecimal totalCreditOrderValueReconciled;
  private BigDecimal totalInstallmentValueReconciled;

  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;

  public static Counter counter(BankReconciliationTriggerType trigger, BankReconciliationMode mode) {
    return Counter.builder()
      .trigger(trigger)
      .mode(mode)
      .startedAt(OffsetDateTime.now())
      .build();
  }

  /**
   * Compatibilidade com chamadas antigas no estilo Java record.

   * O resultado foi convertido para classe Lombok para permitir @Builder, @NoArgsConstructor
   * e serialização mais flexível. Porém alguns schedulers/controllers ainda podem chamar
   * result.releasesAnalyzed() em vez de result.getReleasesAnalyzed().
   */
  public BankReconciliationTriggerType trigger() {
    return trigger;
  }

  public BankReconciliationMode mode() {
    return mode;
  }

  public int releasesAnalyzed() {
    return releasesAnalyzed;
  }

  public int releasesReconciled() {
    return releasesReconciled;
  }

  public int releasesMatchedByCreditOrders() {
    return releasesMatchedByCreditOrders;
  }

  public int releasesMatchedByInstallments() {
    return releasesMatchedByInstallments;
  }

  public int creditOrdersReconciled() {
    return creditOrdersReconciled;
  }

  public int installmentsReconciled() {
    return installmentsReconciled;
  }

  public int transactionsUpdated() {
    return transactionsUpdated;
  }

  public int releasesWithoutMatch() {
    return releasesWithoutMatch;
  }

  public int releasesKeptPending() {
    return releasesKeptPending;
  }

  public int releasesSkippedMissingContext() {
    return releasesSkippedMissingContext;
  }

  public int candidateGroupsSkippedBySafetyCap() {
    return candidateGroupsSkippedBySafetyCap;
  }

  public BigDecimal totalReleaseValueReconciled() {
    return totalReleaseValueReconciled;
  }

  public BigDecimal totalCreditOrderValueReconciled() {
    return totalCreditOrderValueReconciled;
  }

  public BigDecimal totalInstallmentValueReconciled() {
    return totalInstallmentValueReconciled;
  }

  public OffsetDateTime startedAt() {
    return startedAt;
  }

  public OffsetDateTime finishedAt() {
    return finishedAt;
  }

  @Getter
  @Setter
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Counter {

    private BankReconciliationTriggerType trigger;
    private BankReconciliationMode mode;
    private int releasesAnalyzed;
    private int releasesReconciled;
    private int releasesMatchedByCreditOrders;
    private int releasesMatchedByInstallments;
    private int creditOrdersReconciled;
    private int installmentsReconciled;
    private int transactionsUpdated;
    private int releasesWithoutMatch;
    private int releasesKeptPending;
    private int releasesSkippedMissingContext;
    private int candidateGroupsSkippedBySafetyCap;

    private int ordersMatchedRelyingOnFlagWildcard;
    private int ordersMatchedRelyingOnEstablishmentWildcard;
    private int ordersMatchedRelyingOnPaymentKindWildcard;

    @Builder.Default
    private BigDecimal totalReleaseValueReconciled = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalCreditOrderValueReconciled = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalInstallmentValueReconciled = BigDecimal.ZERO;

    private OffsetDateTime startedAt;

    public void releaseAnalyzed() {
      releasesAnalyzed++;
    }

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

    public void transactionsUpdated(int count) {
      transactionsUpdated += count;
    }

    public void releaseWithoutMatch() {
      releasesWithoutMatch++;
    }

    public void releaseKeptPending() {
      releasesKeptPending++;
    }

    public void releaseSkippedMissingContext() {
      releasesSkippedMissingContext++;
    }

    public void candidateGroupSkippedBySafetyCap() {
      candidateGroupsSkippedBySafetyCap++;
    }

    public void matchedOrderRelyingOnFlagWildcard() {
      ordersMatchedRelyingOnFlagWildcard++;
    }

    public void matchedOrderRelyingOnEstablishmentWildcard() {
      ordersMatchedRelyingOnEstablishmentWildcard++;
    }

    public void matchedOrderRelyingOnPaymentKindWildcard() {
      ordersMatchedRelyingOnPaymentKindWildcard++;
    }

    public BankReconciliationResult toResult() {
      return BankReconciliationResult.builder()
        .trigger(trigger)
        .mode(mode)
        .releasesAnalyzed(releasesAnalyzed)
        .releasesReconciled(releasesReconciled)
        .releasesMatchedByCreditOrders(releasesMatchedByCreditOrders)
        .releasesMatchedByInstallments(releasesMatchedByInstallments)
        .creditOrdersReconciled(creditOrdersReconciled)
        .installmentsReconciled(installmentsReconciled)
        .transactionsUpdated(transactionsUpdated)
        .releasesWithoutMatch(releasesWithoutMatch)
        .releasesKeptPending(releasesKeptPending)
        .releasesSkippedMissingContext(releasesSkippedMissingContext)
        .candidateGroupsSkippedBySafetyCap(candidateGroupsSkippedBySafetyCap)
        .ordersMatchedRelyingOnFlagWildcard(ordersMatchedRelyingOnFlagWildcard)
        .ordersMatchedRelyingOnEstablishmentWildcard(ordersMatchedRelyingOnEstablishmentWildcard)
        .ordersMatchedRelyingOnPaymentKindWildcard(ordersMatchedRelyingOnPaymentKindWildcard)
        .totalReleaseValueReconciled(totalReleaseValueReconciled)
        .totalCreditOrderValueReconciled(totalCreditOrderValueReconciled)
        .totalInstallmentValueReconciled(totalInstallmentValueReconciled)
        .startedAt(startedAt)
        .finishedAt(OffsetDateTime.now())
        .build();
    }

    private BigDecimal nvl(BigDecimal value) {
      return value == null ? BigDecimal.ZERO : value;
    }
  }
}
