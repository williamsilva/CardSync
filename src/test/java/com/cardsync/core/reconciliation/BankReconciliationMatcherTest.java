package com.cardsync.core.reconciliation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankReconciliationMatcherTest {

  private final BankReconciliationMatcher matcher = new BankReconciliationMatcher();

  private static final long SAFE_CAP_CENTS = 100_000_000L;
  private static final long SUBSET_DP_MAX_CENTS = 10_000_000L;

  @Test
  void notMatchedWhenNoCandidates() {
    List<String> candidates = List.of();
    var result = matcher.selectByValue(candidates, BigDecimal::new, new BigDecimal("10.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS);

    assertThat(result.matched()).isFalse();
  }

  @Test
  void notMatchedWhenTargetValueIsNull() {
    var result = matcher.selectByValue(List.of("100.00"), BigDecimal::new, null,
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS);

    assertThat(result.matched()).isFalse();
  }

  @Test
  void matchesSingleCandidateWithinTolerance() {
    var result = matcher.selectByValue(
      List.of("50.00", "100.03", "200.00"), BigDecimal::new, new BigDecimal("100.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isTrue();
    assertThat(result.itemsMatched()).isEqualTo(1);
    assertThat(result.<String>typedItems()).containsExactly("100.03");
  }

  @Test
  void doesNotMatchSingleCandidateOutsideTolerance() {
    var result = matcher.selectByValue(
      List.of("100.10"), BigDecimal::new, new BigDecimal("100.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isFalse();
  }

  @Test
  void matchesSumOfAllCandidatesWhenNoSingleCandidateMatches() {
    var result = matcher.selectByValue(
      List.of("30.00", "70.00"), BigDecimal::new, new BigDecimal("100.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isTrue();
    assertThat(result.itemsMatched()).isEqualTo(2);
    assertThat(result.matchedValue()).isEqualByComparingTo("100.00");
  }

  @Test
  void bruteForceFindsExactSubsetAmongUpTo20Candidates() {
    // Soma total (30+45+70+12.50=157.50) não bate; só o subconjunto {45, 70} bate com 115.00.
    var result = matcher.selectByValue(
      List.of("30.00", "45.00", "70.00", "12.50"), BigDecimal::new, new BigDecimal("115.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isTrue();
    assertThat(result.<String>typedItems()).containsExactlyInAnyOrder("45.00", "70.00");
  }

  @Test
  void dpSubsetSumFindsExactSubsetForLargeGroups() {
    // 25 candidatos (> 20, força a DP em centavos): só {v0, v1} bate com o alvo.
    List<String> candidates = new java.util.ArrayList<>();
    candidates.add("11.00");
    candidates.add("22.00");
    for (int i = 0; i < 23; i++) {
      candidates.add(String.valueOf(1000 + i) + ".00");
    }

    var result = matcher.selectByValue(
      candidates, BigDecimal::new, new BigDecimal("33.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isTrue();
    assertThat(result.<String>typedItems()).containsExactlyInAnyOrder("11.00", "22.00");
  }

  @Test
  void skippedWhenTargetExceedsSafeCap() {
    List<String> candidates = new java.util.ArrayList<>();
    for (int i = 0; i < 25; i++) {
      candidates.add(String.valueOf(1000 + i) + ".00");
    }

    var result = matcher.selectByValue(
      candidates, BigDecimal::new, new BigDecimal("999999.00"),
      new BigDecimal("0.05"), 1_000_000L, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isFalse();
    assertThat(result.skippedBySafetyCap()).isTrue();
  }

  @Test
  void notMatchedWhenNoSubsetReachesTarget() {
    var result = matcher.selectByValue(
      List.of("10.00", "20.00"), BigDecimal::new, new BigDecimal("100.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isFalse();
  }

  @Test
  void toleranceIsInclusiveAtTheBoundary() {
    var result = matcher.selectByValue(
      List.of("100.05"), BigDecimal::new, new BigDecimal("100.00"),
      new BigDecimal("0.05"), SAFE_CAP_CENTS, SUBSET_DP_MAX_CENTS
    );

    assertThat(result.matched()).isTrue();
  }
}
