package com.cardsync.domain.repository;

import com.cardsync.domain.model.CreditOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditOrderRepository extends JpaRepository<CreditOrderEntity, UUID>, JpaSpecificationExecutor<CreditOrderEntity> {

  @Query("""
    select co.id
    from CreditOrderEntity co
    where co.releaseBank is null
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus)
      and co.releaseDate is not null
      and co.releaseValue is not null
      and co.company is not null
      and co.bankingDomicile is not null
    order by co.releaseDate asc, co.id asc
  """)
  List<UUID> findEligibleIdsForBankReconciliation(
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus
  );

  @Query("""
    select distinct co
    from CreditOrderEntity co
    left join fetch co.salesSummary
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.company
    left join fetch co.bankingDomicile
    where co.id in :ids
      and co.releaseBank is null
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus)
      and co.releaseDate is not null
      and co.releaseValue is not null
    order by co.releaseDate asc, co.id asc
  """)
  List<CreditOrderEntity> findEligibleByIdsForBankReconciliation(
    @Param("ids") List<UUID> ids,
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.salesSummaryStatus = :status
     where co.salesSummary.id in :salesSummaryIds
       and (co.salesSummaryStatus is null or co.salesSummaryStatus <> :status)
  """)
  int updateSalesSummaryStatusBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.statusPaymentBank = :status
     where co.salesSummary.id in :salesSummaryIds
       and co.statusPaymentBank is null
  """)
  int updateNullStatusPaymentBankBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.reconciliationStatus = :status
     where co.salesSummary.id in :salesSummaryIds
       and co.reconciliationStatus is null
  """)
  int updateNullReconciliationStatusBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  /**
   * Diagnóstico: conta CreditOrders sem SalesSummary vinculado.
   * Um valor > 0 indica que os arquivos de ordem de crédito foram processados mas a
   * vinculação com o resumo de vendas não foi estabelecida (possível divergência de RV/PV).
   */
  @Query("select count(co.id) from CreditOrderEntity co where co.salesSummary is null")
  long countWithoutSalesSummary();

  /**
   * Pré-vinculação: retorna IDs de CreditOrder órfãs (salesSummary = NULL) com
   * acquirer, pvCentralizer e rvNumber preenchidos, dentro do período configurado.
   */
  @Query("""
    select co.id from CreditOrderEntity co
    where co.salesSummary is null
      and co.acquirer is not null
      and co.pvCentralizer is not null
      and co.rvNumber is not null
      and co.rvDate >= :implantationDate
      and co.rvDate >= :lookbackDate
    order by co.rvDate asc, co.id asc
  """)
  List<UUID> findOrphanedIdsWithinDateRange(
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Pré-vinculação: carrega CreditOrder órfãs por IDs com acquirer em fetch join.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.acquirer
    where co.id in :ids
      and co.salesSummary is null
  """)
  List<CreditOrderEntity> findOrphanedByIds(@Param("ids") Collection<UUID> ids);

  /**
   * Diagnóstico de mismatch PV: busca CreditOrders órfãs (sem salesSummary) que
   * compartilham acquirer+rvNumber com algum SalesSummary pendente, independentemente
   * de pvCentralizer. Usado para detectar se a raiz do problema é divergência de PV.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.acquirer
    where co.salesSummary is null
      and co.acquirer.id in :acquirerIds
      and co.rvNumber in :rvNumbers
  """)
  List<CreditOrderEntity> findOrphanedByAcquirerIdsAndRvNumbers(
    @Param("acquirerIds") Collection<UUID> acquirerIds,
    @Param("rvNumbers") Collection<Integer> rvNumbers
  );
}