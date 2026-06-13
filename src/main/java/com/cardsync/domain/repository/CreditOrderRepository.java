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
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditOrderRepository extends JpaRepository<CreditOrderEntity, UUID>, JpaSpecificationExecutor<CreditOrderEntity> {

  @Query("""
    select co
    from CreditOrderEntity co
    left join fetch co.salesSummary ss
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.company
    left join fetch co.bankingDomicile
    where co.releaseBank is null
      and (co.reconciliationStatus is null or co.reconciliationStatus = :pendingStatus)
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.company.id = :companyId
      and co.bankingDomicile.id = :bankingDomicileId
      and (:acquirerId is null or co.acquirer.id = :acquirerId)
      and (:flagId is null or co.flag.id = :flagId)
      and (
        :modalityPaymentBank is null
        or (:modalityPaymentBank = 1 and co.transactionType = 1)
        or (:modalityPaymentBank = 2 and co.transactionType in (2, 3, 4, 5))
      )
      and co.releaseDate between :dateFrom and :dateTo
    order by co.releaseDate asc, co.releaseValue asc
  """)
  List<CreditOrderEntity> findPendingForBankRelease(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId,
    @Param("bankingDomicileId") UUID bankingDomicileId,
    @Param("flagId") UUID flagId,
    @Param("modalityPaymentBank") Integer modalityPaymentBank,
    @Param("dateFrom") LocalDate dateFrom,
    @Param("dateTo") LocalDate dateTo
  );


  @Query("""
    select co
    from CreditOrderEntity co
    left join fetch co.salesSummary ss
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.company
    left join fetch co.bankingDomicile
    where co.releaseBank is null
      and (co.reconciliationStatus is null or co.reconciliationStatus = :pendingStatus)
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.releaseDate is not null
      and co.releaseValue is not null
    order by co.releaseDate asc, co.releaseValue asc
  """)
  List<CreditOrderEntity> findEligibleForBankReconciliation(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus
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