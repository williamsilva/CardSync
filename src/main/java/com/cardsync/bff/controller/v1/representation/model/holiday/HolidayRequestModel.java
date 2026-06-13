package com.cardsync.bff.controller.v1.representation.model.holiday;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayRequestModel(
  @NotNull LocalDate holidayDate,
  @NotBlank @Size(max = 150) String name,
  StatusEnum status
) {
}
