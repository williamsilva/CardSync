package com.cardsync.bff.controller.v1;

import com.cardsync.core.reconciliation.BankReconciliationResult;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.BankReconciliationTriggerType;
import com.cardsync.core.security.CheckSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation/bank-acquirer")
public class BankAcquirerConciliationController {

  private final BankReconciliationService bankReconciliationService;

  @PostMapping("/reconcile")
  @CheckSecurity.FileProcessing.CanProcess
  public BankReconciliationResult reconcile() {
    return bankReconciliationService.reconcilePending(BankReconciliationTriggerType.MANUAL);
  }
}
