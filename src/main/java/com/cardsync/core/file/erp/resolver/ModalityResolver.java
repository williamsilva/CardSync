package com.cardsync.core.file.erp.resolver;

import com.cardsync.domain.model.enums.ModalityEnum;

import java.text.Normalizer;
import java.util.Locale;

public final class ModalityResolver {
  private ModalityResolver() { }

  public static ModalityEnum resolve(String modality, int installment) {
    String normalized = normalize(modality);
    if (normalized == null) return ModalityEnum.NULL;

    if (containsAny(normalized, "outros", "outro")) return ModalityEnum.OUTROS;
    if (containsAny(normalized, "carteira digital", "wallet")) return ModalityEnum.DIGITAL_WALLET;
    if (containsAny(normalized, "debito", "debit", "maestro", "electron")) return ModalityEnum.CASH_DEBIT;
    if (containsAny(normalized, "credito", "credit")) return resolveInstallmentModality(installment);

    return ModalityEnum.NULL;
  }

  private static ModalityEnum resolveInstallmentModality(int installment) {
    if (installment <= 1) return ModalityEnum.CASH_CREDIT;
    if (installment <= 6) return ModalityEnum.INSTALLMENT_CREDIT_2_6;
    if (installment <= 12) return ModalityEnum.INSTALLMENT_CREDIT_7_12;
    if (installment <= 18) return ModalityEnum.INSTALLMENT_CREDIT_13_18;
    return ModalityEnum.NULL;
  }

  private static boolean containsAny(String text, String... values) {
    for (String value : values) {
      if (text.contains(value)) return true;
    }
    return false;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
      .replaceAll("\\s+", " ")
      .toLowerCase(Locale.ROOT);
  }
}
