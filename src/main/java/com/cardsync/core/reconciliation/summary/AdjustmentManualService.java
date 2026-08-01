package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.input.AdjustmentManualInput;
import com.cardsync.core.file.service.FileLookupService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentManualService {

  private final FileLookupService fileLookupService;
  private final AcquirerRepository acquirerRepository;
  private final CompanyRepository companyRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final CreditOrderRepository creditOrderRepository;

  @Transactional
  public AdjustmentEntity create(AdjustmentManualInput input) {
    if (input.rawAdjustmentCode() != null && !input.rawAdjustmentCode().isBlank()
      && adjustmentRepository.existsByRawAdjustmentCodeAndRvNumberOriginal(input.rawAdjustmentCode(), input.rvNumberOriginal())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Ajuste já importado anteriormente: " + input.rawAdjustmentCode()
      );
    }

    AcquirerEntity acquirer = acquirerRepository.findById(UUID.fromString(input.acquirerId()))
      .orElseThrow(() -> new IllegalStateException("Adquirente não encontrada: " + input.acquirerId()));
    EstablishmentEntity establishment = safeEstablishment(input.pvNumber());
    CompanyEntity company = resolveCompany(input, establishment);
    SalesSummaryEntity salesSummary = safeSalesSummary(acquirer.getId(), input.pvNumber(), input.rvNumberOriginal());

    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setAcquirer(acquirer);
    adjustment.setCompany(company);
    adjustment.setEstablishment(establishment);
    adjustment.setSalesSummary(salesSummary);
    adjustment.setPvNumber(input.pvNumber());
    adjustment.setPvNumberOriginal(input.pvNumberOriginal());
    adjustment.setRvNumberOriginal(input.rvNumberOriginal());
    adjustment.setAdjustmentDate(input.adjustmentDate());
    adjustment.setCreditDate(input.creditDate());
    adjustment.setAdjustmentValue(input.adjustmentValue());
    adjustment.setTransactionValue(input.transactionValue());
    adjustment.setTotalDebitValue(input.totalDebitValue());
    adjustment.setPendingValue(input.pendingValue());
    adjustment.setDebitType(normalizeDebitType(input.debitType()));
    adjustment.setAdjustmentType(input.adjustmentType());
    adjustment.setAdjustmentDescription(input.adjustmentDescription());
    adjustment.setRawAdjustmentCode(input.rawAdjustmentCode());
    adjustment.setRvFlagAdjustment(safeFlag(input.flagName()));

    AdjustmentEntity saved = adjustmentRepository.save(adjustment);

    if (salesSummary != null && "D".equals(saved.getDebitType()) && saved.getAdjustmentValue() != null) {
      recomputeEligibleCreditOrders(salesSummary, saved.getAdjustmentValue());
    }

    log.info(
      "✅ Ajuste manual criado: id={}, pv={}, rvOriginal={}, adjustmentDate={}, adjustmentValue={}, salesSummaryVinculado={}",
      saved.getId(), saved.getPvNumber(), saved.getRvNumberOriginal(), saved.getAdjustmentDate(),
      saved.getAdjustmentValue(), salesSummary != null
    );

    return saved;
  }

  /**
   * Um ajuste importado depois que a ordem de crédito já existe nunca reduzia releaseValue —
   * o desconto só acontecia no momento da geração (ver CreditOrderManualService/
   * SalesSummaryCreditOrderReconciliationService). Recalcula aqui as ordens ainda pendentes e
   * sem lançamento bancário vinculado (nunca as já pagas/conciliadas, pra não desfazer uma
   * conciliação real já feita) — dividindo o valor do ajuste igualmente entre as parcelas do
   * resumo (mesmo tratamento usado na geração), já que ele se refere ao resumo inteiro, não a
   * uma parcela específica.
   */
  private void recomputeEligibleCreditOrders(SalesSummaryEntity salesSummary, BigDecimal adjustmentValue) {
    List<CreditOrderEntity> eligibleOrders = creditOrderRepository.findBySalesSummary_Id(salesSummary.getId()).stream()
      .filter(order -> StatusPaymentBankEnum.PENDING.equals(order.getStatusPaymentBank()) && order.getReleaseBank() == null)
      .toList();

    if (eligibleOrders.isEmpty()) {
      return;
    }

    for (CreditOrderEntity order : eligibleOrders) {
      int installmentTotal = order.getInstallmentTotal() != null && order.getInstallmentTotal() > 0
        ? order.getInstallmentTotal()
        : 1;
      BigDecimal share = installmentTotal > 1
        ? adjustmentValue.divide(BigDecimal.valueOf(installmentTotal), 2, RoundingMode.DOWN)
        : adjustmentValue;
      BigDecimal current = order.getReleaseValue() != null ? order.getReleaseValue() : BigDecimal.ZERO;
      order.setReleaseValue(current.subtract(share));
    }
    creditOrderRepository.saveAll(eligibleOrders);

    log.info(
      "🔄 Ordem(ns) de crédito recalculada(s) após ajuste manual: salesSummary={}, ordens={}, ajuste={}",
      salesSummary.getId(), eligibleOrders.size(), adjustmentValue
    );
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    try {
      return fileLookupService.establishmentByPvNumber(pvNumber);
    } catch (IllegalStateException e) {
      log.warn("⚠️ Estabelecimento não encontrado para PV {}: ajuste criado sem vínculo de estabelecimento.", pvNumber);
      return null;
    }
  }

  private SalesSummaryEntity safeSalesSummary(UUID acquirerId, Integer pvNumber, Integer rvNumber) {
    return salesSummaryRepository
      .findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirerId, pvNumber, rvNumber)
      .orElse(null);
  }

  private FlagEntity safeFlag(String flagName) {
    if (flagName == null || flagName.isBlank()) return null;
    try {
      return fileLookupService.flagByName(flagName);
    } catch (IllegalStateException e) {
      log.warn("⚠️ Bandeira não encontrada para '{}': ajuste criado sem vínculo de bandeira.", flagName);
      return null;
    }
  }

  private CompanyEntity resolveCompany(AdjustmentManualInput input, EstablishmentEntity establishment) {
    if (input.companyId() != null && !input.companyId().isBlank()) {
      return companyRepository.findById(UUID.fromString(input.companyId()))
        .orElseGet(() -> establishment != null ? establishment.getCompany() : null);
    }
    return establishment != null ? establishment.getCompany() : null;
  }

  private static String normalizeDebitType(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (normalized.startsWith("D") || normalized.startsWith("C")) {
      return normalized.substring(0, 1);
    }
    return raw.trim();
  }
}
