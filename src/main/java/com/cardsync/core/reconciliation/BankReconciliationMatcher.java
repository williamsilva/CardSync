package com.cardsync.core.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
class BankReconciliationMatcher {

  MatchResult notMatched() {
    return MatchResult.notMatched();
  }

  <T> MatchResult selectByValue(
    List<T> candidates,
    ValueExtractor<T> extractor,
    BigDecimal targetValue,
    BigDecimal tolerance,
    int recursiveLimit,
    long safeCapCents
  ) {
    if (targetValue == null || candidates == null || candidates.isEmpty()) return MatchResult.notMatched();

    BigDecimal safeTolerance = tolerance == null ? new BigDecimal("0.05") : tolerance.abs();
    List<T> sorted = candidates.stream()
      .filter(item -> extractor.value(item) != null)
      .sorted(Comparator.comparing(extractor::value))
      .toList();

    if (sorted.isEmpty()) return MatchResult.notMatched();

    for (T candidate : sorted) {
      BigDecimal value = extractor.value(candidate);
      if (sameAmount(value, targetValue, safeTolerance)) {
        return MatchResult.matched(List.of(candidate), value, false);
      }
    }

    BigDecimal total = sum(sorted, extractor);
    if (sameAmount(total, targetValue, safeTolerance)) {
      return MatchResult.matched(sorted, total, false);
    }

    long targetCents = toCents(targetValue.add(safeTolerance));
    if (targetCents > safeCapCents) {
      log.debug("Subset reconciliation ignored because target cents exceeded safe cap. target={}, safeCapCents={}", targetValue, safeCapCents);
      return MatchResult.skipped();
    }

    MatchResult greedyDesc = greedySubset(sorted.stream()
      .sorted((a, b) -> extractor.value(b).compareTo(extractor.value(a)))
      .toList(), extractor, targetValue, safeTolerance);
    if (greedyDesc.matched()) return greedyDesc;

    MatchResult greedyAsc = greedySubset(sorted, extractor, targetValue, safeTolerance);
    if (greedyAsc.matched()) return greedyAsc;

    int limit = recursiveLimit <= 0 ? 30 : recursiveLimit;
    if (sorted.size() > limit) return MatchResult.notMatched();

    return recursiveSubset(sorted, extractor, targetValue, safeTolerance, 0, BigDecimal.ZERO, new ArrayList<>());
  }

  private <T> MatchResult greedySubset(List<T> candidates, ValueExtractor<T> extractor, BigDecimal targetValue, BigDecimal tolerance) {
    List<T> selected = new ArrayList<>();
    BigDecimal sum = BigDecimal.ZERO;
    BigDecimal maxAllowed = targetValue.add(tolerance);

    for (T candidate : candidates) {
      BigDecimal value = extractor.value(candidate);
      BigDecimal next = sum.add(value);
      if (next.compareTo(maxAllowed) <= 0) {
        selected.add(candidate);
        sum = next;
      }
      if (sameAmount(sum, targetValue, tolerance)) {
        return MatchResult.matched(selected, sum, false);
      }
    }
    return MatchResult.notMatched();
  }

  private <T> MatchResult recursiveSubset(
    List<T> candidates,
    ValueExtractor<T> extractor,
    BigDecimal targetValue,
    BigDecimal tolerance,
    int index,
    BigDecimal sum,
    List<T> selected
  ) {
    if (sameAmount(sum, targetValue, tolerance)) {
      return MatchResult.matched(new ArrayList<>(selected), sum, false);
    }
    if (index >= candidates.size() || sum.compareTo(targetValue.add(tolerance)) > 0) {
      return MatchResult.notMatched();
    }

    T current = candidates.get(index);
    selected.add(current);
    MatchResult withCurrent = recursiveSubset(candidates, extractor, targetValue, tolerance, index + 1, sum.add(extractor.value(current)), selected);
    if (withCurrent.matched()) return withCurrent;

    selected.remove(selected.size() - 1);
    return recursiveSubset(candidates, extractor, targetValue, tolerance, index + 1, sum, selected);
  }

  private <T> BigDecimal sum(List<T> candidates, ValueExtractor<T> extractor) {
    return candidates.stream()
      .map(extractor::value)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean sameAmount(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
    return a != null && b != null && a.subtract(b).abs().compareTo(tolerance) <= 0;
  }

  private long toCents(BigDecimal value) {
    if (value == null) return 0L;
    return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
  }

  interface ValueExtractor<T> {
    BigDecimal value(T item);
  }

  static final class MatchResult {

    private final List<?> items;
    private final BigDecimal matchedValue;
    private final boolean skippedBySafetyCap;

    private MatchResult(List<?> items, BigDecimal matchedValue, boolean skippedBySafetyCap) {
      this.items = items == null ? List.of() : List.copyOf(items);
      this.matchedValue = matchedValue == null ? BigDecimal.ZERO : matchedValue;
      this.skippedBySafetyCap = skippedBySafetyCap;
    }

    static MatchResult matched(List<?> items, BigDecimal value, boolean skippedBySafetyCap) {
      return new MatchResult(items, value, skippedBySafetyCap);
    }

    static MatchResult notMatched() {
      return new MatchResult(List.of(), BigDecimal.ZERO, false);
    }

    static MatchResult skipped() {
      return new MatchResult(List.of(), BigDecimal.ZERO, true);
    }

    boolean matched() {
      return !items.isEmpty();
    }

    int itemsMatched() {
      return items.size();
    }

    BigDecimal matchedValue() {
      return matchedValue;
    }

    boolean skippedBySafetyCap() {
      return skippedBySafetyCap;
    }

    @SuppressWarnings("unchecked")
    <T> List<T> typedItems() {
      return (List<T>) items;
    }
  }
}
