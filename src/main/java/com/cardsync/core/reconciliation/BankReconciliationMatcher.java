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
    long safeCapCents,
    long dpMaxCents
  ) {
    if (targetValue == null || candidates == null || candidates.isEmpty()) return MatchResult.notMatched();

    BigDecimal safeTolerance = tolerance == null ? new BigDecimal("0.05") : tolerance.abs();
    List<T> sorted = candidates.stream()
      .filter(item -> extractor.value(item) != null)
      .sorted(Comparator.comparing(extractor::value))
      .toList();

    if (sorted.isEmpty()) return MatchResult.notMatched();

    // 1) Match por candidato único.
    for (T candidate : sorted) {
      BigDecimal value = extractor.value(candidate);
      if (sameAmount(value, targetValue, safeTolerance)) {
        return MatchResult.matched(List.of(candidate), value, false);
      }
    }

    // 2) Match pela soma total de todos os candidatos.
    BigDecimal total = sum(sorted, extractor);
    if (sameAmount(total, targetValue, safeTolerance)) {
      return MatchResult.matched(sorted, total, false);
    }

    // 3) Para grupos pequenos usa força bruta 2^N — não aloca arrays grandes,
    //    funciona para qualquer valor monetário e cobre os lançamentos bancários
    //    de alto valor (R$ 100k–600k) compostos por poucas ordens (3–15 itens).
    if (sorted.size() <= 20) {
      return bruteForceSubsetSum(sorted, extractor, targetValue, safeTolerance);
    }

    long targetCents = toCents(targetValue.add(safeTolerance));
    if (targetCents > safeCapCents) {
      log.debug("Subset reconciliation ignored because target cents exceeded safe cap. target={}, safeCapCents={}", targetValue, safeCapCents);
      return MatchResult.skipped();
    }

    // 4) Subconjunto exato via programação dinâmica em centavos.
    //    Substitui a antiga heurística gulosa (que perdia combinações válidas) e a
    //    recursão exponencial (limitada a 30 itens). A DP é exata e polinomial
    //    (O(n × alvoCents)), encontrando um subconjunto cuja soma esteja dentro da
    //    tolerância sempre que ele existir.
    if (targetCents > dpMaxCents) {
      log.debug(
        "Subconjunto não tentado: alvo {} excede o teto de DP ({} centavos). "
          + "Aumente reconciliation.subset-dp-max-cents se precisar conciliar valores maiores por composição.",
        targetValue, dpMaxCents
      );
      return MatchResult.notMatched();
    }

    return subsetSumByCents(sorted, extractor, targetValue, safeTolerance);
  }

  /**
   * Subset-sum exato em centavos. Procura um subconjunto cuja soma fique entre
   * (alvo - tolerância) e (alvo + tolerância). Usa DP de alcançabilidade com
   * reconstrução do subconjunto. Complexidade O(n × maxCents) de tempo e memória.
   */
  private <T> MatchResult subsetSumByCents(
    List<T> candidates,
    ValueExtractor<T> extractor,
    BigDecimal targetValue,
    BigDecimal tolerance
  ) {
    long toleranceCents = toCents(tolerance);
    long targetCents = toCents(targetValue);
    long maxCents = targetCents + toleranceCents;
    long minCents = Math.max(0, targetCents - toleranceCents);

    if (maxCents <= 0) return MatchResult.notMatched();

    int n = candidates.size();
    long[] valueCents = new long[n];
    for (int i = 0; i < n; i++) {
      valueCents[i] = toCents(extractor.value(candidates.get(i)));
    }

    // reachable[s] = true se a soma 's' centavos é atingível por algum subconjunto.
    // parentItem[s] = índice do item usado para alcançar 's' pela primeira vez (reconstrução).
    boolean[] reachable = new boolean[(int) maxCents + 1];
    int[] parentItem = new int[(int) maxCents + 1];
    long[] parentSum = new long[(int) maxCents + 1];
    reachable[0] = true;

    for (int i = 0; i < n; i++) {
      long v = valueCents[i];
      if (v <= 0 || v > maxCents) continue;
      // percorre de cima para baixo para usar cada item no máximo uma vez (0/1 knapsack)
      for (long s = maxCents - v; s >= 0; s--) {
        if (reachable[(int) s] && !reachable[(int) (s + v)]) {
          reachable[(int) (s + v)] = true;
          parentItem[(int) (s + v)] = i;
          parentSum[(int) (s + v)] = s;
        }
      }
    }

    // procura a soma alcançável mais próxima do alvo dentro da janela de tolerância
    long bestSum = -1;
    long bestDiff = Long.MAX_VALUE;
    for (long s = minCents; s <= maxCents; s++) {
      if (reachable[(int) s]) {
        long diff = Math.abs(s - targetCents);
        if (diff < bestDiff) {
          bestDiff = diff;
          bestSum = s;
          if (diff == 0) break;
        }
      }
    }

    if (bestSum < 0) {
      return MatchResult.notMatched();
    }

    // reconstrói o subconjunto a partir de parentItem/parentSum
    List<T> selected = new ArrayList<>();
    long cursor = bestSum;
    while (cursor > 0) {
      int item = parentItem[(int) cursor];
      selected.add(candidates.get(item));
      cursor = parentSum[(int) cursor];
    }

    BigDecimal matchedValue = BigDecimal.valueOf(bestSum).movePointLeft(2);
    return MatchResult.matched(selected, matchedValue, false);
  }

  /**
   * Força bruta 2^N para grupos pequenos (N ≤ 20). Não aloca arrays proporcionais
   * ao valor-alvo, portanto funciona para qualquer montante — inclusive lançamentos
   * bancários de alto valor compostos por poucas ordens de crédito.
   */
  private <T> MatchResult bruteForceSubsetSum(
    List<T> candidates,
    ValueExtractor<T> extractor,
    BigDecimal targetValue,
    BigDecimal tolerance
  ) {
    int n = candidates.size();
    long targetCents = toCents(targetValue);
    long toleranceCents = toCents(tolerance);
    long minCents = Math.max(0, targetCents - toleranceCents);
    long maxCents = targetCents + toleranceCents;

    long[] valueCents = new long[n];
    for (int i = 0; i < n; i++) {
      valueCents[i] = toCents(extractor.value(candidates.get(i)));
    }

    long bestDiff = Long.MAX_VALUE;
    int bestMask = -1;
    long bestSum = 0;

    for (int mask = 1; mask < (1 << n); mask++) {
      long s = 0;
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) s += valueCents[i];
      }
      if (s >= minCents && s <= maxCents) {
        long diff = Math.abs(s - targetCents);
        if (diff < bestDiff) {
          bestDiff = diff;
          bestMask = mask;
          bestSum = s;
          if (diff == 0) break;
        }
      }
    }

    if (bestMask < 0) return MatchResult.notMatched();

    List<T> selected = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      if ((bestMask & (1 << i)) != 0) selected.add(candidates.get(i));
    }
    return MatchResult.matched(selected, BigDecimal.valueOf(bestSum).movePointLeft(2), false);
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