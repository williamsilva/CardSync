package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HolidayFilter(
  UUID id,
  String name,
  LocalDate holidayDate,
  Boolean recurring,
  List<StatusEnum> statusEnum
) {
}