package com.cardsync.domain.repository;

import com.cardsync.domain.model.AdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdjustmentRepository extends JpaRepository<AdjustmentEntity, UUID>, JpaSpecificationExecutor<AdjustmentEntity> {

  @Query("""
    select a from AdjustmentEntity a
    where a.adjustmentDate >= :lookback
       or (a.adjustmentDate is null and a.transactionDate >= :lookback)
    order by a.adjustmentDate asc
  """)
  List<AdjustmentEntity> findAllForDashboard(@Param("lookback") LocalDate lookback);

  @Query("""
    select adj.id
      from AdjustmentEntity adj
      join adj.transaction tx
     where adj.cancellationValueRequested is not null
       and adj.cancellationValueRequested > 0
       and (:reprocess = true
            or tx.statusTransaction is null
            or tx.statusTransaction <> :canceledStatus
            or exists (
                 select 1
                   from TransactionErpEntity erp
                  where erp.transactionAcq = tx
                    and (erp.statusTransaction is null or erp.statusTransaction <> :canceledStatus)
            ))
     order by adj.id
  """)
  List<UUID> findIdsForAcquirerSaleCancellationReconciliation(
    @Param("reprocess") boolean reprocess,
    @Param("canceledStatus") Integer canceledStatus
  );

  @Query("""
    select distinct adj
      from AdjustmentEntity adj
      join fetch adj.transaction tx
      left join fetch tx.installments
      left join fetch tx.adjustment
      left join fetch tx.salesSummary ss
      left join fetch ss.bankingDomicile
      left join fetch adj.acquirer
      left join fetch adj.company
      left join fetch adj.establishment
     where adj.id in :ids
     order by adj.id
  """)
  List<AdjustmentEntity> findBatchForAcquirerSaleCancellationReconciliation(@Param("ids") Collection<UUID> ids);

  /**
   * Soma de tarifas (ex.: aluguel/inatividade de POS — Rede EEVD identificador "011", ver
   * ProcessRedeEeVdService.buildAdjustment011) descontadas do lançamento bancário do dia mas que
   * nunca reduzem CreditOrderEntity.releaseValue em si. Agrupado por RV + data do crédito para
   * permitir descontar o valor certo por dia de repasse em BankReconciliationService — uma RV
   * parcelada pode ter tarifas lançadas em dias diferentes.
   */
  @Query("""
    select adj.salesSummary.id, adj.creditDate, sum(adj.adjustmentValue)
      from AdjustmentEntity adj
     where adj.salesSummary.id in :summaryIds
       and adj.adjustmentType = 'POS_FEE'
       and adj.debitType = 'D'
     group by adj.salesSummary.id, adj.creditDate
  """)
  List<Object[]> sumPosFeeBySalesSummaryAndCreditDate(@Param("summaryIds") Collection<UUID> summaryIds);
}
