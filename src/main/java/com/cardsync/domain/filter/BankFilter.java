package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;

import java.util.List;
import java.util.UUID;

public record BankFilter(
  UUID id,
  String code,
  String name,
  String ispb,
  List<StatusEnum> statusEnum
) {
}