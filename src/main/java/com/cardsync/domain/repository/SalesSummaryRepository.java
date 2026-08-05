package com.cardsync.domain.repository;

import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryStats;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionStats;
import com.cardsync.domain.model.SalesSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SalesSummaryRepository extends JpaRepository<SalesSummaryEntity, UUID>, JpaSpecificationExecutor<SalesSummaryEntity> {

  Optional<SalesSummaryEntity> findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber
  );

  /**
   * Mesma chave da consulta acima, mas retornando TODAS as candidatas — usada quando o rvNumber
   * pode legitimamente bater em mais de uma SalesSummary (achado real Cielo: "Chave UR" é uma
   * chave de lote de liquidação, não por venda — ver ProcessCielo04Service#safeSalesSummary).
   * "Pegar a primeira por rvDate" nesse caso cola o CreditOrder na venda errada do mesmo lote.
   */
  List<SalesSummaryEntity> findByAcquirer_IdAndPvNumberAndRvNumber(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber
  );

  /**
   * Importação em lote do relatório de pagamentos da adquirente (ver
   * CreditOrderManualService#importFromAcquirerReport) — busca todos os resumos candidatos de
   * uma vez pelo número do RV (chave de match escolhida para essa importação); rvNumber
   * repetido entre acquirers/pvNumbers diferentes é desambiguado em memória pelo chamador.
   */
  List<SalesSummaryEntity> findByRvNumberIn(Collection<Integer> rvNumbers);

  /**
   * Candidatas por adquirente+RV, SEM restringir por PV — usada pelo backfill de vínculo de
   * CreditOrder órfãs pré-implantação (ver CreditOrderPreImplantationLinkingService) para
   * detectar tanto o vínculo exato (mesmo PV) quanto a divergência de PV entre a ordem
   * (pvCentralizer) e o resumo (pvNumber) para o mesmo RV, mesmo padrão de diagnóstico já usado
   * em SalesSummaryCreditOrderReconciliationService#logPvMismatchDiagnosis.
   */
  @Query("""
    select ss from SalesSummaryEntity ss
    left join fetch ss.acquirer
    where ss.acquirer.id in :acquirerIds
      and ss.rvNumber in :rvNumbers
  """)
  List<SalesSummaryEntity> findByAcquirerIdInAndRvNumberIn(
    @Param("acquirerIds") Collection<UUID> acquirerIds,
    @Param("rvNumbers") Collection<Integer> rvNumbers
  );

  /**
   * Etapa 1b - Resumo de vendas x TransactionAcq.
   *
   * Consulta agregada que calcula, por SalesSummary, o total de transações vinculadas,
   * quantas estão excluídas da análise (CANCELED/DELETED) e quantas estão conciliadas
   * (AUTOMATICALLY_RECONCILED/MANUALLY_RECONCILED).
   *
   * Chamada logo após a conciliação ERP x ADQ, antes da etapa de taxas.
   * A etapa de taxas (Etapa 3) e a conciliação com resumo com gate de taxa (Etapa 4)
   * executam depois e podem refinar o status conforme necessário.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryTransactionStats(
      ss.id,
      count(tx.id),
      coalesce(sum(
        case when tx.statusTransaction in :excludedStatuses then 1L else 0L end
      ), 0L),
      coalesce(sum(
        case when tx.statusTransaction in :reconciledStatuses then 1L else 0L end
      ), 0L)
    )
      from TransactionAcqEntity tx
      join tx.salesSummary ss
     where (:includeAll = true or ss.transactionsStatus is null or ss.transactionsStatus in :pendingStatuses)
       and ss.rvDate >= :implantationDate
       and ss.rvDate >= :lookbackDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<SalesSummaryTransactionStats> findStatsForSalesSummaryTransactionReconciliation(
    @Param("includeAll") boolean includeAll,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("excludedStatuses") Collection<Integer> excludedStatuses,
    @Param("reconciledStatuses") Collection<Integer> reconciledStatuses,
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Recálculo pontual do rollup de um ou poucos SalesSummary específicos, sem o filtro de
   * lookback da Etapa 1b — usado logo após ações manuais (ex.: reconciliação manual,
   * criação de ERP a partir da adquirente) que mudam o statusTransaction de uma
   * TransactionAcqEntity fora da esteira automática. Sem isso, um resumo cujo rvDate já
   * saiu da janela de lookback nunca mais seria reavaliado pela Etapa 1b.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryTransactionStats(
      ss.id,
      count(tx.id),
      coalesce(sum(
        case when tx.statusTransaction in :excludedStatuses then 1L else 0L end
      ), 0L),
      coalesce(sum(
        case when tx.statusTransaction in :reconciledStatuses then 1L else 0L end
      ), 0L)
    )
      from TransactionAcqEntity tx
      join tx.salesSummary ss
     where ss.id in :salesSummaryIds
     group by ss.id
  """)
  List<SalesSummaryTransactionStats> findStatsForSalesSummaryTransactionReconciliationByIds(
    @Param("salesSummaryIds") Collection<UUID> salesSummaryIds,
    @Param("excludedStatuses") Collection<Integer> excludedStatuses,
    @Param("reconciledStatuses") Collection<Integer> reconciledStatuses
  );

  /**
   * Etapa 3 - Venda ADQ x SalesSummary (gate de taxa).
   *
   * NÃO filtra por ss.transactionsStatus: a Etapa 1b (SalesSummaryTransactionReconciliationService)
   * roda antes desta, no mesmo pipeline, e já promove o resumo para RECONCILED olhando só o
   * statusTransaction da transação — sem considerar feeReconciliationStatus. Se esta consulta
   * pulasse resumos com transactionsStatus fora de "pendente", ela nunca reavaliaria um resumo
   * que a Etapa 1b acabou de marcar como conciliado na mesma execução, e a divergência de taxa
   * (feeReconciliationStatus) nunca seria detectada no nível do resumo. Por isso reavalia todo
   * resumo com transação vinculada dentro da janela de lookback, a cada execução.
   *
   * Consulta otimizada para evitar N+1:
   * - não carrega SalesSummaryEntity;
   * - não carrega TransactionAcqEntity;
   * - calcula no banco o total de transações e quantas estão elegíveis pelas etapas anteriores;
   * - usa GROUP BY em vez de DISTINCT + ORDER BY, compatível com MySQL 8.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryStats(
      ss.id,
      count(tx.id),
      coalesce(sum(
        case when tx.statusTransaction in :excludedStatuses then 1L else 0L end
      ), 0L),
      coalesce(sum(
        case when tx.statusTransaction in :eligibleSaleStatuses then 1L else 0L end
      ), 0L)
    )
      from TransactionAcqEntity tx
      join tx.salesSummary ss
     where ss.id is not null
       and ss.rvDate >= :implantationDate
       and ss.rvDate >= :lookbackDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<AcquirerSaleSummaryStats> findStatsForAcquirerSaleSummaryReconciliation(
    @Param("eligibleSaleStatuses") Collection<Integer> eligibleSaleStatuses,
    @Param("excludedStatuses") Collection<Integer> excludedStatuses,
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Mesma agregação acima, mas sem o filtro de lookback — usada no backfill único
   * (ignoreLookback=true) para reavaliar resumos antigos que já saíram da janela normal.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryStats(
      ss.id,
      count(tx.id),
      coalesce(sum(
        case when tx.statusTransaction in :excludedStatuses then 1L else 0L end
      ), 0L),
      coalesce(sum(
        case when tx.statusTransaction in :eligibleSaleStatuses then 1L else 0L end
      ), 0L)
    )
      from TransactionAcqEntity tx
      join tx.salesSummary ss
     where ss.id is not null
       and ss.rvDate >= :implantationDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<AcquirerSaleSummaryStats> findStatsForAcquirerSaleSummaryReconciliationIgnoringLookback(
    @Param("eligibleSaleStatuses") Collection<Integer> eligibleSaleStatuses,
    @Param("excludedStatuses") Collection<Integer> excludedStatuses,
    @Param("implantationDate") LocalDate implantationDate
  );

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update SalesSummaryEntity ss
       set ss.transactionsStatus = :status
     where ss.id in :ids
       and (ss.transactionsStatus is null or ss.transactionsStatus <> :status)
  """)
  int updateTransactionsStatusByIds(
    @Param("ids") Collection<UUID> ids,
    @Param("status") Integer status
  );

  /**
   * Etapa 3 - Marca como conciliados os SalesSummary que não possuem nenhuma TransactionAcqEntity
   * vinculada. Esses registros são invisíveis à query principal (INNER JOIN pelo lado da transação)
   * e ficariam com transactionsStatus nulo para sempre, bloqueando as etapas seguintes.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update SalesSummaryEntity ss
       set ss.transactionsStatus = :reconciledStatus
     where (:includeAlreadyReconciled = true
            or ss.transactionsStatus is null
            or ss.transactionsStatus in :pendingStatuses)
       and not exists (
           select 1 from TransactionAcqEntity tx where tx.salesSummary.id = ss.id
       )
  """)
  int markSummariesWithoutTransactionsAsReconciled(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("reconciledStatus") Integer reconciledStatus
  );

  /**
   * Etapa 4 - SalesSummary x CreditOrder.

   * Consulta agregada para evitar N+1:
   * - não carrega entidades;
   * - conta quantas ordens existem por SalesSummary;
   * - deixa para o service gerar ordens sintéticas apenas para summaries sem ordem.
   *
   * includeAlreadyReconciled (reprocessSalesSummaryCreditOrder) só relaxa o filtro de
   * creditOrderStatus (backfill de resumos cujas ordens já foram geradas) — o filtro de
   * transactionsStatus continua SEMPRE obrigatório, nunca relaxado por essa flag. Antes o mesmo
   * booleano controlava os dois filtros ao mesmo tempo: ligar o backfill também aceitava
   * resumos com transactionsStatus PENDING/DIVERGENT/CANCELED (transações nunca conciliadas),
   * podendo gerar/vincular ordem de crédito antes de qualquer venda individual ser confirmada.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats(
      ss.id,
      count(distinct co.installmentNumber),
      max(ss.grossValue),
      cast(coalesce(max(co.installmentTotal), 1) as integer)
    )
      from SalesSummaryEntity ss
      left join CreditOrderEntity co on co.salesSummary.id = ss.id
     where (ss.transactionsStatus is null or ss.transactionsStatus in :eligibleTransactionStatuses)
       and (:includeAlreadyReconciled = true or ss.creditOrderStatus is null or ss.creditOrderStatus in :pendingCreditOrderStatuses)
       and ss.rvDate >= :implantationDate
       and ss.rvDate >= :lookbackDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<SalesSummaryCreditOrderStats> findStatsForSalesSummaryCreditOrderReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("eligibleTransactionStatuses") Collection<Integer> eligibleTransactionStatuses,
    @Param("pendingCreditOrderStatuses") Collection<Integer> pendingCreditOrderStatuses,
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Mesma agregação acima, mas sem o filtro de lookback — usada no backfill único
   * (ignoreLookback=true) para reavaliar resumos antigos que já saíram da janela normal.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats(
      ss.id,
      count(distinct co.installmentNumber),
      max(ss.grossValue),
      cast(coalesce(max(co.installmentTotal), 1) as integer)
    )
      from SalesSummaryEntity ss
      left join CreditOrderEntity co on co.salesSummary.id = ss.id
     where (ss.transactionsStatus is null or ss.transactionsStatus in :eligibleTransactionStatuses)
       and (:includeAlreadyReconciled = true or ss.creditOrderStatus is null or ss.creditOrderStatus in :pendingCreditOrderStatuses)
       and ss.rvDate >= :implantationDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<SalesSummaryCreditOrderStats> findStatsForSalesSummaryCreditOrderReconciliationIgnoringLookback(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("eligibleTransactionStatuses") Collection<Integer> eligibleTransactionStatuses,
    @Param("pendingCreditOrderStatuses") Collection<Integer> pendingCreditOrderStatuses,
    @Param("implantationDate") LocalDate implantationDate
  );

  /**
   * Complemento das duas consultas acima: SalesSummary de vendas ANTERIORES à implantação
   * ({@code rvDate < implantationDate}) — nunca avaliadas pelo fluxo normal (ambas as variantes
   * acima exigem {@code rvDate >= implantationDate}), mesmo quando alguma parcela (CreditOrder)
   * só vence/libera bem depois do go-live. Usada só pelo backfill dedicado
   * (SalesSummaryPreImplantationReconciliationService) — nunca chamada pelo fluxo automático.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats(
      ss.id,
      count(distinct co.installmentNumber),
      max(ss.grossValue),
      cast(coalesce(max(co.installmentTotal), 1) as integer)
    )
      from SalesSummaryEntity ss
      left join CreditOrderEntity co on co.salesSummary.id = ss.id
     where (ss.transactionsStatus is null or ss.transactionsStatus in :eligibleTransactionStatuses)
       and (:includeAlreadyReconciled = true or ss.creditOrderStatus is null or ss.creditOrderStatus in :pendingCreditOrderStatuses)
       and ss.rvDate < :implantationDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<SalesSummaryCreditOrderStats> findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("eligibleTransactionStatuses") Collection<Integer> eligibleTransactionStatuses,
    @Param("pendingCreditOrderStatuses") Collection<Integer> pendingCreditOrderStatuses,
    @Param("implantationDate") LocalDate implantationDate
  );

  @Query("""
    select distinct ss
      from SalesSummaryEntity ss
      left join fetch ss.acquirer
      left join fetch ss.flag
      left join fetch ss.company
      left join fetch ss.bankingDomicile
      left join fetch ss.processedFile
     where ss.id in :ids
     order by ss.rvDate asc, ss.id asc
  """)
  List<SalesSummaryEntity> findBatchForSalesSummaryCreditOrderReconciliation(@Param("ids") Collection<UUID> ids);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update SalesSummaryEntity ss
       set ss.creditOrderStatus = :status
     where ss.id in :ids
       and (ss.creditOrderStatus is null or ss.creditOrderStatus <> :status)
  """)
  int updateCreditOrderStatusByIds(
    @Param("ids") Collection<UUID> ids,
    @Param("status") Integer status
  );

  /**
   * Pré-vinculação de CreditOrder órfãs: busca SalesSummary cujos campos
   * acquirer + pvNumber + rvNumber possam corresponder a alguma CreditOrder sem vínculo.
   * A filtragem exata (a combinação correta dos três campos) é feita em memória
   * após a consulta, para evitar N queries por órfã.
   */
  @Query("""
    select distinct ss from SalesSummaryEntity ss
    left join fetch ss.acquirer
    where ss.acquirer.id in :acquirerIds
      and ss.pvNumber in :pvNumbers
      and ss.rvNumber in :rvNumbers
  """)
  List<SalesSummaryEntity> findCandidatesForCreditOrderLinking(
    @Param("acquirerIds") Collection<UUID> acquirerIds,
    @Param("pvNumbers") Collection<Integer> pvNumbers,
    @Param("rvNumbers") Collection<Integer> rvNumbers
  );

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update SalesSummaryEntity ss
       set ss.manualGenerated = :manualGenerated
     where ss.id in :ids
       and (ss.manualGenerated is null or ss.manualGenerated <> :manualGenerated)
  """)
  int updateManualGeneratedByIds(
    @Param("ids") Collection<UUID> ids,
    @Param("manualGenerated") Boolean manualGenerated
  );

  /** Retorna os pvNumbers distintos presentes em um arquivo processado (EEVC/EEVD). */
  @Query("""
    select distinct ss.pvNumber
      from SalesSummaryEntity ss
     where ss.processedFile.id = :processedFileId
       and ss.pvNumber is not null
  """)
  Set<Integer> findDistinctPvNumbersByProcessedFileId(@Param("processedFileId") UUID processedFileId);

  /** Retorna pvNumber + acquirer distintos de um arquivo processado (EEVC/EEVD) para auto-cadastro. */
  @Query("""
    select distinct ss.pvNumber, ss.acquirer
      from SalesSummaryEntity ss
     where ss.processedFile.id = :processedFileId
       and ss.pvNumber is not null
       and ss.acquirer is not null
  """)
  List<Object[]> findDistinctPvNumbersWithAcquirerByProcessedFileId(@Param("processedFileId") UUID processedFileId);

  /** Carrega os pvNumbers de múltiplos arquivos de uma vez para evitar N+1 no calendário. */
  @Query("""
    select ss.processedFile.id, ss.pvNumber
      from SalesSummaryEntity ss
     where ss.processedFile.id in :fileIds
       and ss.pvNumber is not null
  """)
  List<Object[]> findPvNumbersByProcessedFileIds(@Param("fileIds") Collection<UUID> fileIds);
}
