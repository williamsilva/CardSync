package com.cardsync.core.file.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrupa avisos esperados de parsing CNAB para evitar WARN linha a linha no console.
 * Os detalhes ainda podem ser gravados no ProcessedFileErrorEntity pelo serviço chamador.
 */
public class CnabProcessingWarningCollector {

  private final Map<String, WarningSummary> summaries = new LinkedHashMap<>();

  public void monetaryOverflow(int lineNumber, String context, String field, String range, BigDecimal value) {
    String key = context + "." + field + "|" + range;
    summaries.computeIfAbsent(key, ignored -> new WarningSummary(context, field, range))
      .add(lineNumber, value);
  }

  public int count() {
    return summaries.values().stream().mapToInt(WarningSummary::count).sum();
  }

  public boolean hasWarnings() {
    return count() > 0;
  }

  public String summary() {
    if (summaries.isEmpty()) return "";
    return summaries.values().stream()
      .map(WarningSummary::toLogMessage)
      .collect(Collectors.joining("; "));
  }

  private static final class WarningSummary {
    private final String context;
    private final String field;
    private final String range;
    private final List<Integer> lines = new ArrayList<>();
    private BigDecimal firstValue;

    private WarningSummary(String context, String field, String range) {
      this.context = context;
      this.field = field;
      this.range = range;
    }

    private void add(int lineNumber, BigDecimal value) {
      if (firstValue == null) firstValue = value;
      lines.add(lineNumber);
    }

    private int count() {
      return lines.size();
    }

    private String toLogMessage() {
      int firstLine = lines.get(0);
      int lastLine = lines.get(lines.size() - 1);
      return context + "." + field
        + "[range=" + range
        + ", qtd=" + count()
        + ", primeiraLinha=" + firstLine
        + ", ultimaLinha=" + lastLine
        + ", primeiroValor=" + firstValue
        + "]";
    }
  }
}
