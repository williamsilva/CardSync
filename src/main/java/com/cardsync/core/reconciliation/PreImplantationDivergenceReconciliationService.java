package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backfill/ferramenta de análise para o cenário descrito na tela de Conciliação Manual Bancária:
 * um lançamento bancário cuja soma de ordens de crédito disponíveis fica ABAIXO do valor do
 * lançamento porque parte das vendas que ele liquida são anteriores à implantação do sistema
 * (nunca existirão como ordem de crédito) — hoje resolvido manualmente selecionando todas as
 * ordens candidatas do mesmo contexto e vinculando com a justificativa de divergência.
 *
 * Reaproveita a MESMA definição de "candidata compatível" do matcher automático via
 * {@link CreditOrderCandidateFinder} e o MESMO caminho de vínculo com divergência
 * ({@link ManualBankReconciliationService#reconcile}) — não é uma reimplementação paralela.
 *
 * Regras de segurança (definidas com o usuário, não infira/mude sem confirmar):
 * - Só vincula quando TODAS as ordens candidatas disponíveis somadas ficam ABAIXO do valor do
 *   lançamento (diferença positiva) — nunca quando a soma ultrapassa o lançamento além da
 *   tolerância configurada (isso não é explicável por "venda pré-implantação sem ordem": indica
 *   ordem errada/duplicada, e fica de fora para revisão manual).
 * - Sem teto de valor pra diferença aceita — o acumulado de vendas pré-implantação pode ser
 *   grande.
 * - {@link #preview()} nunca grava nada; só {@link #apply} executa de fato, processando os
 *   lançamentos um a um (não em lote pré-computado) para que uma ordem já vinculada a um
 *   lançamento anterior nesta mesma execução não seja candidatada de novo para outro.
 * - Escopo restrito a lançamentos pendentes de recebimento por cartão (ver
 *   {@link PendingReceiptReleaseFinder}) — PIX, TED, SISPAG e afins nunca terão ordem de crédito
 *   candidata, então nem entram na análise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreImplantationDivergenceReconciliationService {

  private static final String STANDARD_DIVERGENCE_REASON =
    "Lançamento inclui vendas anteriores à implantação, sem ordem de crédito no sistema";

  private final CreditOrderCandidateFinder creditOrderCandidateFinder;
  private final PendingReceiptReleaseFinder pendingReceiptReleaseFinder;
  private final ManualBankReconciliationService manualBankReconciliationService;
  private final com.cardsync.core.conciliation.ReconciliationSettingsService reconciliationSettingsService;

  @Transactional(readOnly = true)
  public PreImplantationDivergencePreviewResult preview() {
    CreditOrderMatchSettings settings = CreditOrderMatchSettings.from(reconciliationSettingsService);
    List<ReleasesBankEntity> pendingReleases = pendingReceiptReleaseFinder.find();

    int eligible = 0;
    int skippedNegative = 0;
    int skippedNoCandidates = 0;
    List<PreImplantationDivergenceCandidate> candidates = new ArrayList<>();

    for (ReleasesBankEntity release : pendingReleases) {
      ReleaseAnalysis analysis = analyze(release, settings);
      switch (analysis.outcome()) {
        case ELIGIBLE -> {
          eligible++;
          candidates.add(analysis.toCandidate());
        }
        case SKIPPED_NEGATIVE -> skippedNegative++;
        case SKIPPED_NO_CANDIDATES -> skippedNoCandidates++;
      }
    }

    return new PreImplantationDivergencePreviewResult(
      pendingReleases.size(), eligible, skippedNegative, skippedNoCandidates, candidates
    );
  }

  /**
   * @param releaseBankIds quando nulo/vazio, aplica a todos os lançamentos elegíveis (mesmo
   *                       comportamento de antes); quando informado, restringe aos IDs dados
   *                       (seleção manual feita na prévia) — cada um ainda é recalculado aqui,
   *                       não reusa o resultado do preview.
   */
  @Transactional
  public PreImplantationDivergenceApplyResult apply(List<UUID> releaseBankIds) {
    CreditOrderMatchSettings settings = CreditOrderMatchSettings.from(reconciliationSettingsService);
    List<ReleasesBankEntity> pendingReleases = pendingReceiptReleaseFinder.find();

    if (releaseBankIds != null && !releaseBankIds.isEmpty()) {
      Set<UUID> selected = new HashSet<>(releaseBankIds);
      pendingReleases = pendingReleases.stream().filter(r -> selected.contains(r.getId())).toList();
    }

    int linked = 0;
    int skippedNegative = 0;
    int skippedNoCandidates = 0;

    for (ReleasesBankEntity release : pendingReleases) {
      // Recalcula a cada lançamento (não reusa um lote pré-computado): uma ordem já vinculada
      // pelo reconcile() de um lançamento anterior nesta mesma execução não pode ser candidatada
      // de novo — a query de candidatas exige releaseBank is null, e o reconcile() já grava
      // (saveAll) antes de seguirmos pro próximo lançamento do laço.
      ReleaseAnalysis analysis = analyze(release, settings);
      switch (analysis.outcome()) {
        case ELIGIBLE -> {
          List<UUID> orderIds = analysis.compatibleOrders().stream().map(CreditOrderEntity::getId).toList();
          manualBankReconciliationService.reconcile(release.getId(), orderIds, STANDARD_DIVERGENCE_REASON);
          linked++;
        }
        case SKIPPED_NEGATIVE -> skippedNegative++;
        case SKIPPED_NO_CANDIDATES -> skippedNoCandidates++;
      }
    }

    log.info(
      "🔄 Vínculo automático de divergência pré-implantação: analisados={}, vinculados={}, diferença_negativa={}, sem_candidatas={}",
      pendingReleases.size(), linked, skippedNegative, skippedNoCandidates
    );

    return new PreImplantationDivergenceApplyResult(pendingReleases.size(), linked, skippedNegative, skippedNoCandidates);
  }

  private ReleaseAnalysis analyze(ReleasesBankEntity release, CreditOrderMatchSettings settings) {
    List<CreditOrderEntity> compatible = creditOrderCandidateFinder.findCompatible(release, settings);

    if (compatible.isEmpty()) {
      return ReleaseAnalysis.skippedNoCandidates();
    }

    BigDecimal sumOrders = compatible.stream()
      .map(CreditOrderEntity::getReleaseValue)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    // Positivo: lançamento inclui mais valor do que as ordens disponíveis explicam (o padrão
    // esperado de venda pré-implantação sem ordem no sistema). Negativo: ordens somam mais que o
    // lançamento — não é o cenário aqui, fica de fora.
    BigDecimal difference = release.getReleaseValue().subtract(sumOrders);

    if (difference.compareTo(settings.valueTolerance().negate()) < 0) {
      return ReleaseAnalysis.skippedNegative();
    }

    return ReleaseAnalysis.eligible(release, compatible, sumOrders, difference);
  }

  private enum Outcome { ELIGIBLE, SKIPPED_NEGATIVE, SKIPPED_NO_CANDIDATES }

  private record ReleaseAnalysis(
    Outcome outcome,
    ReleasesBankEntity release,
    List<CreditOrderEntity> compatibleOrders,
    BigDecimal sumOrders,
    BigDecimal difference
  ) {
    static ReleaseAnalysis eligible(
      ReleasesBankEntity release, List<CreditOrderEntity> compatibleOrders, BigDecimal sumOrders, BigDecimal difference
    ) {
      return new ReleaseAnalysis(Outcome.ELIGIBLE, release, compatibleOrders, sumOrders, difference);
    }

    static ReleaseAnalysis skippedNegative() {
      return new ReleaseAnalysis(Outcome.SKIPPED_NEGATIVE, null, List.of(), null, null);
    }

    static ReleaseAnalysis skippedNoCandidates() {
      return new ReleaseAnalysis(Outcome.SKIPPED_NO_CANDIDATES, null, List.of(), null, null);
    }

    PreImplantationDivergenceCandidate toCandidate() {
      return new PreImplantationDivergenceCandidate(
        release.getId(),
        release.getCompany() != null ? release.getCompany().getFantasyName() : null,
        release.getAcquirer() != null ? release.getAcquirer().getFantasyName() : null,
        release.getReleaseDate(), release.getReleaseValue(),
        compatibleOrders.size(), sumOrders, difference
      );
    }
  }
}
