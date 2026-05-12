package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.core.config.CardsyncAppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class DateFilterService {

  private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter BR_DATE_SHORT = DateTimeFormatter.ofPattern("d/M/yyyy");

  private final ZoneId businessZone;

  public DateFilterService(CardsyncAppProperties props) {
    this.businessZone = props.getBusinessZone();
  }

  public OffsetDateTime parseFlexibleToOffsetDateTime(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    try {
      return toUtc(OffsetDateTime.parse(value));
    } catch (DateTimeParseException ignored) {
    }

    try {
      return Instant.parse(value).atOffset(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
    }

    try {
      LocalDate localDate = LocalDate.parse(value, BR_DATE);
      return startOfBusinessDay(localDate);
    } catch (DateTimeParseException ignored) {
    }

    try {
      LocalDate localDate = LocalDate.parse(value, BR_DATE_SHORT);
      return startOfBusinessDay(localDate);
    } catch (DateTimeParseException ignored) {
    }

    try {
      LocalDate localDate = LocalDate.parse(value);
      return startOfBusinessDay(localDate);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  public OffsetDateTime startOfBusinessDay(LocalDate value) {
    if (value == null) {
      return null;
    }

    return value
      .atStartOfDay(businessZone)
      .toOffsetDateTime()
      .withOffsetSameInstant(ZoneOffset.UTC);
  }

  public OffsetDateTime endOfBusinessDay(LocalDate value) {
    if (value == null) {
      return null;
    }

    return value
      .plusDays(1)
      .atStartOfDay(businessZone)
      .minusNanos(1)
      .toOffsetDateTime()
      .withOffsetSameInstant(ZoneOffset.UTC);
  }

  public OffsetDateTime startOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value
      .toInstant()
      .atZone(businessZone)
      .toLocalDate()
      .atStartOfDay(businessZone)
      .toOffsetDateTime()
      .withOffsetSameInstant(ZoneOffset.UTC);
  }

  public OffsetDateTime endOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value
      .toInstant()
      .atZone(businessZone)
      .toLocalDate()
      .plusDays(1)
      .atStartOfDay(businessZone)
      .minusNanos(1)
      .toOffsetDateTime()
      .withOffsetSameInstant(ZoneOffset.UTC);
  }

  public ZoneId businessZone() {
    return businessZone;
  }

  private OffsetDateTime toUtc(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}