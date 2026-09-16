package com.cardsync.core.reconciliation.summary;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.nimbussystems.commons.legacy.exceptionhandler.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo manual de CreditOrder órfã a SalesSummary para os lotes de liquidação (Cielo "Chave
 * UR") onde a vinculação automática ({@link CreditOrderOrphanLinkingService}) não resolve com
 * segurança: 2+ SalesSummary do mesmo lote têm o MESMO liquidValue, então o desempate por valor
 * (releaseValue↔liquidValue) nunca aponta um único candidato. Só um humano com contexto adicional
 * (ex.: conferindo o extrato original da adquirente) pode decidir o vínculo correto — por isso
 * esta classe nunca vincula sozinha, apenas lista os candidatos e aplica a escolha do operador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmbiguousCreditOrderLinkingService {

  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final TransactionAcqRepository transactionAcqRepository;

  @Transactional(readOnly = true)
  public List<AmbiguousCreditOrderBatch> listAmbiguousBatches() {
    List<Object[]> keys = salesSummaryRepository.findAmbiguousBatchKeys();
    if (keys.isEmpty()) return List.of();

    List<AmbiguousCreditOrderBatch> batches = new ArrayList<>();
    for (Object[] key : keys) {
      UUID acquirerId = (UUID) key[0];
      Integer pvNumber = (Integer) key[1];
      Integer rvNumber = (Integer) key[2];

      List<CreditOrderEntity> orphans = creditOrderRepository.findOrphanedForSummary(acquirerId, pvNumber, rvNumber);
      // findAmbiguousBatchKeys já exige >= 1 órfã pra essa chave existir no resultado (é a
      // condição do exists() na query) — lista vazia aqui só indica uma corrida rara entre a
      // consulta e um vínculo concorrente, seguro ignorar em vez de mostrar um lote sem nada
      // pra vincular.
      if (orphans.isEmpty()) continue;

      List<SalesSummaryEntity> summaries = salesSummaryRepository
        .findByAcquirer_IdAndPvNumberAndRvNumber(acquirerId, pvNumber, rvNumber);

      batches.add(toBatch(acquirerId, pvNumber, rvNumber, summaries, orphans));
    }
    return batches;
  }

  private AmbiguousCreditOrderBatch toBatch(
    UUID acquirerId, Integer pvNumber, Integer rvNumber,
    List<SalesSummaryEntity> summaries, List<CreditOrderEntity> orphans
  ) {
    List<UUID> summaryIds = summaries.stream().map(SalesSummaryEntity::getId).toList();

    Map<UUID, Integer> installmentTotalBySummaryId = new HashMap<>();
    for (Object[] row : transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(summaryIds)) {
      installmentTotalBySummaryId.put((UUID) row[0], ((Number) row[1]).intValue());
    }

    Map<UUID, Integer> linkedCountBySummaryId = new HashMap<>();
    for (Object[] row : creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(summaryIds)) {
      UUID summaryId = (UUID) row[0];
      linkedCountBySummaryId.merge(summaryId, 1, Integer::sum);
    }

    String acquirerName = summaries.isEmpty()
      ? (orphans.isEmpty() ? null : safeAcquirerName(orphans.getFirst()))
      : safeAcquirerName(summaries.getFirst());

    List<AmbiguousSalesSummaryCandidate> summaryCandidates = summaries.stream()
      .map(ss -> new AmbiguousSalesSummaryCandidate(
        ss.getId(),
        ss.getLiquidValue(),
        ss.getGrossValue(),
        ss.getRvDate(),
        linkedCountBySummaryId.getOrDefault(ss.getId(), 0),
        installmentTotalBySummaryId.get(ss.getId())
      ))
      .toList();

    List<AmbiguousCreditOrderCandidate> orderCandidates = orphans.stream()
      .map(co -> new AmbiguousCreditOrderCandidate(
        co.getId(), co.getReleaseValue(), co.getReleaseDate(), co.getInstallmentNumber(), co.getInstallmentTotal()
      ))
      .toList();

    return new AmbiguousCreditOrderBatch(acquirerId, acquirerName, pvNumber, rvNumber, summaryCandidates, orderCandidates);
  }

  private String safeAcquirerName(SalesSummaryEntity summary) {
    return summary.getAcquirer() != null ? summary.getAcquirer().getFantasyName() : null;
  }

  private String safeAcquirerName(CreditOrderEntity order) {
    return order.getAcquirer() != null ? order.getAcquirer().getFantasyName() : null;
  }

  /**
   * Aplica o vínculo escolhido pelo operador. Reexige a MESMA chave (acquirer+pvCentralizer+
   * rvNumber) dos dois lados — nunca confia apenas nos ids recebidos — pra nunca colar uma ordem
   * e um resumo de lotes diferentes por engano de UI/requisição adulterada. Mesma proteção contra
   * duplicar parcela já usada pela vinculação automática (ver CreditOrderOrphanLinkingService).
   */
  @Transactional
  public void linkManually(UUID creditOrderId, UUID salesSummaryId) {
    CreditOrderEntity order = creditOrderRepository.findById(creditOrderId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "CreditOrder não encontrada: " + creditOrderId));

    if (order.getSalesSummary() != null) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "CreditOrder já está vinculada a um resumo de vendas.");
    }

    SalesSummaryEntity summary = salesSummaryRepository.findById(salesSummaryId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "SalesSummary não encontrado: " + salesSummaryId));

    UUID orderAcquirerId = order.getAcquirer() != null ? order.getAcquirer().getId() : null;
    UUID summaryAcquirerId = summary.getAcquirer() != null ? summary.getAcquirer().getId() : null;
    if (!Objects.equals(orderAcquirerId, summaryAcquirerId)
      || !Objects.equals(order.getPvCentralizer(), summary.getPvNumber())
      || !Objects.equals(order.getRvNumber(), summary.getRvNumber())) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR,
        "CreditOrder e SalesSummary não pertencem ao mesmo lote (acquirer+pvNumber+rvNumber).");
    }

    if (order.getInstallmentNumber() != null
      && creditOrderRepository.existsBySalesSummary_IdAndInstallmentNumber(salesSummaryId, order.getInstallmentNumber())) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR,
        "O resumo já tem uma CreditOrder para a parcela " + order.getInstallmentNumber() + ".");
    }

    order.setSalesSummary(summary);
    // Mesmo cuidado de CreditOrderOrphanLinkingService: propaga RECONCILED imediatamente quando
    // o resumo já está conciliado, pra não deixar CreditOrder.salesSummaryStatus desatualizado
    // até a próxima passada da Etapa 6.
    if (summary.getCreditOrderStatus() == StatusReconciliationEnum.RECONCILED) {
      order.setSalesSummaryStatus(StatusReconciliationEnum.RECONCILED);
    }
    creditOrderRepository.save(order);

    log.info(
      "🔗 Vínculo manual (lote ambíguo): creditOrder={} -> salesSummary={}, acquirer={}, pv={}, rv={}, installmentNumber={}",
      creditOrderId, salesSummaryId, orderAcquirerId, order.getPvCentralizer(), order.getRvNumber(), order.getInstallmentNumber()
    );
  }
}
