package com.cardsync.core.file.acquirerreport.dto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o "Relatório de Pagamentos" da adquirente (ex.: Itaú/Rede — arquivo
 * "pagamentos-XXXXXXXXXXXXXXXXXXX.csv"), usado na importação em lote de ordens de crédito
 * manuais (ver CreditOrderManualService#importFromAcquirerReport).
 *
 * Layout confirmado no arquivo de exemplo (30 colunas, ';' como delimitador, Windows-1252):
 * 1=data do recebimento, 3=data original de vencimento, 4=valor bruto da parcela original,
 * 7=valor MDR descontado, 8=valor líquido da parcela, 15=resumo de vendas/número do lote,
 * 17=estabelecimento (PV), 23=número de parcelas, 24=parcela, 30=status. Acesso por posição
 * (não por nome de cabeçalho) porque é um formato único e fixo desta adquirente — a linha 0 é
 * sempre o cabeçalho e é descartada por não ter um RV numérico válido na coluna 15.
 */
@Slf4j
@Component
public class AcquirerPaymentReportCsvReader {

  private static final Charset FILE_CHARSET = Charset.forName("Windows-1252");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

  private static final int COL_RELEASE_DATE = 0;
  private static final int COL_ORIGINAL_DUE_DATE = 2;
  private static final int COL_GROSS_VALUE = 3;
  private static final int COL_DISCOUNT_VALUE = 6;
  private static final int COL_RELEASE_VALUE = 7;
  private static final int COL_RV_NUMBER = 14;
  private static final int COL_PV_NUMBER = 16;
  private static final int COL_INSTALLMENT_TOTAL = 22;
  private static final int COL_INSTALLMENT_NUMBER = 23;
  private static final int COL_STATUS = 29;

  public List<AcquirerPaymentReportRow> read(MultipartFile file) throws IOException {
    List<String> lines;
    try (InputStream in = file.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(in, FILE_CHARSET))) {
      lines = reader.lines().toList();
    }

    String delimiter = lines.isEmpty() ? ";" : detectDelimiter(lines.get(0));
    String fileName = file.getOriginalFilename();

    List<AcquirerPaymentReportRow> rows = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null || line.isBlank()) continue;

      List<String> values = splitCsv(line, delimiter);
      Integer rvNumber = parseInteger(valueAt(values, COL_RV_NUMBER));
      // Descarta a linha de cabeçalho (e qualquer rodapé/resumo) — nenhuma linha de dados
      // real fica sem RV numérico na coluna do lote.
      if (rvNumber == null) {
        continue;
      }

      rows.add(new AcquirerPaymentReportRow(
        fileName,
        i + 1,
        rvNumber,
        parseInteger(valueAt(values, COL_PV_NUMBER)),
        parseInteger(valueAt(values, COL_INSTALLMENT_NUMBER)),
        parseInteger(valueAt(values, COL_INSTALLMENT_TOTAL)),
        parseLocalDate(valueAt(values, COL_RELEASE_DATE)),
        parseLocalDate(valueAt(values, COL_ORIGINAL_DUE_DATE)),
        parseBigDecimal(valueAt(values, COL_RELEASE_VALUE)),
        parseBigDecimal(valueAt(values, COL_GROSS_VALUE)),
        parseBigDecimal(valueAt(values, COL_DISCOUNT_VALUE)),
        valueAt(values, COL_STATUS)
      ));
    }

    log.info("📘 Relatório de pagamentos da adquirente lido: arquivo={}, linhasDados={}",
      fileName, rows.size());

    return rows;
  }

  private String detectDelimiter(String header) {
    long semicolons = header.chars().filter(ch -> ch == ';').count();
    long commas = header.chars().filter(ch -> ch == ',').count();
    return semicolons >= commas ? ";" : ",";
  }

  private List<String> splitCsv(String line, String delimiter) {
    List<String> values = new ArrayList<>();
    char sep = delimiter.charAt(0);
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (c == sep && !quoted) {
        values.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    values.add(current.toString().trim());
    return values;
  }

  private String valueAt(List<String> values, int index) {
    if (index < 0 || index >= values.size()) return null;
    String value = values.get(index);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private Integer parseInteger(String value) {
    if (value == null || value.isBlank()) return null;
    String onlyDigits = value.replaceAll("[^0-9-]", "");
    if (onlyDigits.isBlank()) return null;
    try {
      return Integer.valueOf(onlyDigits);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.replace("R$", "").replace(" ", "").trim();
    normalized = normalized.replaceAll("[^0-9,.-]", "");
    if (normalized.isBlank() || "-".equals(normalized)) return null;
    if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
    try {
      return new BigDecimal(normalized);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private LocalDate parseLocalDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
