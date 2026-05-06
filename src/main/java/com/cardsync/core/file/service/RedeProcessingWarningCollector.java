package com.cardsync.core.file.service;

import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.ProcessedFileErrorEntity;
import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrupa avisos esperados de layout durante parsing Rede para evitar WARN linha a linha no console.
 * Os detalhes continuam sendo gravados no ProcessedFileErrorEntity para consulta na tela de detalhe.
 */
final class RedeProcessingWarningCollector {
  private final String parserName;
  private final Map<String, WarningStat> monetaryWarnings = new LinkedHashMap<>();

  RedeProcessingWarningCollector(String parserName) {
    this.parserName = parserName;
  }

  void monetaryOutOfLimit(int lineNumber, String field, String context, String range, BigDecimal value) {
    String normalizedField = blankToDefault(field, "money");
    String normalizedContext = blankToDefault(context, "default");
    String normalizedRange = blankToDefault(range, "n/a");
    String key = normalizedContext + "|" + normalizedField + "|" + normalizedRange;

    monetaryWarnings
      .computeIfAbsent(key, ignored -> new WarningStat(normalizedContext, normalizedField, normalizedRange))
      .add(lineNumber, value);
  }

  boolean hasWarnings() {
    return !monetaryWarnings.isEmpty();
  }

  int totalWarnings() {
    return monetaryWarnings.values().stream().mapToInt(WarningStat::count).sum();
  }

  String compactSummary() {
    if (!hasWarnings()) return "";
    return monetaryWarnings.values().stream()
      .map(WarningStat::summary)
      .collect(Collectors.joining("; "));
  }

  void addProcessedFileErrors(ProcessedFileEntity processedFile, String codePrefix) {
    if (processedFile == null || !hasWarnings()) return;

    for (WarningStat stat : monetaryWarnings.values()) {
      processedFile.addError(ProcessedFileErrorEntity.of(
        stat.firstLine,
        ProcessedFileErrorTypeEnum.VALIDATION,
        codePrefix,
        parserName + " valor monetário fora do limite ignorado. campo=" + stat.field
          + ", contexto=" + stat.context
          + ", range=" + stat.range
          + ", ocorrencias=" + stat.count
          + ", primeiraLinha=" + stat.firstLine
          + ", ultimoValor=" + stat.lastValue,
        null
      ));
    }
  }

  void logSummary(Logger log, String fileName) {
    if (!hasWarnings()) return;
    log.warn("⚠ {} {} finalizado com {} aviso(s) de layout monetário ignorado(s): {}",
      parserName, fileName, totalWarnings(), compactSummary());
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static final class WarningStat {
    private final String context;
    private final String field;
    private final String range;
    private int count;
    private int firstLine;
    private int lastLine;
    private BigDecimal lastValue;

    private WarningStat(String context, String field, String range) {
      this.context = context;
      this.field = field;
      this.range = range;
    }

    private void add(int lineNumber, BigDecimal value) {
      if (count == 0) firstLine = lineNumber;
      count++;
      lastLine = lineNumber;
      lastValue = value;
    }

    private int count() {
      return count;
    }

    private String summary() {
      return context + "." + field + "[range=" + range + ", qtd=" + count
        + ", primeiraLinha=" + firstLine + ", ultimaLinha=" + lastLine + "]";
    }
  }
}
