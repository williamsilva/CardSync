package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;

import java.util.List;

public record CompanyFilter (
  Integer id,
  String cnpj,
  String fantasyName,
  String socialReason,
  String createdBy,

  String createdAtTo,
  String createdAtFrom,

  List<StatusEnum> statusEnum,
  List<TypeCompanyEnum>typeEnum
) {
}
