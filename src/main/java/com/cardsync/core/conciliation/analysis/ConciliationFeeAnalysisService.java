package com.cardsync.core.conciliation.analysis;

import com.cardsync.core.file.erp.calculator.FinancialCalculator;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ContractAuditStatusEnum;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliationFeeAnalysisService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal RATE_TOLERANCE = BigDecimal.valueOf(0.01);
  private static final BigDecimal VALUE_TOLERANCE = BigDecimal.valueOf(0.05);

  private final ContractAuditWriterService contractAuditWriterService;
  private final ContractedAcquirerRateLookupService contractedAcquirerRateLookupService;

  private final ThreadLocal<Map<RateLookupKey, List<ContractEntity>>> reconciliationRateCache =
    ThreadLocal.withInitial(HashMap::new);

  private final ThreadLocal<PendingAuditBatch> pendingAuditBatch =
    ThreadLocal.withInitial(PendingAuditBatch::new);

  private final ThreadLocal<Boolean> auditSynchronizationRegistered =
    ThreadLocal.withInitial(() -> Boolean.FALSE);

  @Transactional(readOnly = true)
  public FeeAnalysisResult analyze(TransactionAcqEntity entity) {
    BigDecimal gross = nz(entity.getGrossValue());
    BigDecimal appliedFee = appliedFeeValue(entity);
    BigDecimal appliedRate = appliedRate(entity, gross, appliedFee);

    Optional<ContractedAcquirerRate> contractedRate = contractedAcquirerRateLookupService.findRate(entity);
    BigDecimal expectedRate = contractedRate.map(ContractedAcquirerRate::rate).orElse(null);
    BigDecimal expectedFee = expectedRate != null ? calculateFee(gross, expectedRate) : null;
    BigDecimal feeDifference = expectedFee != null ? appliedFee.subtract(expectedFee) : null;
    String status = feeStatus(expectedRate, appliedRate, feeDifference);

    return new FeeAnalysisResult(
      entity.getId(), entity.getSaleDate(), companyName(entity.getCompany()), establishmentName(entity.getEstablishment()),
      acquirerName(entity.getAcquirer()), flagName(entity.getFlag()), modalityName(entity.getModality()), entity.getNsu(),
      entity.getAuthorization(), entity.getGrossValue(), expectedRate, appliedRate, expectedFee, appliedFee, feeDifference, status
    );
  }

  /**
   * Executa a auditoria de taxa para o par ERP x adquirente já encontrado.

   * Regras:
   * - Sem contrato vigente: a venda ERP recebe a taxa/valores reais da adquirente,
   *   fica marcada com missingContractAtSale=true e não gera falsa divergência de taxa.
   * - Com contrato vigente e diferença acima da tolerância: grava/atualiza cs_contract_audit.
   * - Com contrato vigente e taxa OK: remove auditoria antiga da venda, se existir.
   */
  @Transactional
  public FeeReconciliationResult reconcileMatchedSale(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (erp == null || acq == null) {
      return FeeReconciliationResult.noChange();
    }

    BigDecimal gross = nz(acq.getGrossValue());
    AcquirerFeeSnapshot acquirerFee = acquirerFeeSnapshot(acq);
    BigDecimal appliedFee = acquirerFee.discountValue();
    BigDecimal appliedRate = acquirerFee.rateAcquirer();
    Optional<ContractedAcquirerRate> contractedRate = findRateForReconciliationCached(acq);

    if (contractedRate.isEmpty()) {
      boolean changed = applyAcquirerFeeToErpWhenContractIsMissing(erp, acq, appliedRate);
      changed |= setFeeReconciliationStatus(erp, acq, FeeReconciliationStatusEnum.MISSING_VALID_CONTRACT);
      removeAudit(acq.getId());
      return new FeeReconciliationResult(changed, false, true);
    }

    ContractedAcquirerRate rate = contractedRate.get();

    // 1) Primeiro normaliza o ERP pela taxa contratada vigente.
    // A análise de divergência só acontece depois disso, para comparar a adquirente
    // contra o cenário contratual correto gravado no ERP.
    boolean erpChanged = applyContractRateToErp(erp, rate);

    BigDecimal expectedRate = rate.rate();
    BigDecimal expectedFee = calculateFee(gross, expectedRate);
    BigDecimal difference = calculateDifference(appliedFee, expectedFee);

    if (isFeeOk(expectedRate, appliedRate, difference)) {
      erpChanged |= setFeeReconciliationStatus(erp, acq, FeeReconciliationStatusEnum.RECONCILED);
      removeAudit(acq.getId());
      return new FeeReconciliationResult(erpChanged, false, false);
    }

    erpChanged |= setFeeReconciliationStatus(erp, acq, FeeReconciliationStatusEnum.DIVERGENT_RATE);
    saveOrUpdateAudit(erp, acq, rate, appliedRate, appliedFee, expectedFee, difference);
    return new FeeReconciliationResult(erpChanged, true, false);
  }

  private boolean applyContractRateToErp(TransactionErpEntity erp, ContractedAcquirerRate rate) {
    if (erp == null || rate == null) {
      return false;
    }

    boolean changed = false;
    BigDecimal contractedRate = nz(rate.rate());
    BigDecimal discountValue = FinancialCalculator.calculateDiscountValue(erp.getGrossValue(), contractedRate);
    BigDecimal liquidValue = FinancialCalculator.calculateNetValue(erp.getGrossValue(), contractedRate);

    changed |= setIfDifferent(erp.getContractedFee(), contractedRate, erp::setContractedFee);
    changed |= setIfDifferent(erp.getDiscountValue(), discountValue, erp::setDiscountValue);
    changed |= setIfDifferent(erp.getLiquidValue(), liquidValue, erp::setLiquidValue);
    changed |= setIfDifferent(erp.getMissingContractAtSale(), Boolean.FALSE, erp::setMissingContractAtSale);

    if (erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_CONTRACT) {
      erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
      erp.setCommercialStatusMessage(null);
      changed = true;
    }

    changed |= updateErpInstallmentsFromContract(erp, contractedRate);

    if (changed) {
      log.debug(
        "Taxa ERP atualizada pelo contrato vigente antes da análise de taxas. erpId={}, acqId={}, nsu={}, taxaContrato={}",
        erp.getId(),
        erp.getTransactionAcq() == null ? null : erp.getTransactionAcq().getId(),
        erp.getNsu(),
        contractedRate
      );
    }

    return changed;
  }

  private boolean setFeeReconciliationStatus(TransactionErpEntity erp, TransactionAcqEntity acq, FeeReconciliationStatusEnum status) {
    if (status == null) {
      return false;
    }

    boolean changed = false;
    if (erp != null) {
      changed |= setIfDifferent(erp.getFeeReconciliationStatus(), status, erp::setFeeReconciliationStatus);
    }
    if (acq != null) {
      changed |= setIfDifferent(acq.getFeeReconciliationStatus(), status, acq::setFeeReconciliationStatus);
    }

    return changed;
  }

  private boolean updateErpInstallmentsFromContract(TransactionErpEntity erp, BigDecimal contractedRate) {
    if (erp.getInstallments() == null || erp.getInstallments().isEmpty()) {
      return false;
    }

    List<InstallmentErpEntity> installments = erp.getInstallments().stream()
      .sorted(Comparator.comparing(InstallmentErpEntity::getInstallment, Comparator.nullsLast(Integer::compareTo)))
      .toList();

    int totalInstallments = Math.max(installments.size(), 1);
    BigDecimal transactionGross = nz(erp.getGrossValue());
    BigDecimal distributedGross = transactionGross.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);
    BigDecimal grossRemainder = transactionGross.subtract(distributedGross.multiply(BigDecimal.valueOf(totalInstallments)));

    boolean changed = false;
    int index = 0;
    for (InstallmentErpEntity installment : installments) {
      index++;
      BigDecimal installmentGross = installment.getGrossValue();
      if (installmentGross == null || installmentGross.compareTo(ZERO) == 0) {
        installmentGross = index == 1 ? distributedGross.add(grossRemainder) : distributedGross;
        changed |= setIfDifferent(installment.getGrossValue(), installmentGross, installment::setGrossValue);
      }

      BigDecimal installmentDiscount = FinancialCalculator.calculateDiscountValue(installmentGross, contractedRate);
      BigDecimal installmentLiquid = FinancialCalculator.calculateNetValue(installmentGross, contractedRate);

      changed |= setIfDifferent(installment.getDiscountValue(), installmentDiscount, installment::setDiscountValue);
      changed |= setIfDifferent(installment.getLiquidValue(), installmentLiquid, installment::setLiquidValue);
    }

    return changed;
  }

  private boolean applyAcquirerFeeToErpWhenContractIsMissing(TransactionErpEntity erp, TransactionAcqEntity acq, BigDecimal appliedRate) {
    boolean changed = false;
    BigDecimal normalizedRate = nz(appliedRate);
    BigDecimal discountValue = FinancialCalculator.calculateDiscountValue(erp.getGrossValue(), normalizedRate);
    BigDecimal liquidValue = FinancialCalculator.calculateNetValue(erp.getGrossValue(), normalizedRate);

    changed |= setIfDifferent(erp.getContractedFee(), normalizedRate, erp::setContractedFee);
    changed |= setIfDifferent(erp.getDiscountValue(), discountValue, erp::setDiscountValue);
    changed |= setIfDifferent(erp.getLiquidValue(), liquidValue, erp::setLiquidValue);
    changed |= setIfDifferent(erp.getMissingContractAtSale(), Boolean.TRUE, erp::setMissingContractAtSale);

    if (erp.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_CONTRACT) {
      erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
      erp.setCommercialStatusMessage("Venda conciliada sem contrato vigente na data da transação; taxa/valores " +
        "foram assumidos a partir da adquirente para evitar falsa divergência.");
      changed = true;
    }

    changed |= updateErpInstallmentsFromAcquirer(erp, acq);

    if (changed) {
      log.debug(
        "Taxa ERP atualizada pela adquirente por ausência de contrato vigente. erpId={}, acqId={}, nsu={}, taxaAdquirente={}",
        erp.getId(), acq.getId(), erp.getNsu(), normalizedRate
      );
    }

    return changed;
  }

  private boolean updateErpInstallmentsFromAcquirer(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (erp.getInstallments() == null || erp.getInstallments().isEmpty()) {
      return false;
    }

    Map<Integer, InstallmentAcqEntity> acqByInstallment = acq.getInstallments() == null
      ? Map.of()
      : acq.getInstallments().stream()
      .filter(item -> item.getInstallment() != null)
      .collect(Collectors.toMap(InstallmentAcqEntity::getInstallment, Function.identity(),
        (left, right) -> left, LinkedHashMap::new));

    boolean changed = false;
    if (!acqByInstallment.isEmpty()) {
      for (InstallmentErpEntity erpInstallment : erp.getInstallments()) {
        InstallmentAcqEntity acqInstallment = acqByInstallment.get(erpInstallment.getInstallment());
        if (acqInstallment == null) continue;
        changed |= setIfDifferent(erpInstallment.getGrossValue(), acqInstallment.getGrossValue(), erpInstallment::setGrossValue);
        changed |= setIfDifferent(erpInstallment.getLiquidValue(), acqInstallment.getLiquidValue(), erpInstallment::setLiquidValue);
        changed |= setIfDifferent(erpInstallment.getDiscountValue(), acqInstallment.getDiscountValue(), erpInstallment::setDiscountValue);
        changed |= setIfDifferent(erpInstallment.getExpectedPaymentDate(), acqInstallment.getExpectedPaymentDate(), erpInstallment::setExpectedPaymentDate);
      }
      return changed;
    }

    int totalInstallments = Math.max(erp.getInstallments().size(), 1);
    BigDecimal grossTotal = nz(erp.getGrossValue());
    BigDecimal liquidTotal = nz(erp.getLiquidValue());
    BigDecimal discountTotal = nz(erp.getDiscountValue());

    BigDecimal grossPer = grossTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);
    BigDecimal liquidPer = liquidTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);
    BigDecimal discountPer = discountTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);

    BigDecimal grossRemainder = grossTotal.subtract(grossPer.multiply(BigDecimal.valueOf(totalInstallments)));
    BigDecimal liquidRemainder = liquidTotal.subtract(liquidPer.multiply(BigDecimal.valueOf(totalInstallments)));
    BigDecimal discountRemainder = discountTotal.subtract(discountPer.multiply(BigDecimal.valueOf(totalInstallments)));

    int index = 0;
    for (InstallmentErpEntity installment : erp.getInstallments().stream()
      .sorted(Comparator.comparing(InstallmentErpEntity::getInstallment, Comparator.nullsLast(Integer::compareTo)))
      .toList()) {
      index++;
      changed |= setIfDifferent(installment.getGrossValue(), index == 1 ? grossPer.add(grossRemainder) : grossPer, installment::setGrossValue);
      changed |= setIfDifferent(installment.getLiquidValue(), index == 1 ? liquidPer.add(liquidRemainder) : liquidPer, installment::setLiquidValue);
      changed |= setIfDifferent(installment.getDiscountValue(), index == 1 ? discountPer.add(discountRemainder) : discountPer, installment::setDiscountValue);
    }

    return changed;
  }

  private void saveOrUpdateAudit(
    TransactionErpEntity erp, TransactionAcqEntity acq, ContractedAcquirerRate contractedRate,
    BigDecimal appliedRate, BigDecimal appliedFee, BigDecimal expectedFee, BigDecimal difference) {
    ContractAuditEntity audit = new ContractAuditEntity();

    audit.setStatus(ContractAuditStatusEnum.DIVERGENT_RATE);
    audit.setCaptureCode(firstNonNull(acq.getCapture(), erp.getCapture()));
    audit.setModalityCode(firstNonNull(acq.getModality(), erp.getModality()));
    audit.setNsu(firstNonNull(acq.getNsu(), erp.getNsu()));
    audit.setAuthorization(firstNonNull(acq.getAuthorization(), erp.getAuthorization()));
    audit.setGrossValue(firstNonNull(acq.getGrossValue(), erp.getGrossValue()));
    audit.setLiquidValue(appliedLiquidValue(acq, erp, appliedFee));
    audit.setRateAcquirer(appliedRate);
    audit.setRateContract(contractedRate.rate());
    audit.setDiscountValue(appliedFee);
    audit.setExpectedDiscountValue(expectedFee);
    audit.setDifferenceValue(difference);
    audit.setFlag(firstNonNull(acq.getFlag(), erp.getFlag()));
    audit.setAcquirer(firstNonNull(acq.getAcquirer(), erp.getAcquirer()));
    audit.setContract(contractedRate.contract());
    audit.setCompany(firstNonNull(acq.getCompany(), erp.getCompany()));
    audit.setEstablishment(firstNonNull(acq.getEstablishment(), erp.getEstablishment()));
    audit.setTransactionAcq(acq);
    audit.setTransactionErp(erp);

    saveAuditAfterMainReconciliationCommit(audit);
  }

  private Optional<ContractedAcquirerRate> findRateForReconciliationCached(TransactionAcqEntity acq) {
    RateLookupKey key = RateLookupKey.from(acq);
    if (key == null) {
      return Optional.empty();
    }

    List<ContractEntity> candidates = reconciliationRateCache.get()
      .computeIfAbsent(key, ignored -> contractedAcquirerRateLookupService.findContractCandidates(
        key.companyId(), key.acquirerId(), key.establishmentId(), key.flagId(), key.modality()
      ));

    return contractedAcquirerRateLookupService.findRateFromCandidates(candidates, acq);
  }

  private void removeAudit(UUID transactionAcqId) {
    if (transactionAcqId == null) {
      return;
    }

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      contractAuditWriterService.deleteByTransactionAcqId(transactionAcqId);
      return;
    }

    PendingAuditBatch batch = pendingAuditBatch.get();
    batch.saves.remove(transactionAcqId);
    batch.deletes.add(transactionAcqId);
    registerAuditFlushOnce();
  }

  private void saveAuditAfterMainReconciliationCommit(ContractAuditEntity audit) {
    ContractAuditWriterService.ContractAuditCommand command = ContractAuditWriterService.ContractAuditCommand.from(audit);
    if (command.transactionAcqId() == null) {
      return;
    }

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      contractAuditWriterService.saveOrUpdate(command);
      return;
    }

    PendingAuditBatch batch = pendingAuditBatch.get();
    batch.deletes.remove(command.transactionAcqId());
    batch.saves.put(command.transactionAcqId(), command);
    registerAuditFlushOnce();
  }

  private void registerAuditFlushOnce() {
    if (Boolean.TRUE.equals(auditSynchronizationRegistered.get())) {
      return;
    }

    auditSynchronizationRegistered.set(Boolean.TRUE);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        flushPendingAuditBatch();
      }

      @Override
      public void afterCompletion(int status) {
        reconciliationRateCache.remove();
        pendingAuditBatch.remove();
        auditSynchronizationRegistered.remove();
      }
    });
  }

  private void flushPendingAuditBatch() {
    PendingAuditBatch batch = pendingAuditBatch.get();
    if (batch.isEmpty()) {
      return;
    }

    try {
      if (!batch.deletes.isEmpty()) {
        contractAuditWriterService.deleteByTransactionAcqIds(batch.deletes);
      }
      if (!batch.saves.isEmpty()) {
        contractAuditWriterService.saveOrUpdateAll(batch.saves.values());
      }
    } catch (Exception ex) {
      log.error("Falha ao gravar/remover auditoria de contrato após commit da conciliação. A venda já foi conciliada; a auditoria poderá ser regenerada na próxima análise.", ex);
    }
  }

  /**
   * Snapshot financeiro usado pela auditoria de contrato.

   * Regra principal: a cs_contract_audit deve refletir os valores reais já persistidos
   * em cs_transaction_acq. Por isso, para auditoria, priorizamos os campos escalares
   * da adquirente: mdrRate, discountValue e liquidValue. Os cálculos por parcelas ou
   * por gross-liquid ficam somente como fallback quando a venda da adquirente vier incompleta.
   */
  private AcquirerFeeSnapshot acquirerFeeSnapshot(TransactionAcqEntity acq) {
    if (acq == null) {
      return new AcquirerFeeSnapshot(ZERO, null, null);
    }

    BigDecimal gross = acq.getGrossValue();
    BigDecimal discountValue = firstPositive(
      acq.getDiscountValue(),
      discountFromGrossAndLiquid(acq),
      positive(acq.getMdrRate()) && gross != null ? calculateFee(gross, acq.getMdrRate()) : null
    );
    if (!positive(discountValue)) {
      BigDecimal fromInstallments = installmentAppliedFeeValue(acq);
      if (positive(fromInstallments)) discountValue = fromInstallments;
    }

    BigDecimal rateAcquirer = firstPositive(
      acq.getMdrRate(),
      calculateRate(gross, discountValue)
    );

    BigDecimal liquidValue = acq.getLiquidValue();
    if (liquidValue == null && gross != null && discountValue != null) {
      liquidValue = gross.subtract(discountValue);
    }

    return new AcquirerFeeSnapshot(nz(discountValue), rateAcquirer, liquidValue);
  }

  private BigDecimal calculateDifference(BigDecimal appliedFee, BigDecimal expectedFee) {
    // Positivo = adquirente cobrou mais desconto do que o contrato previa.
    // Negativo = adquirente cobrou menos desconto do que o contrato previa.
    return nz(appliedFee).subtract(nz(expectedFee));
  }

  private BigDecimal discountFromGrossAndLiquid(TransactionAcqEntity acq) {
    if (acq == null || acq.getGrossValue() == null || acq.getLiquidValue() == null) {
      return null;
    }
    return acq.getGrossValue().subtract(acq.getLiquidValue());
  }

  private BigDecimal firstPositive(BigDecimal... values) {
    if (values == null) return null;
    for (BigDecimal value : values) {
      if (positive(value)) return value;
    }
    return null;
  }

  private BigDecimal appliedRate(TransactionAcqEntity entity, BigDecimal gross, BigDecimal appliedFee) {
    if (entity == null) return null;

    // Regra principal: a taxa real da adquirente fica em cs_transaction_acq.mdr_rate.
    // Porém, em alguns layouts Rede (principalmente E-commerce 034/035/036), a transação
    // pode ter sido persistida com mdrRate=0, discountValue=0 e liquidValue=gross,
    // enquanto o desconto real está nas parcelas. Nesse caso, usamos as parcelas como fallback.
    if (positive(entity.getMdrRate())) return entity.getMdrRate();

    BigDecimal rateFromInstallments = calculateRate(gross, installmentAppliedFeeValue(entity));
    if (positive(rateFromInstallments)) return rateFromInstallments;

    return calculateRate(gross, appliedFee);
  }

  private BigDecimal calculateRate(BigDecimal gross, BigDecimal fee) {
    if (gross == null || gross.compareTo(ZERO) == 0 || fee == null) return null;
    return fee.multiply(HUNDRED).divide(gross, 4, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateFee(BigDecimal gross, BigDecimal rate) {
    if (gross == null || rate == null) return null;
    return gross.multiply(rate).divide(HUNDRED, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal appliedLiquidValue(TransactionAcqEntity acq, TransactionErpEntity erp, BigDecimal appliedFee) {
    if (acq != null && acq.getLiquidValue() != null) {
      return acq.getLiquidValue();
    }
    BigDecimal gross = acq != null ? acq.getGrossValue() : null;
    if (gross != null && appliedFee != null) {
      return gross.subtract(appliedFee);
    }
    return erp != null ? erp.getLiquidValue() : null;
  }

  private BigDecimal appliedFeeValue(TransactionAcqEntity entity) {
    if (entity == null) return ZERO;

    // Fonte principal: mdrRate da transação adquirente.
    // Se a taxa vier zerada/nula, tenta recuperar pelo desconto das parcelas.
    // Isso evita auditoria falsa com rate_acquirer=0 e discount_value=0 para vendas e-commerce.
    if (positive(entity.getMdrRate()) && entity.getGrossValue() != null) {
      return calculateFee(entity.getGrossValue(), entity.getMdrRate());
    }

    BigDecimal installmentFee = installmentAppliedFeeValue(entity);
    if (positive(installmentFee)) return installmentFee;

    if (positive(entity.getDiscountValue())) return entity.getDiscountValue();
    if (entity.getGrossValue() != null && entity.getLiquidValue() != null) {
      BigDecimal fromGrossLiquid = entity.getGrossValue().subtract(entity.getLiquidValue());
      if (positive(fromGrossLiquid)) return fromGrossLiquid;
    }
    return ZERO;
  }

  private BigDecimal installmentAppliedFeeValue(TransactionAcqEntity entity) {
    if (entity == null || entity.getInstallments() == null || entity.getInstallments().isEmpty()) {
      return ZERO;
    }

    return entity.getInstallments().stream()
      .map(InstallmentAcqEntity::getDiscountValue)
      .filter(Objects::nonNull)
      .reduce(ZERO, BigDecimal::add);
  }

  private boolean positive(BigDecimal value) {
    return value != null && value.compareTo(ZERO) > 0;
  }

  private String feeStatus(BigDecimal expectedRate, BigDecimal appliedRate, BigDecimal feeDifference) {
    if (expectedRate == null) return "MISSING_CONTRACT";
    if (isFeeOk(expectedRate, appliedRate, feeDifference)) return "OK";
    BigDecimal absRateDifference = expectedRate.subtract(nz(appliedRate)).abs();
    if (absRateDifference.compareTo(RATE_TOLERANCE) > 0) return "RATE_DIVERGENCE";
    return "VALUE_DIVERGENCE";
  }

  private boolean isFeeOk(BigDecimal expectedRate, BigDecimal appliedRate, BigDecimal feeDifference) {
    BigDecimal absFeeDifference = abs(feeDifference);
    if (absFeeDifference.compareTo(VALUE_TOLERANCE) <= 0) return true;
    BigDecimal absRateDifference = expectedRate.subtract(nz(appliedRate)).abs();
    return absRateDifference.compareTo(RATE_TOLERANCE) <= 0;
  }

  private boolean setIfDifferent(BigDecimal current, BigDecimal next, java.util.function.Consumer<BigDecimal> setter) {
    if (sameBigDecimal(current, next)) return false;
    setter.accept(next);
    return true;
  }

  private <T> boolean setIfDifferent(T current, T next, java.util.function.Consumer<T> setter) {
    if (Objects.equals(current, next)) return false;
    setter.accept(next);
    return true;
  }

  private boolean sameBigDecimal(BigDecimal left, BigDecimal right) {
    if (left == null && right == null) return true;
    if (left == null || right == null) return false;
    return left.compareTo(right) == 0;
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private BigDecimal abs(BigDecimal value) {
    return value == null ? ZERO : value.abs();
  }

  private String companyName(CompanyEntity company) {
    if (company == null) return null;
    return firstNonNull(company.getFantasyName(), company.getSocialReason(), company.getCnpj());
  }

  private String establishmentName(EstablishmentEntity establishment) {
    if (establishment == null) return null;
    return firstNonNull( establishment.getPvNumber() != null ? String.valueOf(establishment.getPvNumber()) : null);
  }

  private String acquirerName(AcquirerEntity acquirer) {
    return acquirer == null ? null : acquirer.getFantasyName();
  }

  private String flagName(FlagEntity flag) {
    return flag == null ? null : flag.getName();
  }

  private String modalityName(Integer modality) {
    return modality == null ? null : String.valueOf(modality);
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private record RateLookupKey(UUID companyId, UUID acquirerId, UUID establishmentId, UUID flagId, Integer modality) {
    static RateLookupKey from(TransactionAcqEntity tx) {
      if (tx == null || tx.getAcquirer() == null || tx.getFlag() == null || tx.getModality() == null) {
        return null;
      }

      return new RateLookupKey(
        tx.getCompany() == null ? null : tx.getCompany().getId(),
        tx.getAcquirer().getId(),
        tx.getEstablishment() == null ? null : tx.getEstablishment().getId(),
        tx.getFlag().getId(),
        tx.getModality()
      );
    }
  }

  private record AcquirerFeeSnapshot(
    BigDecimal discountValue,
    BigDecimal rateAcquirer,
    BigDecimal liquidValue
  ) {
  }

  private static final class PendingAuditBatch {
    private final Map<UUID, ContractAuditWriterService.ContractAuditCommand> saves = new LinkedHashMap<>();
    private final Set<UUID> deletes = new LinkedHashSet<>();

    private boolean isEmpty() {
      return saves.isEmpty() && deletes.isEmpty();
    }
  }

  public record FeeReconciliationResult(boolean erpChanged, boolean divergentRate, boolean missingValidContract) {
    static FeeReconciliationResult noChange() {
      return new FeeReconciliationResult(false, false, false);
    }
  }
}
