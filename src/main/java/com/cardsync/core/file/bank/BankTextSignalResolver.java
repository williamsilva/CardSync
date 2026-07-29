package com.cardsync.core.file.bank;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normaliza e extrai sinais comerciais de textos bancários/CNAB.

 * Mantém a regra fora do parser CNAB para que o Lote 6 possa reutilizar a mesma
 * classificação na conciliação bancária.
 */
@Component
public class BankTextSignalResolver {

  // \b não serve aqui: é limite entre \w e não-\w, e letra-para-dígito (ou dígito-para-letra)
  // NÃO é uma transição de \w — dígitos e letras são ambos \w. Muitos bancos colam o PV direto
  // num marcador de texto sem espaço (ex.: "CD0007866470", "DBTO1100125202", "350834GETNET-VISA"),
  // então \b\d{5,12}\b nunca batia nesses casos e o PV nunca era extraído (establishment ficava
  // sempre null). Usar lookaround específico de dígito em vez de \w resolve os dois lados.
  private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<!\\d)\\d{5,12}(?!\\d)");
  private static final Pattern PV_HINT_PATTERN = Pattern.compile("(?:PV|EC|ESTAB|ESTABELECIMENTO|LOJA|COD)\\s*(?:N|NO|NUM|NR|:|-)?\\s*(\\d{5,12})");

  public String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9]", " ")
      .replaceAll("\\s+", " ")
      .trim();
  }

  public String onlyDigits(String value) {
    if (value == null) return null;
    String digits = value.replaceAll("\\D", "");
    return digits.isBlank() ? null : digits;
  }

  public boolean containsNormalized(String normalizedText, String candidate) {
    String normalizedCandidate = normalize(candidate);
    return !normalizedCandidate.isBlank() && normalizedText.contains(normalizedCandidate);
  }

  public boolean containsAny(String normalizedText, String... candidates) {
    if (normalizedText == null || normalizedText.isBlank()) return false;
    for (String candidate : candidates) {
      if (containsNormalized(normalizedText, candidate)) return true;
    }
    return false;
  }

  public List<Integer> extractPvCandidates(String rawText) {
    String normalized = normalize(rawText);
    Set<Integer> values = new LinkedHashSet<>();

    Matcher hinted = PV_HINT_PATTERN.matcher(normalized);
    while (hinted.find()) {
      addInt(values, hinted.group(1));
    }

    Matcher generic = NUMBER_PATTERN.matcher(normalized);
    while (generic.find()) {
      String value = generic.group();
      // Evita capturar datas CNAB como 20251216 ou códigos muito curtos sem contexto.
      if (looksLikeDate(value)) continue;
      addInt(values, value);
    }

    return new ArrayList<>(values);
  }

  public boolean isDebitSignal(String normalizedText) {
    return containsAny(normalizedText,
      "DEBITO", "DEB", "MAESTRO", "ELECTRON", "ELO DEB", "ELODEB", "CARTAO DEBITO", "DB");
  }

  public boolean isCreditSignal(String normalizedText) {
    // "CD" é a abreviação real usada pelo Rede/Santander pra crédito (ex.: "REDE   MAST
    // CD0007866470") — sem ela, lançamentos com bandeira abreviada (MAST, e não MASTER/MASTERCARD)
    // nunca batem em nenhum sinal e ficam com modalidade não classificada (0), o que os torna
    // invisíveis no Extrato Bancário (ReleasesBankSpecs só lista modalidade em {1, 2, 13}).
    return containsAny(normalizedText,
      "CREDITO", "CRED", "CARTAO CREDITO", "VISA", "MASTER", "MASTERCARD", "AMEX", "ELO", "HIPER", "CR", "CD");
  }

  public boolean isRedeSignal(String normalizedText) {
    return containsAny(normalizedText,
      "REDE", "REDECARD", "REDE CARD", "CIELO REDE")
      || normalizedText.matches(".*\\bREDE\\b.*");
  }

  public boolean isCieloSignal(String normalizedText) {
    return containsAny(normalizedText, "CIELO", "VISANET");
  }

  public boolean isStoneSignal(String normalizedText) {
    return containsAny(normalizedText, "STONE", "PAGAR ME", "PAGARME");
  }

  public boolean isGetnetSignal(String normalizedText) {
    return containsAny(normalizedText, "GETNET", "GET NET");
  }

  public boolean isVisaSignal(String normalizedText) {
    return containsAny(normalizedText, "VISA", "ELECTRON", "VSE", "VSA", "VS");
  }

  public boolean isMasterSignal(String normalizedText) {
    return containsAny(normalizedText, "MASTER", "MAST", "MASTERCARD", "MAESTRO", "MCD", "MCC", "MC");
  }

  public boolean isEloSignal(String normalizedText) {
    return containsAny(normalizedText, "ELO", "EL");
  }

  public boolean isCabalSignal(String normalizedText) {
    return containsAny(normalizedText, "CABAL", "CABA");
  }

  public boolean isAmexSignal(String normalizedText) {
    return containsAny(normalizedText, "AMEX", "AMERICAN EXPRESS", "AM");
  }

  /** Santander abrevia "Banescard" para "BANESC" no histórico (ex.: "867379REDE-BANESC DEB"). */
  public boolean isBanescardSignal(String normalizedText) {
    return containsAny(normalizedText, "BANESC", "BANE", "BANESCARD");
  }

  private boolean looksLikeDate(String value) {
    if (value == null || value.length() != 8) return false;
    try {
      int day = Integer.parseInt(value.substring(0, 2));
      int month = Integer.parseInt(value.substring(2, 4));
      int year = Integer.parseInt(value.substring(4, 8));
      boolean ddmmyyyy = day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 2000 && year <= 2099;
      int year2 = Integer.parseInt(value.substring(0, 4));
      int month2 = Integer.parseInt(value.substring(4, 6));
      int day2 = Integer.parseInt(value.substring(6, 8));
      boolean yyyymmdd = year2 >= 2000 && year2 <= 2099 && month2 >= 1 && month2 <= 12 && day2 >= 1 && day2 <= 31;
      return ddmmyyyy || yyyymmdd;
    } catch (Exception ex) {
      return false;
    }
  }

  private void addInt(Set<Integer> values, String value) {
    try {
      values.add(Integer.valueOf(value));
    } catch (Exception ignored) {
      // Campo de layout pode vir mascarado; ignorar aqui é intencional.
    }
  }
}
