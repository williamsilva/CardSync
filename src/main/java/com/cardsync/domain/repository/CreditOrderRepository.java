package com.cardsync.domain.repository;

import com.cardsync.domain.model.CreditOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}