package com.cardsync.domain.repository;

import com.cardsync.core.reconciliation.summary.AcquirerSaleSummaryStats;
import com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats;
import com.cardsync.domain.model.SalesSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalesSummaryRepository extends JpaRepository<SalesSummaryEntity, UUID>, JpaSpecificationExecutor<SalesSummaryEntity> {

  Optional<SalesSummaryEntity> findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber
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
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<AcquirerSaleSummaryStats> findStatsForAcquirerSaleSummaryReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingSummaryStatuses") Collection<Integer> pendingSummaryStatuses,
    @Param("eligibleSaleStatuses") Collection<Integer> eligibleSaleStatuses,
    @Param("eligibleFeeStatuses") Collection<Integer> eligibleFeeStatuses
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
   * Etapa 4 - SalesSummary x CreditOrder.

   * Consulta agregada para evitar N+1:
   * - não carrega entidades;
   * - conta quantas ordens existem por SalesSummary;
   * - deixa para o service gerar ordens sintéticas apenas para summaries sem ordem.
   */
  @Query("""
    select new com.cardsync.core.reconciliation.summary.SalesSummaryCreditOrderStats(
      ss.id,
      count(co.id)
    )
      from SalesSummaryEntity ss
      left join CreditOrderEntity co on co.salesSummary.id = ss.id
     where (:includeAlreadyReconciled = true or ss.transactionsStatus in :eligibleTransactionStatuses)
       and (:includeAlreadyReconciled = true or ss.creditOrderStatus is null or ss.creditOrderStatus in :pendingCreditOrderStatuses)
     group by ss.id
     order by min(ss.rvDate) asc, ss.id asc
  """)
  List<SalesSummaryCreditOrderStats> findStatsForSalesSummaryCreditOrderReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("eligibleTransactionStatuses") Collection<Integer> eligibleTransactionStatuses,
    @Param("pendingCreditOrderStatuses") Collection<Integer> pendingCreditOrderStatuses
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
}
