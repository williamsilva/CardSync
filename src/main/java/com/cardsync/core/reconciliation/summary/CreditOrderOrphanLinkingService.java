package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
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
import java.util.*;

/**
 * Vincula CreditOrder órfãs (salesSummary = NULL) ao SalesSummary correspondente
 * por acquirer + pvCentralizer + rvNumber, antes da conciliação Resumo x Ordem.
 *
 * <p>Órfãs surgem quando o arquivo EEFI é processado antes do arquivo de resumo
 * de vendas: a FK não é estabelecida na ingestão e a Etapa 6 (Resumo x Ordem)
 * não as enxerga via JOIN. Esse passo corrige a vinculação retroativamente.</p>
 *
 * <p><b>Achado real (Cielo):</b> acquirer+pvCentralizer+rvNumber NÃO identifica uma única
 * SalesSummary — a "Chave UR" da Cielo (origem do rvNumber) é uma chave de LOTE de liquidação,
 * compartilhada por várias vendas distintas do mesmo lote (confirmado com dado real: 7
 * SalesSummary diferentes, valores completamente diferentes, com o mesmo rvNumber). Vincular
 * pela chave sozinha (como antes) colava TODAS as CreditOrder órfãs do lote numa única
 * SalesSummary arbitrária (a de rvDate mais recente), deixando as demais sem ordem nenhuma e a
 * tela de Resumo de Vendas mostrando ordens de outras vendas. A correção desambigua por VALOR
 * (releaseValue↔liquidValue) dentro do grupo — só vincula quando exatamente uma candidata bate
 * dentro da tolerância; múltiplas ou nenhuma batendo fica órfã (mais seguro que adivinhar errado).</p>
 *
 * <p><b>Achado real (Etapa 6, 2026-09-13):</b> um SalesSummary sem nenhuma CreditOrder pode
 * receber uma ordem SINTÉTICA (ver SalesSummaryCreditOrderReconciliationService, installmentNumber=1)
 * antes de o arquivo EEFI real chegar atrasado. Sem checar o installmentNumber já existente no
 * resumo, esta classe vinculava a órfã real por cima da sintética, duplicando a CreditOrder para
 * a mesma parcela — ambas elegíveis para a Etapa 7, com risco de conciliação bancária em
 * duplicidade (ou uma delas nunca ser paga, mascarando o resumo como parcialmente pago). Agora só
 * vincula quando o resumo candidato ainda não tem nenhuma CreditOrder para aquele installmentNumber.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditOrderOrphanLinkingService {

  private static final int BATCH_SIZE = 1_000;
  private static final BigDecimal VALUE_TOLERANCE = new BigDecimal("0.01");

  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;

  @Transactional
  public int linkOrphanedCreditOrders() {
    return linkOrphanedCreditOrders(false);
  }

  /**
   * @param ignoreLookback quando {@code true}, ignora o filtro de lookback — usado para um
   *                        backfill único, vinculando órfãs antigas que já saíram da janela
   *                        normal.
   */
  @Transactional
  public int linkOrphanedCreditOrders(boolean ignoreLookback) {
    LocalDate implantationDate = implantationDateProvider.get();

    List<UUID> orphanedIds;
    if (ignoreLookback) {
      orphanedIds = creditOrderRepository.findOrphanedIdsIgnoringLookback(implantationDate);
    } else {
      LocalDate lookbackDate = LocalDate.now().minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths());
      orphanedIds = creditOrderRepository.findOrphanedIdsWithinDateRange(implantationDate, lookbackDate);
    }

    if (orphanedIds.isEmpty()) {
      log.info("✅ Pré-vinculação: nenhuma CreditOrder órfã no período. implantationDate={}, ignoreLookback={}", implantationDate, ignoreLookback);
      return 0;
    }

    log.info("🔗 Pré-vinculação: {} CreditOrder(s) órfã(s) no período. implantationDate={}, ignoreLookback={}", orphanedIds.size(), implantationDate, ignoreLookback);

    int totalLinked = 0;
    int batchNumber = 0;
    int totalBatches = (int) Math.ceil((double) orphanedIds.size() / BATCH_SIZE);

    for (int start = 0; start < orphanedIds.size(); start += BATCH_SIZE) {
      batchNumber++;
      List<UUID> batchIds = orphanedIds.subList(start, Math.min(start + BATCH_SIZE, orphanedIds.size()));

      List<CreditOrderEntity> orphans = creditOrderRepository.findOrphanedByIds(batchIds);

      Set<UUID> acquirerIds = new HashSet<>();
      Set<Integer> pvNumbers = new HashSet<>();
      Set<Integer> rvNumbers = new HashSet<>();

      for (CreditOrderEntity co : orphans) {
        if (co.getAcquirer() != null && co.getPvCentralizer() != null && co.getRvNumber() != null) {
          acquirerIds.add(co.getAcquirer().getId());
          pvNumbers.add(co.getPvCentralizer());
          rvNumbers.add(co.getRvNumber());
        }
      }

      if (acquirerIds.isEmpty()) {
        log.info("⚠️ Pré-vinculação batch {}/{}: nenhuma CreditOrder com acquirer+pv+rv válidos.", batchNumber, totalBatches);
        continue;
      }

      List<SalesSummaryEntity> candidates = salesSummaryRepository.findCandidatesForCreditOrderLinking(
        acquirerIds, pvNumbers, rvNumbers
      );

      // Agrupa TODAS as candidatas por (acquirerId:pvNumber:rvNumber) — a chave pode ter mais de
      // uma SalesSummary (lote de liquidação da Cielo, ver javadoc da classe); desambiguar por
      // valor é feito por-órfã, abaixo, não aqui.
      Map<String, List<SalesSummaryEntity>> summaryMap = new HashMap<>();
      for (SalesSummaryEntity ss : candidates) {
        if (ss.getAcquirer() == null || ss.getPvNumber() == null || ss.getRvNumber() == null) continue;
        String key = ss.getAcquirer().getId() + ":" + ss.getPvNumber() + ":" + ss.getRvNumber();
        summaryMap.computeIfAbsent(key, k -> new ArrayList<>()).add(ss);
      }

      Map<UUID, Set<Integer>> existingInstallmentsBySummaryId = existingInstallmentNumbersFor(candidates);

      List<CreditOrderEntity> toSave = new ArrayList<>();
      int batchLinked = 0;
      int batchAmbiguous = 0;
      int batchSkippedDuplicateInstallment = 0;

      for (CreditOrderEntity co : orphans) {
        if (co.getAcquirer() == null || co.getPvCentralizer() == null || co.getRvNumber() == null) continue;
        String key = co.getAcquirer().getId() + ":" + co.getPvCentralizer() + ":" + co.getRvNumber();
        List<SalesSummaryEntity> keyCandidates = summaryMap.get(key);
        if (keyCandidates == null || keyCandidates.isEmpty()) continue;

        SalesSummaryEntity match = keyCandidates.size() == 1 ? keyCandidates.get(0) : selectByValue(keyCandidates, co);
        if (match == null) {
          batchAmbiguous++;
          continue;
        }

        // Resumo pode já ter uma CreditOrder (real ou sintética) para essa mesma parcela — ex.:
        // a Etapa 6 gera uma ordem sintética quando o resumo não tem nenhuma, e o arquivo EEFI
        // real chega atrasado depois. Vincular por cima duplicaria a CreditOrder da parcela.
        if (co.getInstallmentNumber() != null
          && existingInstallmentsBySummaryId.getOrDefault(match.getId(), Set.of()).contains(co.getInstallmentNumber())) {
          batchSkippedDuplicateInstallment++;
          continue;
        }

        co.setSalesSummary(match);
        // Se o resumo já está conciliado, propaga o status imediatamente para evitar
        // inconsistência entre SalesSummary.creditOrderStatus e CreditOrder.salesSummaryStatus
        if (match.getCreditOrderStatus() == StatusReconciliationEnum.RECONCILED) {
          co.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
        }
        toSave.add(co);
        batchLinked++;
      }

      if (batchAmbiguous > 0) {
        log.warn(
          "⚠️ Pré-vinculação batch {}/{}: {} CreditOrder(s) com acquirer+pv+rv correspondendo a mais de uma SalesSummary do mesmo lote, sem bater por valor com nenhuma (ou batendo com mais de uma) — deixadas órfãs.",
          batchNumber, totalBatches, batchAmbiguous
        );
      }

      if (batchSkippedDuplicateInstallment > 0) {
        log.warn(
          "⚠️ Pré-vinculação batch {}/{}: {} CreditOrder(s) órfã(s) ignorada(s) — o SalesSummary candidato já tem uma CreditOrder para o mesmo installmentNumber (provável ordem sintética gerada antes do arquivo real chegar). Deixadas órfãs para revisão manual.",
          batchNumber, totalBatches, batchSkippedDuplicateInstallment
        );
      }

      if (!toSave.isEmpty()) {
        creditOrderRepository.saveAll(toSave);
      }

      totalLinked += batchLinked;

      log.info(
        "🔄 Pré-vinculação batch {}/{}: orfas={}, candidatas={}, vinculadas={}, duplicadasIgnoradas={}, totalVinculadas={}",
        batchNumber, totalBatches, orphans.size(), candidates.size(), batchLinked, batchSkippedDuplicateInstallment, totalLinked
      );
    }

    log.info(
      "✅ Pré-vinculação concluída. orfasTotais={}, vinculadas={}, semCorrespondência={}",
      orphanedIds.size(), totalLinked, orphanedIds.size() - totalLinked
    );

    return totalLinked;
  }

  /**
   * Vincula imediatamente as CreditOrders órfãs que correspondem a um resumo de vendas
   * recém-criado manualmente (acquirer + pvCentralizer + rvNumber).
   *
   * <p>Chamado logo após a persistência do resumo manual para que a conciliação
   * Resumo x Ordem já encontre as ordens vinculadas sem precisar aguardar o pipeline.</p>
   */
  @Transactional
  public int linkOrphanedCreditOrdersForSummary(SalesSummaryEntity summary) {
    if (summary.getAcquirer() == null || summary.getPvNumber() == null || summary.getRvNumber() == null) {
      log.warn("⚠️ Vinculação direta ignorada: summary id={} sem acquirer/pvNumber/rvNumber.", summary.getId());
      return 0;
    }

    List<CreditOrderEntity> orphans = creditOrderRepository.findOrphanedForSummary(
      summary.getAcquirer().getId(),
      summary.getPvNumber(),
      summary.getRvNumber()
    );

    if (orphans.isEmpty()) {
      log.info("✅ Vinculação direta: nenhuma CreditOrder órfã para pv={}, rv={}", summary.getPvNumber(), summary.getRvNumber());
      return 0;
    }

    // Mesmo cuidado do batch acima: pv+rv pode achar órfãs de OUTRAS vendas do mesmo lote —
    // só vincula as que batem por valor com este summary específico (ver javadoc da classe).
    List<CreditOrderEntity> matching = orphans.size() == 1
      ? orphans
      : orphans.stream().filter(co -> valuesMatch(co.getReleaseValue(), summary.getLiquidValue())).toList();

    if (matching.isEmpty()) {
      log.info("✅ Vinculação direta: {} CreditOrder(s) órfã(s) por pv+rv, nenhuma bate por valor com summary id={}, pv={}, rv={}, liquidValue={} — deixadas órfãs.",
        orphans.size(), summary.getId(), summary.getPvNumber(), summary.getRvNumber(), summary.getLiquidValue());
      return 0;
    }

    // Mesma proteção do batch acima: não duplica CreditOrder para uma parcela que o resumo já tem
    // (ex.: ordem sintética gerada pela Etapa 6 antes desta órfã aparecer).
    Set<Integer> existingInstallments = creditOrderRepository.findInstallmentNumbersBySalesSummaryId(summary.getId());
    List<CreditOrderEntity> toLink = new ArrayList<>();
    int skippedDuplicateInstallment = 0;
    for (CreditOrderEntity co : matching) {
      if (co.getInstallmentNumber() != null && existingInstallments.contains(co.getInstallmentNumber())) {
        skippedDuplicateInstallment++;
        continue;
      }
      co.setSalesSummary(summary);
      if (summary.getCreditOrderStatus() == StatusReconciliationEnum.RECONCILED) {
        co.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
      }
      toLink.add(co);
    }

    if (skippedDuplicateInstallment > 0) {
      log.warn("⚠️ Vinculação direta: {} CreditOrder(s) ignorada(s) — summary id={} já tem CreditOrder para o mesmo installmentNumber.",
        skippedDuplicateInstallment, summary.getId());
    }

    if (toLink.isEmpty()) {
      return 0;
    }

    creditOrderRepository.saveAll(toLink);

    log.info("🔗 Vinculação direta: {} CreditOrder(s) vinculada(s) ao summary id={}, pv={}, rv={}",
      toLink.size(), summary.getId(), summary.getPvNumber(), summary.getRvNumber());

    return toLink.size();
  }

  private Map<UUID, Set<Integer>> existingInstallmentNumbersFor(List<SalesSummaryEntity> candidates) {
    List<UUID> candidateIds = candidates.stream().map(SalesSummaryEntity::getId).toList();
    if (candidateIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, Set<Integer>> result = new HashMap<>();
    for (Object[] row : creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(candidateIds)) {
      UUID summaryId = (UUID) row[0];
      Integer installmentNumber = (Integer) row[1];
      if (summaryId != null && installmentNumber != null) {
        result.computeIfAbsent(summaryId, ignored -> new HashSet<>()).add(installmentNumber);
      }
    }
    return result;
  }

  /**
   * Entre as candidatas que batem por acquirer+pv+rv (mesmo lote de liquidação), escolhe a que
   * bate por valor (releaseValue↔liquidValue) com a ordem órfã. Só retorna um match quando
   * exatamente UMA candidata bate dentro da tolerância — múltiplas batendo (ambíguo) ou nenhuma
   * batendo (ex.: parcela isolada de uma venda parcelada, cujo valor não é o total do resumo)
   * retornam null, deixando a ordem órfã em vez de vincular errado.
   */
  private SalesSummaryEntity selectByValue(List<SalesSummaryEntity> candidates, CreditOrderEntity order) {
    if (order.getReleaseValue() == null) return null;

    List<SalesSummaryEntity> matches = candidates.stream()
      .filter(ss -> valuesMatch(ss.getLiquidValue(), order.getReleaseValue()))
      .toList();

    return matches.size() == 1 ? matches.get(0) : null;
  }

  private boolean valuesMatch(BigDecimal a, BigDecimal b) {
    return a != null && b != null && a.subtract(b).abs().compareTo(VALUE_TOLERANCE) <= 0;
  }
}
