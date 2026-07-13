package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ManualBankReconciliationRequest;
import com.cardsync.bff.controller.v1.representation.model.conciliation.MarkLegacyReleasesRequest;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.ManualBankReconciliationResult;
import com.cardsync.core.reconciliation.ManualBankReconciliationService;
import com.cardsync.core.reconciliation.MarkLegacyResult;
import com.cardsync.core.reconciliation.UndoBankReconciliationResult;
import com.cardsync.core.security.CheckSecurity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/bank-reconciliation")
public class ManualBankReconciliationController {

    private final BankReconciliationService bankReconciliationService;
    private final ManualBankReconciliationService manualBankReconciliationService;

    @PostMapping("/manual")
    @CheckSecurity.FileProcessing.CanProcess
    public ManualBankReconciliationResult reconcile(@Valid @RequestBody ManualBankReconciliationRequest request) {
        return manualBankReconciliationService.reconcile(request.releaseBankId(), request.creditOrderIds());
    }

    @PostMapping("/legacy")
    @CheckSecurity.FileProcessing.CanProcess
    public MarkLegacyResult markLegacy(@Valid @RequestBody MarkLegacyReleasesRequest request) {
        return manualBankReconciliationService.markLegacy(request.releaseBankIds());
    }

    @PostMapping("/undo/{releaseBankId}")
    @CheckSecurity.FileProcessing.CanProcess
    public UndoBankReconciliationResult undo(@PathVariable UUID releaseBankId) {
        return bankReconciliationService.undoReconciliation(releaseBankId);
    }
}
