package com.cardsync.core.reconciliation;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationStrictModeImpactModel;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diagnóstico sob demanda do impacto dos toggles de matching rígido (Etapa 7), para
 * consulta manual antes de ligar cada um em produção — ver
 * ReconciliationSettingsEntity.flagMatchRequired/establishmentMatchRequired/paymentKindMatchRequired.
 * Mesmas contagens já logadas ao final de cada execução de
 * {@link BankReconciliationService#reconcilePending()}, expostas aqui via endpoint para não
 * depender de esperar o próximo ciclo do scheduler.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationStrictModeDiagnosticsService {

  private static final int PAYMENT_PENDING = StatusPaymentBankEnum.PENDING.getCode();
  private static final int PAYMENT_PARTIAL = StatusPaymentBankEnum.PARTIALLY_PAID.getCode();
  private static final int STATUS_PENDING = BankReconciliationStatus.PENDING.getCode();
  private static final int SUMMARY_RECONCILED_STATUS = StatusReconciliationEnum.RECONCILED.getCode();

  private final CreditOrderRepository creditOrderRepository;
  private final ReleasesBankRepository releasesBankRepository;

  @Transactional(readOnly = true)
  public ReconciliationStrictModeImpactModel getImpact() {
    return new ReconciliationStrictModeImpactModel(
      creditOrderRepository.countEligiblePendingWithoutFlag(SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL),
      releasesBankRepository.countPendingWithoutFlag(STATUS_PENDING),
      creditOrderRepository.countEligiblePendingWithoutPvCentralizer(SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL),
      releasesBankRepository.countPendingWithoutEstablishment(STATUS_PENDING),
      creditOrderRepository.countEligiblePendingWithUnknownPaymentKind(SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL)
    );
  }
}
