package com.cardsync.domain.repository;

import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionErpRepository extends JpaRepository<TransactionErpEntity, UUID>, JpaSpecificationExecutor<TransactionErpEntity> {

  Page<TransactionErpEntity> findByCommercialStatusIn(Collection<ErpCommercialStatusEnum> statuses, Pageable pageable);

  Optional<TransactionErpEntity> findByIdAndCommercialStatusIn(UUID id, Collection<ErpCommercialStatusEnum> statuses);

  List<TransactionErpEntity> findTop500ByCommercialStatusInOrderBySaleDateAsc(Collection<ErpCommercialStatusEnum> statuses);

  @Query("""
    select e.id
      from TransactionErpEntity e
     where (:includeAlreadyReconciled = true
            or (e.saleReconciliationDate is null
                and (e.statusTransaction is null or e.statusTransaction in :pendingStatuses)))
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findIdsForErpAcquirerReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses
  );

  @Query("""
    select distinct e
      from TransactionErpEntity e
      left join fetch e.acquirer
      left join fetch e.flag
      left join fetch e.company
      left join fetch e.establishment
      left join fetch e.bankingDomicile
      left join fetch e.adjustment
      left join fetch e.transactionAcq ta
      left join fetch ta.adjustment
      left join fetch ta.salesSummary tas
      left join fetch tas.bankingDomicile
     where e.id in :ids
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findBatchForErpAcquirerReconciliation(@Param("ids") Collection<UUID> ids);
}
