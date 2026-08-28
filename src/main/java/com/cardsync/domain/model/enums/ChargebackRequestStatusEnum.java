package com.cardsync.domain.model.enums;

import lombok.Getter;

/**
 * Status do request de chargeback (decisão da empresa sobre a solicitação da adquirente).
 * Espelha 1:1 o enum TypeScript equivalente (STATUS_CODE_MAP em
 * chargeback-request-status.enum.ts) — sem códigos legados, ao contrário de
 * {@link ChargebackRequestReasonEnum}.
 */
@Getter
public enum ChargebackRequestStatusEnum {
  AGUARDANDO_DECISAO(1),
  PENDENTE(2),
  APROVADO(3),
  REPROVADO(4),
  CANCELADO(5);

  private final int code;

  ChargebackRequestStatusEnum(int code) {
    this.code = code;
  }
}
