package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankReconciliationStatus {

  PENDING(StatusPaymentBankEnum.PENDING.getCode(), "Pendente"),
  RECONCILED(StatusPaymentBankEnum.PAID.getCode(), "Conciliado"),
  NOT_RECONCILED(StatusPaymentBankEnum.NOT_PAID.getCode(), "Não conciliado"),
  DIVERGENT(StatusPaymentBankEnum.DIVERGENT.getCode(), "Divergente"),
  INSTALLMENT_RECONCILED(StatusInstallmentEnum.RECONCILED.getCode(), "Parcela conciliada"),
  INSTALLMENT_DIVERGENT(StatusInstallmentEnum.DIVERGENT.getCode(), "Parcela divergente");

  private final int code;
  private final String description;
}
