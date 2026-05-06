package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.PendingDebtEntity;
import com.cardsync.domain.model.SettledDebtEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Component
public class ConciliationDebitChargebackClassifier {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public boolean isChargeback(PendingDebtEntity entity) {
    if (entity == null) return false;
    return entity.getNumberProcessChargeback() != null
      || hasChargebackTerms(entity.getReasonDescription())
      || hasChargebackTerms(entity.getCompensationDescription());
  }

  public boolean isChargeback(SettledDebtEntity entity) {
    if (entity == null) return false;
    return entity.getNumberProcessChargeback() != null
      || hasChargebackTerms(entity.getReasonDescription())
      || hasChargebackTerms(entity.getCompensation());
  }

  public boolean isChargeback(AdjustmentEntity entity) {
    if (entity == null) return false;
    return hasChargebackTerms(entity.getAdjustmentDescription())
      || hasChargebackTerms(entity.getAdjustmentType())
      || hasChargebackTerms(entity.getDebitType())
      || hasChargebackTerms(entity.getRawAdjustmentCode())
      || hasChargebackTerms(entity.getSourceRecordIdentifier());
  }

  public String type(PendingDebtEntity entity) {
    return isChargeback(entity) ? "PENDING_CHARGEBACK" : "PENDING_DEBT";
  }

  public String type(SettledDebtEntity entity) {
    return isChargeback(entity) ? "SETTLED_CHARGEBACK" : "SETTLED_DEBT";
  }

  public String type(AdjustmentEntity entity) {
    if (isChargeback(entity)) return "CHARGEBACK_ADJUSTMENT";
    if (hasCancellationTerms(entity)) return "CANCELLATION";
    if (isCreditAdjustment(entity)) return "CREDIT_ADJUSTMENT";
    if (isDebitAdjustment(entity)) return "DEBIT_ADJUSTMENT";
    if (Boolean.TRUE.equals(entity.getEcommerce())) return "ECOMMERCE_ADJUSTMENT";
    return "ADJUSTMENT";
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

  public String chargebackStatus(PendingDebtEntity entity) {
    BigDecimal pending = nz(entity.getPendingValue());
    BigDecimal compensated = nz(entity.getCompensatedValue());
    if (pending.compareTo(ZERO) <= 0 && compensated.compareTo(ZERO) > 0) return "REVERSED";
    if (compensated.compareTo(ZERO) > 0) return "UNDER_REVIEW";
    return "OPEN";
  }

  public String chargebackStatus(SettledDebtEntity entity) {
    return "LOST";
  }

  public String chargebackStatus(AdjustmentEntity entity) {
    if (isCreditAdjustment(entity)) return "REVERSED";
    return "OPEN";
  }

  public BigDecimal debitValue(AdjustmentEntity entity) {
    BigDecimal value = firstNonNull(entity.getPendingValue(), entity.getTotalDebitValue(), entity.getAdjustmentValue(), entity.getCancellationValueRequested(), entity.getTransactionValue());
    return value != null ? value.abs() : ZERO;
  }

  public BigDecimal settledValue(AdjustmentEntity entity) {
    if (isCreditAdjustment(entity)) return debitValue(entity);
    return null;
  }

  public LocalDate debitDate(AdjustmentEntity entity) {
    return firstNonNull(entity.getAdjustmentDate(), entity.getCreditDate(), entity.getReleaseDate(), entity.getTransactionDate(), entity.getLetterDate());
  }

  public LocalDate settlementDate(AdjustmentEntity entity) {
    return firstNonNull(entity.getCreditDate(), entity.getReleaseDate());
  }

  public boolean contributesToDebitAnalysis(AdjustmentEntity entity) {
    if (entity == null) return false;
    return isDebitAdjustment(entity)
      || isCreditAdjustment(entity)
      || isChargeback(entity)
      || hasCancellationTerms(entity)
      || notBlank(entity.getAdjustmentDescription())
      || notBlank(entity.getAdjustmentType())
      || notBlank(entity.getDebitType());
  }

  public boolean isDebitAdjustment(AdjustmentEntity entity) {
    if (entity == null) return false;
    String combined = combine(entity.getAdjustmentType(), entity.getDebitType(), entity.getRawAdjustmentCode(), entity.getSourceRecordIdentifier(), entity.getAdjustmentDescription(), entity.getRecordType());
    BigDecimal value = firstNonNull(entity.getTotalDebitValue(), entity.getPendingValue(), entity.getAdjustmentValue(), entity.getCancellationValueRequested());
    return containsAny(combined, "debito", "débito", "debit", "chargeback", "contest", "cancel", "estorno")
      || (value != null && value.signum() < 0)
      || "038".equals(trim(entity.getRecordType()))
      || "044".equals(trim(entity.getRecordType()))
      || "055".equals(trim(entity.getRecordType()));
  }

  public boolean isCreditAdjustment(AdjustmentEntity entity) {
    if (entity == null) return false;
    String combined = combine(entity.getAdjustmentType(), entity.getDebitType(), entity.getRawAdjustmentCode(), entity.getSourceRecordIdentifier(), entity.getAdjustmentDescription(), entity.getRecordType());
    BigDecimal value = firstNonNull(entity.getAdjustmentValue(), entity.getLiquidValue(), entity.getNewTransactionValue());
    return containsAny(combined, "credito", "crédito", "credit")
      || (value != null && value.signum() > 0 && !isDebitAdjustment(entity))
      || "043".equals(trim(entity.getRecordType()));
  }

  private boolean hasChargebackTerms(String value) {
    return containsAny(normalize(value), "chargeback", "contest", "disputa", "desacordo", "fraude", "representacao", "representação");
  }

  private boolean hasCancellationTerms(AdjustmentEntity entity) {
    String combined = combine(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), entity.getRawAdjustmentCode(), entity.getSourceRecordIdentifier());
    return containsAny(combined, "cancel", "cancelamento", "estorno", "devolucao", "devolução");
  }

  private boolean containsAny(String value, String... needles) {
    String normalized = normalize(value);
    if (normalized == null) return false;
    for (String needle : needles) {
      if (normalized.contains(normalize(needle))) return true;
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
    return value == null ? null : value.toLowerCase(Locale.ROOT).trim();
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
