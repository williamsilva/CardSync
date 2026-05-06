package com.cardsync.core.file.erp.resolver;

import com.cardsync.domain.model.enums.CaptureEnum;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class CaptureResolver {
  private static final Set<String> ECOMMERCE_ORIGINS = Set.of(
    "portal",
    "pagamento online",
    "ecommerce",
    "e-commerce",
    "loja virtual",
    "internet"
  );

  private static final Set<String> PDV_ORIGINS = Set.of(
    "consumo pdv",
    "menu adm pdv",
    "autoatendimento",
    "recarga/pagamento pdv",
    "central de atendimento multiclubes",
    "central de atendimento",
    "tef",
    "pdv",
    "pos"
  );

  private CaptureResolver() { }

  public static CaptureEnum resolve(String origin, String transactionType) {
    String normalizedOrigin = normalize(origin);
    if (normalizedOrigin == null) return CaptureEnum.NULL;

    if (ECOMMERCE_ORIGINS.contains(normalizedOrigin) || containsAny(normalizedOrigin, "online", "ecommerce", "e-commerce")) {
      return CaptureEnum.ECOMMERCE;
    }

    if (PDV_ORIGINS.contains(normalizedOrigin) || containsAny(normalizedOrigin, "pdv", "tef", "central de atendimento", "autoatendimento")) {
      return resolvePdvCapture(transactionType);
    }

    return CaptureEnum.NULL;
  }

  private static CaptureEnum resolvePdvCapture(String transactionType) {
    String normalizedTransactionType = normalize(transactionType);
    if (normalizedTransactionType != null && normalizedTransactionType.contains("manual")) {
      return CaptureEnum.MANUAL;
    }
    return CaptureEnum.PDV;
  }

  private static boolean containsAny(String text, String... values) {
    for (String value : values) {
      if (text.contains(value)) return true;
    }
    return false;
  }

  private static String normalize(String input) {
    if (input == null || input.isBlank()) return null;
    return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
      .replaceAll("\\s+", " ")
      .toLowerCase(Locale.ROOT);
  }
}
