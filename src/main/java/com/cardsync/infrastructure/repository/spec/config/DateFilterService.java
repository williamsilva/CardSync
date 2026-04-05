package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.core.config.CardsyncAppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Component
public class DateFilterService {

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
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
    }

    try {
      return Instant.parse(value).atOffset(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
    }

    try {
      LocalDate localDate = LocalDate.parse(value);
      return localDate.atStartOfDay(businessZone).toOffsetDateTime();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  public OffsetDateTime startOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value.toInstant()
      .atZone(businessZone)
      .toLocalDate()
      .atStartOfDay(businessZone)
      .toOffsetDateTime();
  }

  public OffsetDateTime endOfBusinessDay(OffsetDateTime value) {
    if (value == null) {
      return null;
    }

    return value.toInstant()
      .atZone(businessZone)
      .toLocalDate()
      .plusDays(1)
      .atStartOfDay(businessZone)
      .minusNanos(1)
      .toOffsetDateTime();
  }

  public ZoneId businessZone() {
    return businessZone;
  }
}