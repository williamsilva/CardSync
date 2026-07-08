package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ManualBankReconciliationRequest;
import com.cardsync.core.reconciliation.ManualBankReconciliationResult;
import com.cardsync.core.reconciliation.ManualBankReconciliationService;
import com.cardsync.core.security.CheckSecurity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/bank-reconciliation")
public class ManualBankReconciliationController {

    private final ManualBankReconciliationService manualBankReconciliationService;

    @PostMapping("/manual")
    @CheckSecurity.FileProcessing.CanProcess
    public ManualBankReconciliationResult reconcile(@Valid @RequestBody ManualBankReconciliationRequest request) {
        return manualBankReconciliationService.reconcile(request.releaseBankId(), request.creditOrderIds());
    }
}
