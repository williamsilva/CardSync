package com.cardsync.bff.controller.v1.representation.model.holiday;

import com.cardsync.domain.model.enums.StatusEnum;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HolidayModel(
  UUID id,
  LocalDate holidayDate,
  String name,
  StatusEnum status,
  OffsetDateTime statusDate,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {
}
