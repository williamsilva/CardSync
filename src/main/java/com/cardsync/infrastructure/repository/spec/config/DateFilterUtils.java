package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.core.config.CardsyncAppProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

final class DateFilterUtils {

  private static ZoneId BUSINESS_ZONE;

  private DateFilterUtils(CardsyncAppProperties props) {
    BUSINESS_ZONE = props.getBusinessZone();
  }

  static OffsetDateTime parseFlexibleToOffsetDateTime(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
    }

    try {
      return Instant.parse(value).atOffset(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
    }

    try {
      LocalDate localDate = LocalDate.parse(value);
      return localDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  static OffsetDateTime startOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value.toInstant()
      .atZone(BUSINESS_ZONE)
      .toLocalDate()
      .atStartOfDay(BUSINESS_ZONE)
      .toOffsetDateTime();
  }

  static OffsetDateTime endOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value.toInstant()
      .atZone(BUSINESS_ZONE)
      .toLocalDate()
      .plusDays(1)
      .atStartOfDay(BUSINESS_ZONE)
      .minusNanos(1)
      .toOffsetDateTime();
  }
}
