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
public enum ReleaseCategoryEnum {

  NULL(0, "NULL"),
  PIX(1, "PIX", Set.of(103)),
  PAYMENT(2, "PAYMENT", Set.of(104, 113, 125, 117, 120, 222)),
  RECEIPT(3, "RECEIPT", Set.of(205, 209, 213)),
  TF_PIX(4, "TF_PIX", Set.of(105)),
  APL_AUT(5, "APL_AUT", Set.of(106));

  private final Integer code;         // código oficial (persistido no banco)
  private final String description;
  private final Set<Integer> aliases; // códigos alternativos aceitos (só leitura do arquivo)

  ReleaseCategoryEnum(Integer code, String description, Set<Integer> aliases) {
    this.code = code;
    this.description = description;
    this.aliases = aliases;
  }

  // construtor auxiliar para enums sem aliases
  ReleaseCategoryEnum(Integer code, String description) {
    this(code, description, Set.of());
  }

  /*
   * Lookup O(1)
   */
  private static final Map<Integer, ReleaseCategoryEnum> BY_CODE =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ReleaseCategoryEnum::getCode, Function.identity()));

  private static final Map<String, ReleaseCategoryEnum> BY_NAME =
    Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

  /*
   * Converte código do banco -> enum
   */
  public static ReleaseCategoryEnum fromCode(Integer code) {

    if (code == null) {
      return null;
    }

    ReleaseCategoryEnum value = BY_CODE.get(code);

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ReleaseCategoryEnum code: " + code
      );
    }

    return value;
  }

  /*
   * Converte código do arquivo (código canônico ou alias) -> enum.
   * Retorna NULL para códigos desconhecidos (uso: parsing de arquivo CNAB).
   */
  public static ReleaseCategoryEnum fromCodeOrAlias(Integer code) {

    if (code == null) {
      return NULL;
    }

    ReleaseCategoryEnum byCode = BY_CODE.get(code);
    if (byCode != null) {
      return byCode;
    }

    for (ReleaseCategoryEnum e : values()) {
      if (e.aliases.contains(code)) {
        return e;
      }
    }

    return NULL;
  }

  /*
   * Converte string -> enum
   */
  public static ReleaseCategoryEnum fromName(String name) {

    if (name == null || name.isBlank()) {
      return null;
    }

    ReleaseCategoryEnum value = BY_NAME.get(name.trim().toUpperCase());

    if (value == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Invalid ReleaseCategoryEnum name: " + name
      );
    }

    return value;
  }

  /*
   * Enum -> código do banco
   */
  public static Integer toCode(ReleaseCategoryEnum status) {
    return status != null ? status.code : null;
  }

}