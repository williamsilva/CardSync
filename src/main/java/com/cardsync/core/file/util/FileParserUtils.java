package com.cardsync.core.file.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

@Slf4j
public final class FileParserUtils {
  private FileParserUtils() {}

  private static final ZoneId SOURCE_FILE_ZONE = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter DATE_FORMAT_6 = DateTimeFormatter.ofPattern("ddMMyy");
  private static final DateTimeFormatter DATE_FORMAT_8 = DateTimeFormatter.ofPattern("ddMMyyyy");
  private static final DateTimeFormatter DATE_FORMAT_8_YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

  public static Integer extractIntegerLine(String line, String range, int lineNumber) {
    return extractValueLine(line, range, lineNumber, value -> value.isBlank() ? null : Integer.parseInt(value));
  }

  public static Long extractLongLine(String line, String range, int lineNumber) {
    return extractValueLine(line, range, lineNumber, value -> value.isBlank() ? null : Long.parseLong(value));
  }

  public static String extractStringLine(String line, String range, int lineNumber) {
    return extractValueLine(line, range, lineNumber, Function.identity());
  }

  public static LocalDate extractDateLine(String line, String range, int lineNumber) {
    return extractValueLine(line, range, lineNumber, FileParserUtils::parseDate);
  }

  public static BigDecimal extractBigDecimalLine(String line, String range, int lineNumber) {
    BigDecimal value = extractValueLine(line, range, lineNumber, FileParserUtils::parseMoneyInCents);
    return value == null ? BigDecimal.ZERO : value;
  }

  /**
   * Para layouts (ex.: Cielo Extrato Eletrônico) que trazem o sinal num campo de 1 caractere
   * separado, imediatamente antes dos dígitos do valor (ex.: "+0000000024580"), em vez de um "-"
   * embutido no próprio número como parseMoneyInCents espera. O range deve cobrir sinal + valor
   * juntos (o sinal é sempre o primeiro caractere do range).
   */
  public static BigDecimal extractSignedMoneyLine(String line, String range, int lineNumber) {
    BigDecimal value = extractValueLine(line, range, lineNumber, FileParserUtils::parseSignedMoneyInCents);
    return value == null ? BigDecimal.ZERO : value;
  }

  public static OffsetDateTime extractOffsetDateTimeLine(String line, int lineNumber, String rangeDate, String rangeTime) {
    String date = extractStringLine(line, rangeDate, lineNumber);
    String time = extractStringLine(line, rangeTime, lineNumber);
    if (date == null || time == null || date.isBlank() || time.isBlank()) return null;

    LocalDate parsedDate = parseDate(date);
    if (parsedDate == null) return null;

    String cleanTime = time.trim();
    if (cleanTime.matches("0+") && cleanTime.length() > 6) return null;

    String pattern = cleanTime.length() == 4 ? "HHmm" : "HHmmss";
    LocalTime parsedTime = LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern(pattern));
    return LocalDateTime.of(parsedDate, parsedTime)
      .atZone(SOURCE_FILE_ZONE)
      .toOffsetDateTime()
      .withOffsetSameInstant(ZoneOffset.UTC);
  }

  private static <T> T extractValueLine(String line, String range, int lineNumber, Function<String, T> parser) {
    if (line == null || line.isBlank()) return null;
    int[] positions = parseRangeLine(range);
    if (positions == null) {
      log.warn("[Linha {}] Range inválido '{}'; ignorando campo.", lineNumber, range);
      return null;
    }
    int start = positions[0];
    int end = positions[1];
    if (start < 0 || end < start) {
      log.warn("[Linha {}] Range inconsistente '{}' (start={}, end={}); ignorando campo.", lineNumber, range, start, end);
      return null;
    }
    if (start >= line.length()) return null;
    if (end > line.length()) {
      log.debug("[Linha {}] Range {} ultrapassa tamanho da linha (len={}); ajustando para fim.", lineNumber, range, line.length());
      end = line.length();
    }
    String raw = line.substring(start, end).trim();
    try {
      return parser.apply(raw);
    } catch (Exception ex) {
      log.debug("⚠ [Linha {} range {}] Não foi possível converter '{}'.", lineNumber, range, raw);
      return null;
    }
  }

  private static BigDecimal parseMoneyInCents(String raw) {
    if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
    String normalized = raw.trim().replace(",", ".");
    if (normalized.matches("-?\\d+")) return new BigDecimal(normalized).movePointLeft(2);
    return new BigDecimal(normalized);
  }

  private static BigDecimal parseSignedMoneyInCents(String raw) {
    if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
    String trimmed = raw.trim();
    char sign = trimmed.charAt(0);
    BigDecimal value = parseMoneyInCents(trimmed.substring(1));
    return sign == '-' ? value.negate() : value;
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim();

    // Layouts Rede costumam usar 00000000/000000 para campos de data sem valor.
    // Isso não deve derrubar o processamento do arquivo.
    if (value.equals("00000000") || value.equals("000000") || value.matches("0+")) {
      return null;
    }

    try {
      if (value.length() == 6) return LocalDate.parse(value, DATE_FORMAT_6);
      if (value.length() == 8) {
        try {
          return LocalDate.parse(value, DATE_FORMAT_8);
        } catch (DateTimeParseException ignored) {
          return LocalDate.parse(value, DATE_FORMAT_8_YMD);
        }
      }
    } catch (DateTimeParseException ex) {
      throw new DateTimeParseException("Data inválida", value, 0, ex);
    }

    throw new DateTimeParseException("Formato de data inválido", value, 0);
  }

  private static int[] parseRangeLine(String range) {
    if (range == null) return null;
    String[] parts = range.split("-");
    if (parts.length != 2) return null;
    try {
      return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}