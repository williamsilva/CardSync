package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ApplyNoCreditOrderLegacyMarkingRequest;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ApplyPreImplantationDivergenceRequest;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ManualBankReconciliationRequest;
import com.cardsync.bff.controller.v1.representation.model.conciliation.MarkLegacyReleasesRequest;
import com.cardsync.core.file.bank.BankStatementAcquirerReclassificationService;
import com.cardsync.core.file.bank.BankStatementEstablishmentReclassificationService;
import com.cardsync.core.file.bank.BankStatementFlagReclassificationService;
import com.cardsync.core.file.bank.BankStatementModalityReclassificationService;
import com.cardsync.core.file.bank.ReclassifyBankStatementAcquirerResult;
import com.cardsync.core.file.bank.ReclassifyBankStatementEstablishmentResult;
import com.cardsync.core.file.bank.ReclassifyBankStatementFlagsResult;
import com.cardsync.core.file.bank.ReclassifyBankStatementModalityResult;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.core.reconciliation.ManualBankReconciliationResult;
import com.cardsync.core.reconciliation.ManualBankReconciliationService;
import com.cardsync.core.reconciliation.MarkLegacyResult;
import com.cardsync.core.reconciliation.NoCreditOrderLegacyApplyResult;
import com.cardsync.core.reconciliation.NoCreditOrderLegacyMarkingService;
import com.cardsync.core.reconciliation.NoCreditOrderLegacyPreviewResult;
import com.cardsync.core.reconciliation.PreImplantationDivergenceApplyResult;
import com.cardsync.core.reconciliation.PreImplantationDivergencePreviewResult;
import com.cardsync.core.reconciliation.PreImplantationDivergenceReconciliationService;
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
    private final BankStatementFlagReclassificationService bankStatementFlagReclassificationService;
    private final BankStatementModalityReclassificationService bankStatementModalityReclassificationService;
    private final BankStatementAcquirerReclassificationService bankStatementAcquirerReclassificationService;
    private final BankStatementEstablishmentReclassificationService bankStatementEstablishmentReclassificationService;
    private final PreImplantationDivergenceReconciliationService preImplantationDivergenceReconciliationService;
    private final NoCreditOrderLegacyMarkingService noCreditOrderLegacyMarkingService;

    @PostMapping("/manual")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public ManualBankReconciliationResult reconcile(@Valid @RequestBody ManualBankReconciliationRequest request) {
        return manualBankReconciliationService.reconcile(
            request.releaseBankId(), request.creditOrderIds(), request.divergenceReason()
        );
    }

    @PostMapping("/legacy")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public MarkLegacyResult markLegacy(@Valid @RequestBody MarkLegacyReleasesRequest request) {
        return manualBankReconciliationService.markLegacy(request.releaseBankIds());
    }

    @PostMapping("/undo/{releaseBankId}")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public UndoBankReconciliationResult undo(@PathVariable UUID releaseBankId) {
        return bankReconciliationService.undoReconciliation(releaseBankId);
    }

    /**
     * Backfill único: reclassifica a bandeira de todos os lançamentos bancários CNAB240 já
     * importados usando a lógica corrigida de BankStatementClassifierService.resolveFlag
     * (antes casava por erp_code como substring numérica solta, gerando falsos positivos como
     * Cabal/Banescard virando American Express).
     */
    @PostMapping("/reclassify-flags")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public ReclassifyBankStatementFlagsResult reclassifyFlags() {
        return bankStatementFlagReclassificationService.reclassifyAll();
    }

    /**
     * Backfill único: reclassifica a modalidade (débito/crédito) dos lançamentos bancários de
     * recebimento já importados com modalidade não classificada (invisíveis no Extrato Bancário
     * até serem corrigidos — ver BankStatementModalityReclassificationService).
     */
    @PostMapping("/reclassify-modality")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public ReclassifyBankStatementModalityResult reclassifyModality() {
        return bankStatementModalityReclassificationService.reclassifyAll();
    }

    /**
     * Backfill único: vincula o adquirente dos lançamentos bancários de recebimento já
     * importados sem adquirente resolvido (ver BankStatementAcquirerReclassificationService).
     * Rode antes de "reclassify-establishment": a resolução de estabelecimento usa o adquirente
     * já vinculado do lançamento para restringir a busca por PV.
     */
    @PostMapping("/reclassify-acquirer")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public ReclassifyBankStatementAcquirerResult reclassifyAcquirer() {
        return bankStatementAcquirerReclassificationService.reclassifyAll();
    }

    /**
     * Backfill único: vincula o estabelecimento dos lançamentos bancários de recebimento já
     * importados sem estabelecimento resolvido (ver BankStatementEstablishmentReclassificationService).
     */
    @PostMapping("/reclassify-establishment")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public ReclassifyBankStatementEstablishmentResult reclassifyEstablishment() {
        return bankStatementEstablishmentReclassificationService.reclassifyAll();
    }

    /**
     * Análise (não grava nada): lista os lançamentos pendentes cuja soma de ordens de crédito
     * candidatas disponíveis fica abaixo do valor do lançamento — padrão de vendas anteriores à
     * implantação sem ordem no sistema — e que seriam vinculados por {@link #applyPreImplantationDivergence}.
     */
    @PostMapping("/pre-implantation-divergence/preview")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public PreImplantationDivergencePreviewResult previewPreImplantationDivergence() {
        return preImplantationDivergenceReconciliationService.preview();
    }

    /**
     * Executa de fato: vincula, com a justificativa padrão de divergência pré-implantação, os
     * lançamentos elegíveis (recalculado no momento da execução, não reusa o preview). Sem
     * releaseBankIds no corpo (ou lista vazia), aplica a todos os elegíveis; com a lista, restringe
     * à seleção feita na prévia.
     */
    @PostMapping("/pre-implantation-divergence/apply")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public PreImplantationDivergenceApplyResult applyPreImplantationDivergence(
        @RequestBody(required = false) ApplyPreImplantationDivergenceRequest request
    ) {
        return preImplantationDivergenceReconciliationService.apply(
            request != null ? request.releaseBankIds() : null
        );
    }

    /**
     * Análise (não grava nada): lista os lançamentos pendentes sem NENHUMA ordem de crédito
     * candidata e dentro da janela de legado — que seriam marcados como legado por
     * {@link #applyNoCreditOrderLegacyMarking}.
     */
    @PostMapping("/no-credit-order-legacy/preview")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public NoCreditOrderLegacyPreviewResult previewNoCreditOrderLegacyMarking() {
        return noCreditOrderLegacyMarkingService.preview();
    }

    /**
     * Executa de fato: marca como legado os lançamentos elegíveis (recalculado no momento da
     * execução, não reusa o preview). Sem releaseBankIds no corpo (ou lista vazia), aplica a
     * todos os elegíveis; com a lista, restringe à seleção feita na prévia.
     */
    @PostMapping("/no-credit-order-legacy/apply")
    @CheckSecurity.Reconciliation.ManualBankReconciliation.CanProcess
    public NoCreditOrderLegacyApplyResult applyNoCreditOrderLegacyMarking(
        @RequestBody(required = false) ApplyNoCreditOrderLegacyMarkingRequest request
    ) {
        return noCreditOrderLegacyMarkingService.apply(
            request != null ? request.releaseBankIds() : null
        );
    }
}
