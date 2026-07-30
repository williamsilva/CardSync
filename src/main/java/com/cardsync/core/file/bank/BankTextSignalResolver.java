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
      "DEBITO", "MAESTRO", "ELECTRON", "ELO DEB", "ELODEB", "CARTAO DEBITO")
      // "DEB"/"DB" isolados (não dentro de outra palavra como "DEBITO", já coberto acima) —
      // mesma classe de abreviação real do "CD" abaixo, mantidos como token isolado em vez de
      // substring livre para não colidir com nomes/palavras que contenham "DEB"/"DB" no meio.
      || hasStandaloneToken(normalizedText, "DEB")
      || hasStandaloneToken(normalizedText, "DB");
  }

  public boolean isCreditSignal(String normalizedText) {
    // "CD" é a abreviação real usada pelo Rede/Santander pra crédito (ex.: "REDE   MAST
    // CD0007866470") — sem ela, lançamentos com bandeira abreviada (MAST, e não MASTER/MASTERCARD)
    // nunca batem em nenhum sinal e ficam com modalidade não classificada (0), o que os torna
    // invisíveis no Extrato Bancário (ReleasesBankSpecs só lista modalidade em {1, 2, 13}).
    // "CR"/"CD" como substring livre geram falso positivo em qualquer nome que contenha essas
    // duas letras juntas (ex.: favorecido "CONCRETOCOM" contém "CR"), e "CRED" como substring
    // livre batia dentro da palavra "SICREDI" (nome do próprio banco aparecendo na descrição de
    // pagamentos de boleto Sicredi, ex.: "LIQUIDACAO BOLETO SICREDI") — classificando um débito
    // de conta como se fosse cartão de crédito. Por isso "CR"/"CD" exigem o padrão real
    // (abreviação + dígitos colados, sem letra antes) e "CRED" exige não estar colado a outra
    // letra antes/depois (token isolado), preservando o caso real do comentário acima.
    return containsAny(normalizedText,
      "CREDITO", "CARTAO CREDITO", "VISA", "MASTER", "MASTERCARD", "AMEX", "ELO", "HIPER")
      || hasStandaloneToken(normalizedText, "CRED")
      || hasAbbreviationFollowedByDigits(normalizedText, "CR")
      || hasAbbreviationFollowedByDigits(normalizedText, "CD");
  }

  /**
   * Casa {@code token} apenas quando não está colado a outra letra antes ou depois (ex.: "CRED"
   * em "PGTO CRED" bate, mas não em "SICREDI" nem em "CREDITO"). Dígitos colados nas pontas são
   * permitidos, já que abreviações bancárias reais costumam vir seguidas de código/NSU.
   */
  private boolean hasStandaloneToken(String normalizedText, String token) {
    if (normalizedText == null || normalizedText.isBlank()) return false;
    return Pattern.compile("(?<![A-Z])" + Pattern.quote(token) + "(?![A-Z])").matcher(normalizedText).find();
  }

  /**
   * Casa {@code prefix} apenas quando não está colado a outra letra antes e é imediatamente
   * seguido de um dígito (ex.: "CD0007866470"), reproduzindo o padrão real de abreviação bancária
   * sem casar a substring solta dentro de palavras como "CONCRETOCOM".
   */
  private boolean hasAbbreviationFollowedByDigits(String normalizedText, String prefix) {
    if (normalizedText == null || normalizedText.isBlank()) return false;
    return Pattern.compile("(?<![A-Z])" + Pattern.quote(prefix) + "(?=[0-9])").matcher(normalizedText).find();
  }

  public boolean isPixSignal(String normalizedText) {
    return containsAny(normalizedText, "PIX");
  }

  /**
   * PIX recebido (dinheiro entrando na conta) — usado para resolver a modalidade como
   * PIX_REC em vez de cair, por coincidência de texto, em "cartão de crédito" (ver
   * isCreditSignal: o marcador "PIX_CRED" do Sicredi contém um "CRED" isolado válido).
   */
  public boolean isPixReceiptSignal(String normalizedText) {
    return isPixSignal(normalizedText)
      && (containsAny(normalizedText, "RECEBIMENTO", "RECEBIDO") || hasStandaloneToken(normalizedText, "PIX CRED"));
  }

  /** PIX enviado/pago (dinheiro saindo da conta) — mesma lógica de isPixReceiptSignal, direção inversa. */
  public boolean isPixSentSignal(String normalizedText) {
    return isPixSignal(normalizedText)
      && (containsAny(normalizedText, "PAGAMENTO", "ENVIADO", "ENVIO") || hasStandaloneToken(normalizedText, "PIX DEB"));
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
    // "ELO" como substring livre batia dentro de nomes comuns de favorecidos de PIX (ex.:
    // "ANGELO" contém "ELO" inteiro) — classificando recebimentos PIX como se fossem venda em
    // cartão Elo. Nos dados reais do Sicredi a bandeira Elo sempre aparece como palavra isolada
    // ("SICREDI DEBITO ELO"), nunca colada a outra letra, então exigir token isolado é seguro.
    return hasStandaloneToken(normalizedText, "ELO");
  }

  public boolean isCabalSignal(String normalizedText) {
    return containsAny(normalizedText, "CABAL", "CABA");
  }

  public boolean isAmexSignal(String normalizedText) {
    // "AM" como substring livre batia dentro de "PAGAMENTO" e "ACQUAMANIA" (nome da própria
    // empresa) — fazendo praticamente qualquer pagamento PIX (e boa parte dos recebimentos) ser
    // marcado como venda em cartão American Express. "AMEX"/"AMERICAN EXPRESS" continuam iguais.
    return containsAny(normalizedText, "AMEX", "AMERICAN EXPRESS") || hasStandaloneToken(normalizedText, "AM");
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
