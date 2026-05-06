package com.cardsync.core.file.erp.dto;

import com.cardsync.core.file.config.FileProcessingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Component
public class TransactionErpCsvReader {
  private final FileProcessingProperties fileProcessingProperties;

  public TransactionErpCsvReader() {
    this.fileProcessingProperties = null;
  }

  @Autowired
  public TransactionErpCsvReader(FileProcessingProperties fileProcessingProperties) {
    this.fileProcessingProperties = fileProcessingProperties;
  }

  private static final Charset FILE_CHARSET = Charset.forName("Windows-1252");
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

  private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"),
    DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"),
    DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"),
    DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss"),
    DateTimeFormatter.ofPattern("dd-MM-yy HH:mm")
  );

  private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("dd/MM/uuuu"),
    DateTimeFormatter.ofPattern("dd/MM/yy"),
    DateTimeFormatter.ofPattern("dd-MM-uuuu"),
    DateTimeFormatter.ofPattern("dd-MM-yy")
  );

  public List<TransactionErpCsvDto> read(Path file) throws Exception {
    List<String> lines = Files.readAllLines(file, FILE_CHARSET);
    int headerIndex = findHeaderIndex(lines);
    if (headerIndex < 0) {
      log.warn("⚠ Cabeçalho ERP não encontrado no arquivo {}. O header precisa conter ao menos colunas de transação/data/valor.", file.getFileName());
      return List.of();
    }

    String delimiter = detectDelimiter(lines.get(headerIndex));
    List<String> headers = splitCsv(lines.get(headerIndex), delimiter);
    Map<String, Integer> headerMap = headerMap(headers);

    log.info("📘 ERP CSV detectado. arquivo={}, linhaCabecalho={}, delimitador='{}', colunas={}",
      file.getFileName(), headerIndex + 1, delimiter, headers);

    List<TransactionErpCsvDto> rows = new ArrayList<>();
    for (int i = headerIndex + 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null || line.isBlank()) continue;

      List<String> values = splitCsv(line, delimiter);
      if (isSkippableNonTransactionLine(line, values, headerMap)) {
        continue;
      }

      TransactionErpCsvDto dto = new TransactionErpCsvDto();
      dto.setLineNumber(i + 1);
      dto.setTransaction(valueAny(values, headerMap,
        "transacao", "tipo transacao", "tipo de transacao", "operacao", "tipo operacao", "situacao"));
      dto.setOrigin(valueAny(values, headerMap,
        "origem", "canal", "meio captura", "meio de captura", "captura", "tipo captura", "produto"));
      dto.setAcquirer(valueAny(values, headerMap,
        "adquirente", "operadora", "rede", "credenciadora", "administradora", "bandeira adquirente"));
      dto.setModality(valueAny(values, headerMap,
        "pagto", "pagamento", "forma pagamento", "forma de pagamento", "tipo pagamento", "modalidade", "produto"));
      dto.setFlag(valueAny(values, headerMap,
        "bandeira", "cartao bandeira", "bandeira cartao", "brand", "card brand"));
      dto.setInstallmentType(valueAny(values, headerMap,
        "parcelamento", "tipo parcelamento", "tipo de parcelamento", "plano"));
      dto.setInstallment(parseInteger(valueAny(values, headerMap,
        "parcelas", "qtd parcelas", "quantidade parcelas", "qtd. parcelas", "qtde parcelas", "numero parcelas", "n parcelas", "parcela")));
      dto.setCardNumber(valueAny(values, headerMap,
        "cartao", "cartao mascarado", "numero cartao", "numero do cartao", "pan mascarado"));
      dto.setCardName(valueAny(values, headerMap, "nome cartao", "nome do cartao"));
      dto.setNsu(parseLong(valueAny(values, headerMap,
        "nsu", "nsu host", "nsu tef", "nsu rede", "cv", "comprovante", "numero cv")));
      dto.setTid(valueAny(values, headerMap, "tid"));
      dto.setAuthorization(valueAny(values, headerMap,
        "autorizacao", "codigo autorizacao", "cod autorizacao", "cod. autorizacao", "auth", "authorization", "aut"));
      dto.setThreeDs(valueAny(values, headerMap, "3ds"));
      dto.setAntiFraud(valueAny(values, headerMap, "antifraude", "anti fraude"));
      dto.setGrossValue(parseBigDecimal(valueAny(values, headerMap,
        "valor", "valor bruto", "valor venda", "valor da venda", "valor transacao", "valor da transacao", "bruto")));
      dto.setSaleDate(parseOffsetDateTime(valueAny(values, headerMap,
        "data", "data venda", "data da venda", "data transacao", "data da transacao", "dt venda", "dt transacao", "data/hora", "data hora", "data movimento")));

      dto.setCompanyCnpj(onlyDigits(valueAny(values, headerMap,
        "cnpj empresa", "cnpj", "cpf/cnpj", "documento empresa", "documento", "cnpj loja", "cnpj estabelecimento", "cpf cnpj", "cpf/cnpj empresa")));
      dto.setCompanyName(valueAny(values, headerMap,
        "empresa", "nome empresa", "razao social", "razao social empresa", "fantasia empresa", "nome fantasia", "empresa fantasia", "grupo comercial"));
      dto.setEstablishmentPvNumber(parseInteger(valueAny(values, headerMap,
        "pv", "numero pv", "n pv", "nº pv", "codigo pv", "cod pv", "cod. pv", "codigo estabelecimento", "cod estabelecimento", "cod. estabelecimento", "numero estabelecimento", "estabelecimento pv", "ponto de venda", "loja", "codigo loja", "cod loja", "unidade")));
      dto.setEstablishmentName(valueAny(values, headerMap,
        "nome estabelecimento", "estabelecimento", "nome loja", "loja", "ponto venda", "ponto de venda", "nome pv", "fantasia estabelecimento", "nome unidade", "unidade"));
      dto.setMachine(valueAny(values, headerMap,
        "maquina", "terminal", "pdv", "pos", "tef", "equipamento", "ec", "codigo ec", "cod ec"));

      applyDefaults(dto);
      rows.add(dto);
    }
    return rows;
  }

  private void applyDefaults(TransactionErpCsvDto dto) {
    if (dto == null || fileProcessingProperties == null || fileProcessingProperties.getErp() == null) {
      return;
    }

    FileProcessingProperties.Erp erp = fileProcessingProperties.getErp();

    if (dto.getEstablishmentPvNumber() == null) {
      dto.setEstablishmentPvNumber(erp.getDefaultPvNumber());
    }
    if (isBlank(dto.getCompanyCnpj())) {
      dto.setCompanyCnpj(onlyDigits(erp.getDefaultCompanyCnpj()));
    }
    if (isBlank(dto.getCompanyName())) {
      dto.setCompanyName(trimToNull(erp.getDefaultCompanyName()));
    }
    if (isBlank(dto.getEstablishmentName())) {
      dto.setEstablishmentName(trimToNull(erp.getDefaultEstablishmentName()));
    }
  }

  public int countPhysicalLines(Path file) throws Exception {
    try (var stream = Files.lines(file, FILE_CHARSET)) {
      return (int) stream.count();
    }
  }

  private int findHeaderIndex(List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String normalized = normalize(lines.get(i));
      boolean hasTransaction = normalized.contains("transacao")
        || normalized.contains("operacao")
        || normalized.contains("pagamento");
      boolean hasAmount = normalized.contains("valor")
        || normalized.contains("bruto");
      boolean hasDate = normalized.contains("data")
        || normalized.contains("dt ");
      boolean hasAcquirerOrFlag = normalized.contains("adquirente")
        || normalized.contains("credenciadora")
        || normalized.contains("operadora")
        || normalized.contains("bandeira");

      if (hasAmount && hasDate && (hasTransaction || hasAcquirerOrFlag)) return i;
    }
    return -1;
  }

  private boolean isSkippableNonTransactionLine(String line, List<String> values, Map<String, Integer> headerMap) {
    String normalizedLine = normalize(line);

    if (values == null || values.isEmpty()) {
      return true;
    }

    long nonBlankValues = values.stream()
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(v -> !v.isBlank())
      .count();

    if (nonBlankValues == 0) {
      return true;
    }

    // Rodapes/resumos reais do relatorio TEF: "862 transacoes", "1.464 transacoes" etc.
    if (nonBlankValues == 1 && normalizedLine.matches(".*\\b[0-9.]+\\s+transacoes\\b.*")) {
      return true;
    }

    // Algumas exportacoes colocam linhas de resumo/titulo no meio/fim do arquivo.
    if (normalizedLine.matches(".*\\b(total|subtotal|resumo|transacoes)\\b.*")
      && !looksLikeTransactionDataLine(values, headerMap)) {
      return true;
    }

    // Se a linha tem so uma coluna, ela nao e uma transacao tabular valida.
    if (nonBlankValues == 1 && values.size() == 1) {
      return true;
    }

    return false;
  }

  private boolean looksLikeTransactionDataLine(List<String> values, Map<String, Integer> headerMap) {
    String dateValue = valueAny(values, headerMap, "data", "data venda", "data da venda", "data transacao", "dt venda", "data/hora", "data hora");
    String amountValue = valueAny(values, headerMap, "valor", "valor bruto", "valor venda", "valor transacao", "bruto");
    String acquirerValue = valueAny(values, headerMap, "adquirente", "operadora", "rede", "credenciadora", "administradora");

    return hasDateShape(dateValue) || hasMoneyShape(amountValue) || (acquirerValue != null && !acquirerValue.isBlank());
  }

  private boolean hasDateShape(String value) {
    if (value == null || value.isBlank()) return false;
    String text = value.trim();
    return text.matches("\\d{2}/\\d{2}/\\d{2,4}(\\s+\\d{2}:\\d{2}(:\\d{2})?)?")
      || text.matches("\\d{2}-\\d{2}-\\d{2,4}(\\s+\\d{2}:\\d{2}(:\\d{2})?)?")
      || text.matches("\\d{4}-\\d{2}-\\d{2}.*");
  }

  private boolean hasMoneyShape(String value) {
    if (value == null || value.isBlank()) return false;
    String text = value.replace("R$", "").trim();
    return text.matches("-?\\d{1,3}(\\.\\d{3})*,\\d{2}")
      || text.matches("-?\\d+,\\d{2}")
      || text.matches("-?\\d+(\\.\\d+)?");
  }

  private String detectDelimiter(String header) {
    long semicolons = header.chars().filter(ch -> ch == ';').count();
    long commas = header.chars().filter(ch -> ch == ',').count();
    return semicolons >= commas ? ";" : ",";
  }

  private Map<String, Integer> headerMap(List<String> headers) {
    Map<String, Integer> result = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      result.put(normalize(headers.get(i)), i);
      result.put(compactNormalize(headers.get(i)), i);
    }
    return result;
  }

  private String valueAny(List<String> values, Map<String, Integer> headerMap, String... keys) {
    for (String key : keys) {
      String found = value(values, headerMap, key);
      if (found != null) return found;
    }
    return null;
  }

  private String value(List<String> values, Map<String, Integer> headerMap, String key) {
    Integer index = headerMap.get(normalize(key));
    if (index == null) index = headerMap.get(compactNormalize(key));
    if (index == null || index >= values.size()) return null;
    String value = values.get(index);
    return value == null || value.isBlank() ? null : value.trim();
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

  private BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO;
    String normalized = value.replace("R$", "").replace(" ", "").trim();
    normalized = normalized.replaceAll("[^0-9,.-]", "");
    if (normalized.isBlank() || "-".equals(normalized)) return BigDecimal.ZERO;
    if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
    return new BigDecimal(normalized);
  }

  private Integer parseInteger(String value) {
    if (value == null || value.isBlank()) return null;
    String onlyDigits = value.replaceAll("[^0-9-]", "");
    return onlyDigits.isBlank() ? null : Integer.valueOf(onlyDigits);
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) return null;
    String onlyDigits = value.replaceAll("[^0-9]", "");
    return onlyDigits.isBlank() ? null : Long.valueOf(onlyDigits);
  }

  private String onlyDigits(String value) {
    if (value == null || value.isBlank()) return null;
    String onlyDigits = value.replaceAll("[^0-9]", "");
    return onlyDigits.isBlank() ? null : onlyDigits;
  }

  private OffsetDateTime parseOffsetDateTime(String value) {
    if (value == null || value.isBlank()) return null;

    String text = normalizeTwoDigitYear(value.trim());
    if (text.equals("00000000") || text.equals("000000") || text.matches("0+")) {
      return null;
    }

    for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
      try {
        return LocalDateTime.parse(text, formatter)
          .atZone(DEFAULT_ZONE)
          .toOffsetDateTime();
      } catch (DateTimeParseException ignored) { }
    }

    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
      try {
        return LocalDate.parse(text, formatter)
          .atStartOfDay(DEFAULT_ZONE)
          .toOffsetDateTime();
      } catch (DateTimeParseException ignored) { }
    }

    try {
      return OffsetDateTime.parse(text);
    } catch (DateTimeParseException ignored) { }

    try {
      return LocalDateTime.parse(text)
        .atZone(DEFAULT_ZONE)
        .toOffsetDateTime();
    } catch (DateTimeParseException ignored) {
      throw new IllegalArgumentException("Data/hora ERP inválida: " + value);
    }
  }

  private String normalizeTwoDigitYear(String text) {
    if (text == null) return null;

    // Ex.: 01/12/25 23:56 -> 01/12/2025 23:56
    if (text.matches("\\d{2}/\\d{2}/\\d{2}(\\s+.*)?")) {
      return text.substring(0, 6) + "20" + text.substring(6);
    }

    // Ex.: 01-12-25 23:56 -> 01-12-2025 23:56
    if (text.matches("\\d{2}-\\d{2}-\\d{2}(\\s+.*)?")) {
      return text.substring(0, 6) + "20" + text.substring(6);
    }

    return text;
  }


  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private String compactNormalize(String value) {
    return normalize(value).replaceAll("[^a-z0-9]", "");
  }

  private String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value.replace("\uFEFF", "").trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
      .toLowerCase(Locale.ROOT);
  }
}
