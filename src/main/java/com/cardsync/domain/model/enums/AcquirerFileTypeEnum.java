package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcquirerFileTypeEnum {

  GENERIC("Arquivo diário"),
  EEVC("EEVC - Extrato de movimento de vendas"),
  EEVD("EEVD - Movimentação diária de cartões de débito"),
  EEFI("EEFI - Extrato de movimentação financeira");

  private final String description;
}
