package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.ReleasesBankEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ferramenta de análise irmã de {@link PreImplantationDivergenceReconciliationService}, pro
 * cenário oposto: um lançamento bancário pendente sem NENHUMA ordem de crédito candidata — ou
 * seja, não tem o que vincular de jeito nenhum (nem com divergência) — e cuja data já está
 * dentro da janela de legado (go-live + N meses configurado). Hoje isso é feito selecionando o
 * lançamento manualmente na tela e clicando "Marcar como Legado".
 *
 * Reaproveita a MESMA busca de candidatas ({@link CreditOrderCandidateFinder}) e o MESMO caminho
 * de marcação ({@link ManualBankReconciliationService#markLegacy}) — não é uma reimplementação
 * paralela.
 *
 * Regras (mesmo padrão de PreImplantationDivergenceReconciliationService):
 * - Só marca quando NÃO existe nenhuma ordem de crédito candidata (se existir alguma, mesmo que
 *   não feche o valor, o caso é de divergência pré-implantação, não de legado — fica de fora
 *   pra revisão manual/pra ferramenta de divergência).
 * - Só marca lançamentos dentro da janela de legado (mesma regra de
 *   {@link ManualBankReconciliationService#isEligibleForLegacy}) — fora da janela fica de fora.
 * - {@link #preview()} nunca grava nada; só {@link #apply} executa de fato.
 * - Escopo restrito a lançamentos pendentes de recebimento por cartão (ver
 *   {@link PendingReceiptReleaseFinder}) — PIX, TED, SISPAG e afins nunca terão ordem de crédito
 *   candidata, então nem entram na análise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoCreditOrderLegacyMarkingService {

  private final PendingReceiptReleaseFinder pendingReceiptReleaseFinder;
  private final CreditOrderCandidateFinder creditOrderCandidateFinder;
  private final ManualBankReconciliationService manualBankReconciliationService;
  private final com.cardsync.core.conciliation.ReconciliationSettingsService reconciliationSettingsService;

  @Transactional(readOnly = true)
  public NoCreditOrderLegacyPreviewResult preview() {
    CreditOrderMatchSettings settings = CreditOrderMatchSettings.from(reconciliationSettingsService);
    LocalDate cutoffDate = reconciliationSettingsService.getLegacyMarkingCutoffDate();
    List<ReleasesBankEntity> pendingReleases = pendingReceiptReleaseFinder.find();

    int eligible = 0;
    int skippedHasCandidates = 0;
    int skippedOutsideWindow = 0;
    List<NoCreditOrderLegacyCandidate> candidates = new ArrayList<>();

    for (ReleasesBankEntity release : pendingReleases) {
      ReleaseAnalysis analysis = analyze(release, settings, cutoffDate);
      switch (analysis.outcome()) {
        case ELIGIBLE -> {
          eligible++;
          candidates.add(analysis.toCandidate());
        }
        case SKIPPED_HAS_CANDIDATES -> skippedHasCandidates++;
        case SKIPPED_OUTSIDE_WINDOW -> skippedOutsideWindow++;
      }
    }

    return new NoCreditOrderLegacyPreviewResult(
      pendingReleases.size(), eligible, skippedHasCandidates, skippedOutsideWindow, candidates
    );
  }

  /**
   * @param releaseBankIds quando nulo/vazio, aplica a todos os lançamentos elegíveis; quando
   *                       informado, restringe aos IDs dados (seleção manual feita na prévia) —
   *                       cada um ainda é recalculado aqui, não reusa o resultado do preview.
   */
  @Transactional
  public NoCreditOrderLegacyApplyResult apply(List<UUID> releaseBankIds) {
    CreditOrderMatchSettings settings = CreditOrderMatchSettings.from(reconciliationSettingsService);
    LocalDate cutoffDate = reconciliationSettingsService.getLegacyMarkingCutoffDate();
    List<ReleasesBankEntity> pendingReleases = pendingReceiptReleaseFinder.find();

    if (releaseBankIds != null && !releaseBankIds.isEmpty()) {
      Set<UUID> selected = new HashSet<>(releaseBankIds);
      pendingReleases = pendingReleases.stream().filter(r -> selected.contains(r.getId())).toList();
    }

    List<UUID> toMark = new ArrayList<>();
    int skippedHasCandidates = 0;
    int skippedOutsideWindow = 0;

    for (ReleasesBankEntity release : pendingReleases) {
      ReleaseAnalysis analysis = analyze(release, settings, cutoffDate);
      switch (analysis.outcome()) {
        case ELIGIBLE -> toMark.add(release.getId());
        case SKIPPED_HAS_CANDIDATES -> skippedHasCandidates++;
        case SKIPPED_OUTSIDE_WINDOW -> skippedOutsideWindow++;
      }
    }

    int marked = toMark.isEmpty() ? 0 : manualBankReconciliationService.markLegacy(toMark).updated();

    log.info(
      "🔄 Marcação automática de legado (sem ordem de crédito): analisados={}, marcados={}, com_ordens_candidatas={}, fora_da_janela_legado={}",
      pendingReleases.size(), marked, skippedHasCandidates, skippedOutsideWindow
    );

    return new NoCreditOrderLegacyApplyResult(pendingReleases.size(), marked, skippedHasCandidates, skippedOutsideWindow);
  }

  private ReleaseAnalysis analyze(ReleasesBankEntity release, CreditOrderMatchSettings settings, LocalDate cutoffDate) {
    if (!creditOrderCandidateFinder.findCompatible(release, settings).isEmpty()) {
      return ReleaseAnalysis.skippedHasCandidates();
    }
    if (!manualBankReconciliationService.isEligibleForLegacy(release, cutoffDate)) {
      return ReleaseAnalysis.skippedOutsideWindow();
    }
    return ReleaseAnalysis.eligible(release);
  }

  private enum Outcome { ELIGIBLE, SKIPPED_HAS_CANDIDATES, SKIPPED_OUTSIDE_WINDOW }

  private record ReleaseAnalysis(Outcome outcome, ReleasesBankEntity release) {
    static ReleaseAnalysis eligible(ReleasesBankEntity release) {
      return new ReleaseAnalysis(Outcome.ELIGIBLE, release);
    }

    static ReleaseAnalysis skippedHasCandidates() {
      return new ReleaseAnalysis(Outcome.SKIPPED_HAS_CANDIDATES, null);
    }

    static ReleaseAnalysis skippedOutsideWindow() {
      return new ReleaseAnalysis(Outcome.SKIPPED_OUTSIDE_WINDOW, null);
    }

    NoCreditOrderLegacyCandidate toCandidate() {
      return new NoCreditOrderLegacyCandidate(
        release.getId(),
        release.getCompany() != null ? release.getCompany().getFantasyName() : null,
        release.getAcquirer() != null ? release.getAcquirer().getFantasyName() : null,
        release.getReleaseDate(), release.getReleaseValue()
      );
    }
  }
}
