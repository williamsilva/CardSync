package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayInput(

  @NotNull
  LocalDate holidayDate,

  @NotBlank
  @Size(max = 150)
  String name,

  StatusEnum status,

  /** Quando true, o feriado se repete todo ano. O ano de holidayDate é ignorado. */
  Boolean recurring
) {
}
