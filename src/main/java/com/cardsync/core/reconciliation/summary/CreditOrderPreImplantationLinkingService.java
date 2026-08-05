package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backfill/ferramenta de análise para CreditOrder órfãs (sem SalesSummary) cujo rvDate é
 * ANTERIOR à data de implantação — excluídas por desenho do backfill padrão
 * ({@link CreditOrderOrphanLinkingService}, que só processa {@code rvDate >= implantação}).
 * Encontrada ao investigar por que milhares de ordens de crédito nunca chegam a ser elegíveis
 * pra conciliação bancária: nunca tiveram SalesSummary vinculado.
 *
 * Categoriza cada órfã contra os SalesSummary do mesmo adquirente+RV (sem restringir por PV,
 * ao contrário do backfill padrão) em três grupos:
 * - EXACT_MATCH: mesmo adquirente+PV+RV — vínculo seguro, {@link #apply} vincula automaticamente,
 *   mesma lógica de {@link CreditOrderOrphanLinkingService#linkOrphanedCreditOrders}.
 * - PV_MISMATCH: mesmo adquirente+RV mas PV diferente entre a ordem (pvCentralizer) e o resumo
 *   (pvNumber) — mesmo padrão de diagnóstico já usado em
 *   {@code SalesSummaryCreditOrderReconciliationService#logPvMismatchDiagnosis} (hoje só logado,
 *   nunca agido). É uma decisão de negócio (pode indicar estabelecimento errado em um dos dois
 *   lados) — NUNCA vinculado automaticamente aqui, só reportado pra revisão manual.
 * - NO_MATCH: nenhum resumo com esse adquirente+RV existe no sistema — nada a fazer.
 *
 * {@link #preview()} nunca grava nada; só {@link #apply} executa de fato, e só para EXACT_MATCH.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditOrderPreImplantationLinkingService {

  private final ImplantationDateProvider implantationDateProvider;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;

  @Transactional(readOnly = true)
  public CreditOrderPreImplantationLinkingPreviewResult preview() {
    List<CreditOrderEntity> orphans = loadOrphans();
    Map<String, List<SalesSummaryEntity>> summariesByKey = candidateSummariesByKey(orphans);

    int exactMatch = 0, pvMismatch = 0, noMatch = 0;
    List<CreditOrderPreImplantationLinkingCandidate> candidates = new ArrayList<>();
    List<CreditOrderPreImplantationPvMismatch> mismatches = new ArrayList<>();

    for (CreditOrderEntity co : orphans) {
      OrphanAnalysis analysis = analyze(co, summariesByKey);
      switch (analysis.outcome()) {
        case EXACT_MATCH -> { exactMatch++; candidates.add(analysis.toCandidate()); }
        case PV_MISMATCH -> { pvMismatch++; mismatches.add(analysis.toMismatch()); }
        case NO_MATCH -> noMatch++;
      }
    }

    return new CreditOrderPreImplantationLinkingPreviewResult(
      orphans.size(), exactMatch, pvMismatch, noMatch, candidates, mismatches
    );
  }

  /**
   * @param creditOrderIds quando nulo/vazio, aplica a todas as órfãs com vínculo exato
   *                       disponível; quando informado, restringe aos IDs dados (seleção manual
   *                       feita na prévia) — cada um ainda é recalculado aqui, não reusa o
   *                       resultado do preview. PV_MISMATCH nunca é vinculado, mesmo se
   *                       explicitamente selecionado.
   */
  @Transactional
  public CreditOrderPreImplantationLinkingApplyResult apply(List<UUID> creditOrderIds) {
    List<CreditOrderEntity> orphans = loadOrphans();

    if (creditOrderIds != null && !creditOrderIds.isEmpty()) {
      Set<UUID> selected = new HashSet<>(creditOrderIds);
      orphans = orphans.stream().filter(co -> selected.contains(co.getId())).toList();
    }

    Map<String, List<SalesSummaryEntity>> summariesByKey = candidateSummariesByKey(orphans);

    List<CreditOrderEntity> toLink = new ArrayList<>();
    int pvMismatch = 0, noMatch = 0;

    for (CreditOrderEntity co : orphans) {
      OrphanAnalysis analysis = analyze(co, summariesByKey);
      switch (analysis.outcome()) {
        case EXACT_MATCH -> {
          co.setSalesSummary(analysis.match());
          // Se o resumo já está conciliado, propaga o status imediatamente para evitar
          // inconsistência entre SalesSummary.creditOrderStatus e CreditOrder.salesSummaryStatus
          // (mesmo comportamento de CreditOrderOrphanLinkingService).
          if (analysis.match().getCreditOrderStatus() == StatusReconciliationEnum.RECONCILED) {
            co.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
          }
          toLink.add(co);
        }
        case PV_MISMATCH -> pvMismatch++;
        case NO_MATCH -> noMatch++;
      }
    }

    if (!toLink.isEmpty()) {
      creditOrderRepository.saveAll(toLink);
    }

    log.info(
      "🔗 Vínculo automático de órfãs pré-implantação: analisadas={}, vinculadas={}, pv_mismatch={}, sem_correspondência={}",
      orphans.size(), toLink.size(), pvMismatch, noMatch
    );

    return new CreditOrderPreImplantationLinkingApplyResult(orphans.size(), toLink.size(), pvMismatch, noMatch);
  }

  private List<CreditOrderEntity> loadOrphans() {
    LocalDate implantationDate = implantationDateProvider.get();
    List<UUID> ids = creditOrderRepository.findOrphanedIdsBeforeImplantation(implantationDate);
    return ids.isEmpty() ? List.of() : creditOrderRepository.findOrphanedByIdsWithCompany(ids);
  }

  private Map<String, List<SalesSummaryEntity>> candidateSummariesByKey(List<CreditOrderEntity> orphans) {
    Set<UUID> acquirerIds = new HashSet<>();
    Set<Integer> rvNumbers = new HashSet<>();
    for (CreditOrderEntity co : orphans) {
      if (co.getAcquirer() != null && co.getRvNumber() != null) {
        acquirerIds.add(co.getAcquirer().getId());
        rvNumbers.add(co.getRvNumber());
      }
    }
    if (acquirerIds.isEmpty()) return Map.of();

    Map<String, List<SalesSummaryEntity>> byKey = new HashMap<>();
    for (SalesSummaryEntity ss : salesSummaryRepository.findByAcquirerIdInAndRvNumberIn(acquirerIds, rvNumbers)) {
      if (ss.getAcquirer() == null || ss.getRvNumber() == null) continue;
      String key = ss.getAcquirer().getId() + ":" + ss.getRvNumber();
      byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(ss);
    }
    return byKey;
  }

  private OrphanAnalysis analyze(CreditOrderEntity co, Map<String, List<SalesSummaryEntity>> summariesByKey) {
    if (co.getAcquirer() == null || co.getRvNumber() == null || co.getPvCentralizer() == null) {
      return OrphanAnalysis.noMatch(co);
    }

    String key = co.getAcquirer().getId() + ":" + co.getRvNumber();
    List<SalesSummaryEntity> candidates = summariesByKey.getOrDefault(key, List.of());
    if (candidates.isEmpty()) {
      return OrphanAnalysis.noMatch(co);
    }

    List<SalesSummaryEntity> samePv = candidates.stream()
      .filter(ss -> co.getPvCentralizer().equals(ss.getPvNumber()))
      .toList();

    // Achado real (Cielo): mesmo adquirente+PV+rvNumber pode achar VÁRIOS SalesSummary — a
    // "Chave UR" (origem do rvNumber) é uma chave de lote de liquidação, não por venda (ver
    // javadoc de CreditOrderOrphanLinkingService). Escolher só pelo rvDate mais recente colava
    // a ordem na venda errada do mesmo lote; desambigua por valor (releaseValue↔liquidValue) —
    // só vira EXACT_MATCH quando exatamente uma bate.
    if (samePv.size() > 1) {
      List<SalesSummaryEntity> byValue = samePv.stream()
        .filter(ss -> valuesMatch(ss.getLiquidValue(), co.getReleaseValue()))
        .toList();
      return byValue.size() == 1 ? OrphanAnalysis.exactMatch(co, byValue.getFirst()) : OrphanAnalysis.noMatch(co);
    }

    Optional<SalesSummaryEntity> exact = samePv.stream().findFirst();

    if (exact.isPresent()) {
      return OrphanAnalysis.exactMatch(co, exact.get());
    }

    SalesSummaryEntity mostRecentMismatch = candidates.stream()
      .max(Comparator.comparing(SalesSummaryEntity::getRvDate, Comparator.nullsFirst(Comparator.naturalOrder())))
      .orElseThrow();
    return OrphanAnalysis.pvMismatch(co, mostRecentMismatch);
  }

  private static final BigDecimal VALUE_TOLERANCE = new BigDecimal("0.01");

  private boolean valuesMatch(BigDecimal a, BigDecimal b) {
    return a != null && b != null && a.subtract(b).abs().compareTo(VALUE_TOLERANCE) <= 0;
  }

  private enum Outcome { EXACT_MATCH, PV_MISMATCH, NO_MATCH }

  private record OrphanAnalysis(Outcome outcome, CreditOrderEntity order, SalesSummaryEntity match) {
    static OrphanAnalysis exactMatch(CreditOrderEntity order, SalesSummaryEntity match) {
      return new OrphanAnalysis(Outcome.EXACT_MATCH, order, match);
    }

    static OrphanAnalysis pvMismatch(CreditOrderEntity order, SalesSummaryEntity match) {
      return new OrphanAnalysis(Outcome.PV_MISMATCH, order, match);
    }

    static OrphanAnalysis noMatch(CreditOrderEntity order) {
      return new OrphanAnalysis(Outcome.NO_MATCH, order, null);
    }

    CreditOrderPreImplantationLinkingCandidate toCandidate() {
      return new CreditOrderPreImplantationLinkingCandidate(
        order.getId(),
        order.getCompany() != null ? order.getCompany().getFantasyName() : null,
        order.getAcquirer() != null ? order.getAcquirer().getFantasyName() : null,
        order.getRvNumber(), order.getPvCentralizer(),
        order.getRvDate(), order.getReleaseDate(), order.getReleaseValue(),
        match.getId()
      );
    }

    CreditOrderPreImplantationPvMismatch toMismatch() {
      return new CreditOrderPreImplantationPvMismatch(
        order.getId(),
        order.getCompany() != null ? order.getCompany().getFantasyName() : null,
        order.getAcquirer() != null ? order.getAcquirer().getFantasyName() : null,
        order.getRvNumber(),
        order.getPvCentralizer(), match.getPvNumber(),
        order.getReleaseDate(), order.getReleaseValue(),
        match.getId()
      );
    }
  }
}
