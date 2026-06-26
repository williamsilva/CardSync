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
   * Etapa 3 - Venda ADQ x SalesSummary.

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
        case
          when tx.statusTransaction in :eligibleSaleStatuses
           and (tx.feeReconciliationStatus is null or tx.feeReconciliationStatus in :eligibleFeeStatuses)
          then 1L
          else 0L
        end
      ), 0L)
    )
      from TransactionAcqEntity tx
      join tx.salesSummary ss
     where ss.id is not null
       and (:includeAlreadyReconciled = true or ss.transactionsStatus is null or ss.transactionsStatus in :pendingSummaryStatuses)
       and ss.rvDate >= :implantationDate
       and ss.rvDate >= :lookbackDate
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<AcquirerSaleSummaryStats> findStatsForAcquirerSaleSummaryReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingSummaryStatuses") Collection<Integer> pendingSummaryStatuses,
    @Param("eligibleSaleStatuses") Collection<Integer> eligibleSaleStatuses,
    @Param("eligibleFeeStatuses") Collection<Integer> eligibleFeeStatuses,
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
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
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats(
      ss.id,
      count(co.id),
      max(ss.grossValue)
    )
      from SalesSummaryEntity ss
      left join CreditOrderEntity co on co.salesSummary.id = ss.id
     where (:includeAlreadyReconciled = true or ss.transactionsStatus is null or ss.transactionsStatus in :eligibleTransactionStatuses)
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
