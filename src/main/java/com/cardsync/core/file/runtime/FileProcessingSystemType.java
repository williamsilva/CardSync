package com.cardsync.core.file.runtime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileProcessingSystemType {
  ERP("ERP", "Arquivos ERP"),
  ACQUIRER("ACQUIRER", "Arquivos de adquirentes"),
  BANK("BANK", "Arquivos bancários");

  private final String code;
  private final String description;
}
