package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.CardsyncAppProperties;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Vincula CreditOrder órfãs (salesSummary = NULL) ao SalesSummary correspondente
 * por acquirer + pvCentralizer + rvNumber, antes da conciliação Resumo x Ordem.
 *
 * <p>Órfãs surgem quando o arquivo EEFI é processado antes do arquivo de resumo
 * de vendas: a FK não é estabelecida na ingestão e a Etapa 6 (Resumo x Ordem)
 * não as enxerga via JOIN. Esse passo corrige a vinculação retroativamente.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditOrderOrphanLinkingService {

  private static final int BATCH_SIZE = 1_000;

  private final CardsyncAppProperties appProperties;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;

  @Transactional
  public int linkOrphanedCreditOrders() {
    LocalDate implantationDate = appProperties.getImplantationDate();
    LocalDate lookbackDate = LocalDate.now().minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths());

    List<UUID> orphanedIds = creditOrderRepository.findOrphanedIdsWithinDateRange(implantationDate, lookbackDate);

    if (orphanedIds.isEmpty()) {
      log.info("✅ Pré-vinculação: nenhuma CreditOrder órfã no período. implantationDate={}, lookbackDate={}", implantationDate, lookbackDate);
      return 0;
    }

    log.info("🔗 Pré-vinculação: {} CreditOrder(s) órfã(s) no período. implantationDate={}, lookbackDate={}", orphanedIds.size(), implantationDate, lookbackDate);

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

      // Para cada combinação (acquirerId:pvNumber:rvNumber) mantém o SalesSummary mais recente,
      // replicando o comportamento do processamento de arquivo (ORDER BY rvDate DESC).
      Map<String, SalesSummaryEntity> summaryMap = new HashMap<>();
      for (SalesSummaryEntity ss : candidates) {
        if (ss.getAcquirer() == null || ss.getPvNumber() == null || ss.getRvNumber() == null) continue;
        String key = ss.getAcquirer().getId() + ":" + ss.getPvNumber() + ":" + ss.getRvNumber();
        summaryMap.merge(key, ss, (existing, candidate) ->
          candidate.getRvDate() != null
            && (existing.getRvDate() == null || candidate.getRvDate().isAfter(existing.getRvDate()))
            ? candidate : existing
        );
      }

      List<CreditOrderEntity> toSave = new ArrayList<>();
      int batchLinked = 0;

      for (CreditOrderEntity co : orphans) {
        if (co.getAcquirer() == null || co.getPvCentralizer() == null || co.getRvNumber() == null) continue;
        String key = co.getAcquirer().getId() + ":" + co.getPvCentralizer() + ":" + co.getRvNumber();
        SalesSummaryEntity match = summaryMap.get(key);
        if (match != null) {
          co.setSalesSummary(match);
          toSave.add(co);
          batchLinked++;
        }
      }

      if (!toSave.isEmpty()) {
        creditOrderRepository.saveAll(toSave);
      }

      totalLinked += batchLinked;

      log.info(
        "🔄 Pré-vinculação batch {}/{}: orfas={}, candidatas={}, vinculadas={}, totalVinculadas={}",
        batchNumber, totalBatches, orphans.size(), candidates.size(), batchLinked, totalLinked
      );
    }

    log.info(
      "✅ Pré-vinculação concluída. orfasTotais={}, vinculadas={}, semCorrespondência={}",
      orphanedIds.size(), totalLinked, orphanedIds.size() - totalLinked
    );

    return totalLinked;
  }
}
