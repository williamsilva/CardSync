package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Getter
@AllArgsConstructor
public enum AdjustmentReasonEnum {

  NULL(0),
  SALES_ANTICIPATION(1),
  TX_MAN_TEF(5),
  CANCEL_CHBK_MAESTRO(9),
  TARIFA_CBK(15),
  CANCEL_VENDAS(18),
  POS_INATIV_CONEC_PIN(20),
  SALE_DISPUTE(22),
  CHARGEBACK(23),
  TRF_AD_EXCESSO_CBACK(24),
  AL_POS_PINPAD_TX_CONECT(28),
  NAO_TOKENIZADAS(29),
  CANCEL_VENDA_DEBITO(32);

  private final int code;

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, AdjustmentReasonEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(AdjustmentReasonEnum::getCode, Function.identity()));

  private static final Map<String, AdjustmentReasonEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum.
   *
   * Os arquivos da Rede (tabela III) possuem dezenas de códigos de motivo; este
   * enum mapeia apenas os relevantes para a conciliação. Códigos não mapeados
   * NÃO devem derrubar o processamento do arquivo — o código bruto é preservado
   * separadamente (ex.: rawAdjustmentCode / adjustmentReason2) e a classificação
   * de chargeback recai sobre a descrição. Por isso, código desconhecido retorna
   * NULL em vez de lançar exceção.
   */
  public static AdjustmentReasonEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    AdjustmentReasonEnum value = BY_CODE.get(code);

    if (value == null) {
      log.debug("Código de AdjustmentReasonEnum não mapeado: {}; tratando como NULL.", code);
      return NULL;
    }

    return value;
  }

  /*
   * Converte string -> enum
   */
  public static AdjustmentReasonEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    AdjustmentReasonEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid AdjustmentReasonEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(AdjustmentReasonEnum status) {
    return status != null ? status.code : null;
  }
}