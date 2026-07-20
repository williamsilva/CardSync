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

  /**
   * Notas de classificação (ver BankStatementClassification.getNotes(), ex.:
   * "pv_candidates_without_establishment", "flag_not_detected_by_text") — antes calculadas em
   * BankStatementClassifierService.classify() e descartadas silenciosamente após o retorno.
   * Agrupadas por texto da nota, mesmo padrão de agregação de summaries acima, para dar
   * visibilidade por arquivo/banco à confiabilidade da extração de estabelecimento/bandeira
   * a partir do CNAB — usado no diagnóstico de impacto do modo estrito de conciliação bancária.
   */
  private final Map<String, NoteSummary> classificationNotes = new LinkedHashMap<>();

  public void monetaryOverflow(int lineNumber, String context, String field, String range, BigDecimal value) {
    String key = context + "." + field + "|" + range;
    summaries.computeIfAbsent(key, ignored -> new WarningSummary(context, field, range))
      .add(lineNumber, value);
  }

  public void classificationNote(int lineNumber, String note) {
    classificationNotes.computeIfAbsent(note, NoteSummary::new).add(lineNumber);
  }

  public int count() {
    return summaries.values().stream().mapToInt(WarningSummary::count).sum()
      + classificationNotes.values().stream().mapToInt(NoteSummary::count).sum();
  }

  public boolean hasWarnings() {
    return count() > 0;
  }

  public String summary() {
    if (summaries.isEmpty() && classificationNotes.isEmpty()) return "";
    return java.util.stream.Stream.concat(
        summaries.values().stream().map(WarningSummary::toLogMessage),
        classificationNotes.values().stream().map(NoteSummary::toLogMessage)
      )
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

  private static final class NoteSummary {
    private final String note;
    private final List<Integer> lines = new ArrayList<>();

    private NoteSummary(String note) {
      this.note = note;
    }

    private void add(int lineNumber) {
      lines.add(lineNumber);
    }

    private int count() {
      return lines.size();
    }

    private String toLogMessage() {
      return note
        + "[qtd=" + count()
        + ", primeiraLinha=" + lines.get(0)
        + ", ultimaLinha=" + lines.get(lines.size() - 1)
        + "]";
    }
  }
}
