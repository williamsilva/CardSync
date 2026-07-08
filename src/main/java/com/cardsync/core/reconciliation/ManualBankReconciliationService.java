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

    @Transactional
    public ManualBankReconciliationResult reconcile(UUID releaseBankId, List<UUID> creditOrderIds) {
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

        if (!toSave.isEmpty()) {
            creditOrderRepository.saveAll(toSave);
            release.setReconciliationStatus(StatusPaymentBankEnum.PAID);
            releasesBankRepository.save(release);
            updateSalesSummaryStatuses(toSave);
        }

        log.info("Conciliação manual bancária: lançamento={}, conciliadas={}, já conciliadas={}",
                releaseBankId, reconciled, alreadyReconciled);

        return new ManualBankReconciliationResult(reconciled, alreadyReconciled);
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
