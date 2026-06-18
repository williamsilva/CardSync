package com.cardsync.domain.model.enums;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ModalityPaymentBankEnum {

  NULL(0, "NULL"),
  CASH_DEBIT(1, "CASH_DEBIT", Set.of(3819)),
  CASH_CREDIT(2, "CASH_CREDIT", Set.of(3818)),
  PIX_REC(3, "PIX_REC", Set.of(0, 3987, 3988, 3989)),
  PIX_DEV(4, "PIX_DEV", Set.of(4046, 4044, 4050)),
  PIX_ENV(5, "PIX_ENV", Set.of(3985, 3983)),
  TRF_PIX_ENV(6, "TRF_PIX_ENV", Set.of(4012, 74)),
  TRF_PIX_CHECK(7, "TRF_PIX_CHECK", Set.of(4099)),
  TED_REC(8, "TED_REC", Set.of(477)),
  PGTO_BOL(9, "PGTO_BOL", Set.of(2988, 688, 69, 662)),
  PGTO_SALA(10, "PGTO_SALA", Set.of(2036)),
  APL_CONT(11, "APL_CONT", Set.of(45, 3625)),
  IOF(12, "IOF", Set.of(36)),
  ANTECIP_CRED(13, "ANTECIP_CRED", Set.of(3907));

  private final Integer code;         // código oficial (persistido no banco)
  private final String description;
  private final Set<Integer> aliases; // códigos alternativos aceitos (só leitura do arquivo)

  ModalityPaymentBankEnum(Integer code, String description, Set<Integer> aliases) {
    this.code = code;
    this.description = description;
    this.aliases = aliases;
  }

  // construtor auxiliar para enums sem aliases
  ModalityPaymentBankEnum(Integer code, String description) {
    this(code, description, Set.of());
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, ModalityPaymentBankEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ModalityPaymentBankEnum::getCode, Function.identity()));

  private static final Map<String, ModalityPaymentBankEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static ModalityPaymentBankEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    ModalityPaymentBankEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ModalityPaymentBankEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte código do arquivo (código canônico ou alias) -> enum.
   * Retorna NULL para códigos desconhecidos (uso: parsing de arquivo CNAB).
   */
  public static ModalityPaymentBankEnum fromCodeOrAlias(Integer code) {

    if (code == null) {
      return NULL;
    }

    ModalityPaymentBankEnum byCode = BY_CODE.get(code);
    if (byCode != null) {
      return byCode;
    }

    for (ModalityPaymentBankEnum e : values()) {
      if (e.aliases.contains(code)) {
        return e;
      }
    }

    return NULL;
  }

  /*
   * Converte string -> enum
   */
  public static ModalityPaymentBankEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    ModalityPaymentBankEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ModalityPaymentBankEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(ModalityPaymentBankEnum status) {
    return status != null ? status.code : null;
  }

}