package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.InstallmentUnschedulingEntity;
import com.cardsync.domain.model.PendingDebtEntity;
import com.cardsync.domain.model.SettledDebtEntity;
import com.cardsync.domain.model.enums.ChargebackAnalysisStatus;
import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.domain.model.enums.ChargebackEventSourceType;
import com.cardsync.domain.model.enums.ChargebackReasonCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

@Component
public class ConciliationDebitChargebackClassifier {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public boolean isChargeback(PendingDebtEntity entity) {
    if (entity == null) return false;

    if (isEcommerceDebtRecord(entity.getRecordType()) && hasStrongTransactionKey(entity.getPvNumber(), entity.getNsu(),
      entity.getAuthorization(), entity.getTid())) {
      return true;
    }

    return isSaleChargebackReason(entity.getReasonCode(), entity.getReasonCode2(), entity.getReasonDescription());
  }

  public boolean isChargeback(SettledDebtEntity entity) {
    if (entity == null) return false;

    if (isEcommerceDebtRecord(entity.getRecordType()) && hasStrongTransactionKey(entity.getPvNumber(), entity.getNsu(),
      entity.getAuthorization(), entity.getTid())) {
      return true;
    }

    return isSaleChargebackReason(entity.getReasonCode(), entity.getCodeReasonAdjustment2(), entity.getReasonDescription());
  }

  public boolean isChargeback(AdjustmentEntity entity) {
    if (entity == null) return false;

    String recordType = trim(entity.getRecordType());
    if ("054".equals(recordType) && hasStrongTransactionKey(entity.getPvNumberOriginal(), entity.getNsu(), entity.getAuthorization(), entity.getTid())) {
      return true;
    }

    AdjustmentReasonEnum reason = entity.getAdjustmentReason();
    return isSaleChargebackReason(reason != null ? reason.getCode() : null, entity.getAdjustmentReason2(), entity.getAdjustmentDescription());
  }

  public boolean isChargeback(InstallmentUnschedulingEntity entity) {
    if (entity == null) return false;

    String recordType = trim(entity.getRecordType());

    // EEVD registro 08: motivo 01 = Chargeback; 00 = Cancelamento.
    if ("08".equals(recordType)) {
      return Integer.valueOf(1).equals(entity.getUnschedulingStatus());
    }

    // EEFI 049/069: tipo de débito 2 = cancelamento via emissor, que compõe a trilha de chargeback.
    if ("049".equals(recordType) || "069".equals(recordType)) {
      return "2".equals(trim(entity.getTypeDebit())) || hasStrongTransactionKey(entity.getPvNumberOriginal(), entity.getNsu(), null, entity.getTid());
    }

    // EEFI 057 é o complemento e-commerce do desagendamento; não possui motivo explícito, então entra apenas com chave forte.
    return "057".equals(recordType) && hasStrongTransactionKey(entity.getPvNumberOriginal(), entity.getNsu(), null, entity.getTid());
  }

  public boolean isSaleChargebackReason(Integer primaryCode, Integer secondaryCode, String description) {
    if (isSaleChargebackReasonCode(primaryCode) || isSaleChargebackReasonCode(secondaryCode)) {
      return true;
    }

    if (isExplicitNonSaleChargebackCode(primaryCode) || isExplicitNonSaleChargebackCode(secondaryCode)) {
      return false;
    }

    return hasSaleChargebackTerms(description);
  }

  public boolean isSaleChargebackReasonCode(Integer code) {
    return ChargebackReasonCode.isSaleChargebackReasonCode(code);
  }

  public boolean isExplicitNonSaleChargebackCode(Integer code) {
    return ChargebackReasonCode.isExplicitNonSaleChargebackCode(code);
  }

  public ChargebackEventSourceType type(PendingDebtEntity entity) {
    return "055".equals(trim(entity.getRecordType()))
      ? ChargebackEventSourceType.PENDING_DEBT_ECOMMERCE
      : ChargebackEventSourceType.PENDING_DEBT;
  }

  public ChargebackEventSourceType type(SettledDebtEntity entity) {
    return "056".equals(trim(entity.getRecordType()))
      ? ChargebackEventSourceType.SETTLED_DEBT_ECOMMERCE
      : ChargebackEventSourceType.SETTLED_DEBT;
  }

  public ChargebackEventSourceType type(AdjustmentEntity entity) {
    String recordType = trim(entity.getRecordType());

    if ("035".equals(recordType) || "053".equals(recordType)) return ChargebackEventSourceType.NET_ADJUSTMENT;
    if ("038".equals(recordType)) return ChargebackEventSourceType.BANK_DEBIT;
    if ("054".equals(recordType)) return ChargebackEventSourceType.BANK_DEBIT_ECOMMERCE;
    if ("043".equals(recordType)) return ChargebackEventSourceType.CREDIT_REVERSAL;

    if (isChargeback(entity)) return ChargebackEventSourceType.CHARGEBACK_ADJUSTMENT;
    if (hasCancellationTerms(entity)) return ChargebackEventSourceType.CANCELLATION;
    if (isCreditAdjustment(entity)) return ChargebackEventSourceType.CREDIT_ADJUSTMENT;
    if (isDebitAdjustment(entity)) return ChargebackEventSourceType.DEBIT_ADJUSTMENT;
    if (Boolean.TRUE.equals(entity.getEcommerce())) return ChargebackEventSourceType.ECOMMERCE_ADJUSTMENT;
    return ChargebackEventSourceType.ADJUSTMENT;
  }

  public ChargebackEventSourceType type(InstallmentUnschedulingEntity entity) {
    return Boolean.TRUE.equals(entity.getEcommerce())
      ? ChargebackEventSourceType.DESCHEDULEMENT_ECOMMERCE
      : ChargebackEventSourceType.DESCHEDULEMENT;
  }

  public String status(PendingDebtEntity entity) {
    BigDecimal pending = nz(entity.getPendingValue());
    BigDecimal compensated = nz(entity.getCompensatedValue());
    if (pending.compareTo(ZERO) <= 0 && compensated.compareTo(ZERO) > 0) return "COMPENSATED";
    if (compensated.compareTo(ZERO) > 0) return "PARTIALLY_COMPENSATED";
    return "PENDING";
  }

  public String status(SettledDebtEntity entity) {
    return "LIQUIDATED";
  }

  public String status(AdjustmentEntity entity) {
    if (isCreditAdjustment(entity)) return "CREDIT_APPLIED";
    if (isDebitAdjustment(entity) || hasCancellationTerms(entity) || isChargeback(entity)) return "DEBIT_APPLIED";
    return "APPLIED";
  }

  public ChargebackAnalysisStatus chargebackStatus(InstallmentUnschedulingEntity entity) {
    return ChargebackAnalysisStatus.DESCHEDULED;
  }

  public BigDecimal debitValue(AdjustmentEntity entity) {
    BigDecimal value = firstNonNull(
      entity.getPendingValue(),
      entity.getTotalDebitValue(),
      entity.getAdjustmentValue(),
      entity.getCancellationValueRequested(),
      entity.getTransactionValue()
    );
    return value != null ? value.abs() : ZERO;
  }

  public BigDecimal debitValue(InstallmentUnschedulingEntity entity) {
    BigDecimal value = firstNonNull(
      entity.getCancellationValue(),
      entity.getAdjustmentValue(),
      entity.getRvValueOriginal(),
      entity.getOriginalValueChangedInstallment(),
      entity.getNewInstallmentValue()
    );
    return value != null ? value.abs() : ZERO;
  }

  public BigDecimal settledValue(AdjustmentEntity entity) {
    if (isCreditAdjustment(entity)) return debitValue(entity);
    return null;
  }

  public LocalDate debitDate(AdjustmentEntity entity) {
    return firstNonNull(
      entity.getAdjustmentDate(),
      entity.getCreditDate(),
      entity.getReleaseDate(),
      entity.getTransactionDate(),
      entity.getLetterDate()
    );
  }

  public LocalDate settlementDate(AdjustmentEntity entity) {
    return firstNonNull(entity.getCreditDate(), entity.getReleaseDate());
  }

  public LocalDate unschedulingDate(InstallmentUnschedulingEntity entity) {
    return firstNonNull(
      entity.getCancellationDate(),
      entity.getAdjustedCreditDate(),
      entity.getDateCredit(),
      entity.getAdjustedRvDate(),
      entity.getRvDateOriginal(),
      entity.getTransactionDate(),
      entity.getNegotiationDate()
    );
  }

  public boolean isDebitAdjustment(AdjustmentEntity entity) {
    if (entity == null) return false;
    String combined = combine(
      entity.getAdjustmentType(),
      entity.getDebitType(),
      entity.getRawAdjustmentCode(),
      entity.getSourceRecordIdentifier(),
      entity.getAdjustmentDescription(),
      entity.getRecordType()
    );

    BigDecimal value = firstNonNull(
      entity.getTotalDebitValue(),
      entity.getPendingValue(),
      entity.getAdjustmentValue(),
      entity.getCancellationValueRequested()
    );

    return containsAny(combined, "debito", "débito", "debit")
      || (value != null && value.signum() < 0)
      || "038".equals(trim(entity.getRecordType()))
      || "044".equals(trim(entity.getRecordType()))
      || "055".equals(trim(entity.getRecordType()));
  }

  public boolean isCreditAdjustment(AdjustmentEntity entity) {
    if (entity == null) return false;
    String combined = combine(
      entity.getAdjustmentType(),
      entity.getDebitType(),
      entity.getRawAdjustmentCode(),
      entity.getSourceRecordIdentifier(),
      entity.getAdjustmentDescription(),
      entity.getRecordType()
    );

    BigDecimal value = firstNonNull(
      entity.getAdjustmentValue(),
      entity.getLiquidValue(),
      entity.getNewTransactionValue()
    );

    return containsAny(combined, "credito", "crédito", "credit")
      || (value != null && value.signum() > 0 && !isDebitAdjustment(entity))
      || "043".equals(trim(entity.getRecordType()));
  }

  private boolean isEcommerceDebtRecord(String recordType) {
    String type = trim(recordType);
    return "055".equals(type) || "056".equals(type);
  }

  private boolean hasStrongTransactionKey(Integer pvNumber, Long nsu, String authorization, String tid) {
    if (pvNumber == null) return false;
    if (notBlank(tid)) return true;
    return nsu != null && notBlank(authorization);
  }

  private boolean hasSaleChargebackTerms(String value) {
    String normalized = normalize(value);
    if (normalized == null) return false;

    if (containsAny(normalized, "tarifa", "pos-inativ", "pinpad", "conec", "conect", "al.pos", "aluguel", "cancel venda debito")) {
      return false;
    }

    return containsAny(
      normalized,
      "contestacao vda",
      "contestacao venda",
      "contestacao de venda",
      "contest vendas",
      // Vocabulário real da Cielo (Tabela IX/Tabela II do manual, ver ProcessCielo03Service):
      // "Venda contestada pelo banco a pedido do portador do cartão", "Contestação do portador
      // do cartão" — não batem nos termos acima, que assumem "contestação [de] venda".
      "contestada pelo portador",
      "contestado pelo portador",
      "contestada pelo banco",
      "contestacao do portador",
      "chargeback",
      "chbk",
      "cback",
      "disputa",
      "disputas"
    );
  }

  private boolean hasCancellationTerms(AdjustmentEntity entity) {
    String combined = combine(
      entity.getAdjustmentDescription(),
      entity.getAdjustmentType(),
      entity.getDebitType(),
      entity.getRawAdjustmentCode(),
      entity.getSourceRecordIdentifier()
    );
    return containsAny(combined, "cancel", "cancelamento", "estorno", "devolucao", "devolução");
  }

  private boolean containsAny(String value, String... needles) {
    String normalized = normalize(value);
    if (normalized == null) return false;
    for (String needle : needles) {
      String normalizedNeedle = normalize(needle);
      if (normalizedNeedle != null && normalized.contains(normalizedNeedle)) return true;
    }
    return false;
  }

  private String combine(String... values) {
    StringBuilder builder = new StringBuilder();
    if (values == null) return "";
    for (String value : values) {
      if (notBlank(value)) builder.append(' ').append(value);
    }
    return builder.toString();
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return normalized.toLowerCase(Locale.ROOT).trim();
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... values) {
    if (values == null) return null;
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}