package com.cardsync.core.reconciliation;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualBankReconciliationService {

    private final ReleasesBankRepository releasesBankRepository;
    private final CreditOrderRepository creditOrderRepository;
    private final SalesSummaryRepository salesSummaryRepository;
    private final com.cardsync.core.conciliation.ReconciliationSettingsService reconciliationSettingsService;

    @Transactional
    public ManualBankReconciliationResult reconcile(UUID releaseBankId, List<UUID> creditOrderIds, String divergenceReason) {
        int zeroValueReconciled = reconcileZeroValueOrders();

        ReleasesBankEntity release = releasesBankRepository.findById(releaseBankId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "bank.release.not.found: " + releaseBankId));

        List<CreditOrderEntity> orders = creditOrderRepository.findAllById(creditOrderIds);

        if (orders.size() != creditOrderIds.size()) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND, "credit.order.not.found");
        }

        int reconciled = 0;
        int alreadyReconciled = 0;
        List<CreditOrderEntity> toSave = new ArrayList<>();

        for (CreditOrderEntity order : orders) {
            if (order.getReleaseBank() != null) {
                alreadyReconciled++;
                continue;
            }
            order.setReleaseBank(release);
            order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
            order.setReconciliationStatus(BankReconciliationStatus.RECONCILED.getCode());
            order.setCreditStatus(BankReconciliationStatus.RECONCILED.getCode());
            toSave.add(order);
            reconciled++;
        }

        BigDecimal divergenceValue = null;

        if (!toSave.isEmpty()) {
            // Nunca silencioso: um lançamento que mistura vendas anteriores à implantação (sem
            // CreditOrder no sistema) com vendas atuais nunca vai bater exato — mas o vínculo só
            // pode seguir em frente com uma justificativa explícita, validada de novo aqui (defesa
            // em profundidade — o frontend já exige isso, mas a API não pode confiar só nisso).
            BigDecimal sumOrders = orders.stream()
                    .map(CreditOrderEntity::getReleaseValue)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal releaseValue = release.getReleaseValue() != null ? release.getReleaseValue() : BigDecimal.ZERO;
            BigDecimal diff = releaseValue.subtract(sumOrders).abs();
            BigDecimal tolerance = reconciliationSettingsService.getValueTolerance();

            if (diff.compareTo(tolerance) > 0) {
                if (divergenceReason == null || divergenceReason.isBlank()) {
                    throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "manual.reconciliation.divergence.reason.required");
                }
                divergenceValue = diff;
                release.setDivergenceValue(diff);
                release.setDivergenceReason(divergenceReason.trim());
            } else {
                release.setDivergenceValue(null);
                release.setDivergenceReason(null);
            }

            creditOrderRepository.saveAll(toSave);
            release.setReconciliationStatus(StatusPaymentBankEnum.PAID);
            releasesBankRepository.save(release);
            updateSalesSummaryStatuses(toSave);
        }

        log.info("Conciliação manual bancária: lançamento={}, conciliadas={}, já conciliadas={}, valor_zero={}, divergencia={}",
                releaseBankId, reconciled, alreadyReconciled, zeroValueReconciled, divergenceValue);

        return new ManualBankReconciliationResult(reconciled, alreadyReconciled, zeroValueReconciled, divergenceValue);
    }

    /**
     * Marca lançamentos bancários como legado (liquidações de vendas anteriores à
     * implantação do sistema). Elegibilidade por lançamento: somente pendentes com
     * data de lançamento até go-live + N meses (configuração de conciliação) são
     * marcados; os demais são ignorados para não sobrescrever conciliações nem
     * marcar lançamentos fora da janela de legado.
     */
    @Transactional
    public MarkLegacyResult markLegacy(List<UUID> releaseBankIds) {
        java.time.LocalDate cutoffDate = reconciliationSettingsService.getLegacyMarkingCutoffDate();

        List<ReleasesBankEntity> releases = releasesBankRepository.findAllById(releaseBankIds);

        if (releases.size() != releaseBankIds.size()) {
            throw BusinessException.notFound(ErrorCode.NOT_FOUND, "bank.release.not.found");
        }

        int updated = 0;
        int skipped = 0;
        List<ReleasesBankEntity> toSave = new ArrayList<>();

        for (ReleasesBankEntity release : releases) {
            if (release.getReconciliationStatus() != StatusPaymentBankEnum.PENDING
                    || !isEligibleForLegacy(release, cutoffDate)) {
                skipped++;
                continue;
            }
            release.setReconciliationStatus(StatusPaymentBankEnum.LEGACY);
            toSave.add(release);
            updated++;
        }

        if (!toSave.isEmpty()) {
            releasesBankRepository.saveAll(toSave);
        }

        log.info("Marcação de lançamentos como legado: solicitados={}, marcados={}, ignorados={}",
                releaseBankIds.size(), updated, skipped);

        return new MarkLegacyResult(updated, skipped);
    }

    /**
     * Um lançamento é elegível para legado quando sua data de lançamento é até a
     * data-limite (go-live + N meses), inclusive. Sem data-limite configurada,
     * qualquer lançamento é elegível; sem data de lançamento, não é possível
     * verificar a elegibilidade e o lançamento é ignorado.
     * Visibilidade de pacote (não private) para reuso em NoCreditOrderLegacyMarkingService.
     */
    boolean isEligibleForLegacy(ReleasesBankEntity release, java.time.LocalDate cutoffDate) {
        if (cutoffDate == null) return true;
        java.time.LocalDate releaseDate = release.getReleaseDate();
        return releaseDate != null && !releaseDate.isAfter(cutoffDate);
    }

    private int reconcileZeroValueOrders() {
        List<CreditOrderEntity> zeroValueOrders = creditOrderRepository
                .findPendingZeroValueOrders(StatusPaymentBankEnum.PENDING.getCode(), BigDecimal.ZERO);

        if (zeroValueOrders.isEmpty()) return 0;

        for (CreditOrderEntity order : zeroValueOrders) {
            order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
            order.setReconciliationStatus(BankReconciliationStatus.RECONCILED.getCode());
            order.setCreditStatus(BankReconciliationStatus.RECONCILED.getCode());
        }

        creditOrderRepository.saveAll(zeroValueOrders);
        updateSalesSummaryStatuses(zeroValueOrders);

        log.info("Conciliação automática de ordens com valor zero: {} ordens conciliadas", zeroValueOrders.size());

        return zeroValueOrders.size();
    }

    private void updateSalesSummaryStatuses(List<CreditOrderEntity> reconciledOrders) {
        Map<UUID, SalesSummaryEntity> summaries = new LinkedHashMap<>();
        for (CreditOrderEntity order : reconciledOrders) {
            SalesSummaryEntity summary = order.getSalesSummary();
            if (summary == null) continue;
            summaries.put(summary.getId(), summary);
        }

        for (SalesSummaryEntity summary : summaries.values()) {
            boolean allPaid = summary.getCreditOrders().stream()
                    .allMatch(co -> StatusPaymentBankEnum.PAID.equals(co.getStatusPaymentBank()));

            if (allPaid) {
                summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
                summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
            } else {
                summary.setCreditOrderStatus(StatusReconciliationEnum.PARTIALLY_RECONCILED);
            }
            salesSummaryRepository.save(summary);
        }
    }
}
