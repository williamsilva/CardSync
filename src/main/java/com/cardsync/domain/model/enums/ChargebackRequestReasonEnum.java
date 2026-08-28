package com.cardsync.domain.model.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Motivo do request de chargeback (Rede) — cada valor agrupa vários códigos brutos de
 * {@code cs_rede_request_notice.request_code}, do jeito que a adquirente realmente os envia no
 * arquivo (ver dados reais: request_code 3001/5001/3034/3053 são os que aparecem na base, não os
 * códigos-base 1-9).
 *
 * <p>Espelha, código por código, o {@code STATUS_CODE_MAP} de
 * {@code chargeback-request-reason.enum.ts} no frontend — mudou lá, muda aqui também. Usado só
 * pra expandir o(s) motivo(s) selecionado(s) no filtro numa lista de request_code reais antes do
 * IN; a exibição continua sendo feita no frontend (que já faz a mesma normalização a partir do
 * dado bruto devolvido pela API).
 */
public enum ChargebackRequestReasonEnum {
  DOCUMENTATION_REQUEST(1, 17, 21, 41, 42, 50, 97, 3001, 3002, 5001, 5060),
  DUPLICATE_TRANSACTION(2),
  FRAUD(3),
  PRODUCT_NOT_RECEIVED(4, 3055, 5030, 6355),
  CREDIT_NOT_PROCESSED(5),
  DEFECTIVE_PRODUCT(6, 3053, 5053, 8202, 8502),
  /** Sem código-base 1-9 no frontend — só existe via os códigos legados abaixo. */
  SERVICE_NOT_PROVIDED(3059, 5130),
  TRANSACTION_CANCELLATION(7, 3060, 5085),
  DUPLICATE_REQUEST(8, 5082, 3034),
  OTHER(9);

  private final Set<Integer> codes;

  ChargebackRequestReasonEnum(int... rawCodes) {
    this.codes = Arrays.stream(rawCodes).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Set<Integer> getCodes() {
    return codes;
  }
}
