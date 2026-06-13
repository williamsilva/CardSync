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
       and (e.modality is not null and e.modality <> :excludedModality)
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findIdsForErpAcquirerReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("excludedModality") Integer excludedModality
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
      left join fetch e.installments
      left join fetch e.transactionAcq ta
      left join fetch ta.adjustment
      left join fetch ta.salesSummary tas
      left join fetch tas.bankingDomicile
     where e.id in :ids
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findBatchForErpAcquirerReconciliation(@Param("ids") Collection<UUID> ids);



  @Query(
    value = """
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
       where e.statusTransactionReason = :reasonCode
         and (e.transactionAcq is null or e.saleReconciliationDate is null)
    """,
    countQuery = """
      select count(e)
        from TransactionErpEntity e
       where e.statusTransactionReason = :reasonCode
         and (e.transactionAcq is null or e.saleReconciliationDate is null)
    """
  )
  Page<TransactionErpEntity> findMissingInAcquirerForManualResolution(
    @Param("reasonCode") Integer reasonCode,
    Pageable pageable
  );

  @Query(
    value = """
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
       where e.statusTransactionReason in :reasonCodes
         and (e.saleReconciliationDate is null or e.transactionAcq is null)
    """,
    countQuery = """
      select count(e)
        from TransactionErpEntity e
       where e.statusTransactionReason in :reasonCodes
         and (e.saleReconciliationDate is null or e.transactionAcq is null)
    """
  )
  Page<TransactionErpEntity> findNotReconciledErpAcquirerDivergences(
    @Param("reasonCodes") Collection<Integer> reasonCodes,
    Pageable pageable
  );

  Optional<TransactionErpEntity> findFirstByTransactionAcq_Id(UUID transactionAcqId);

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
      left join fetch e.installments
     where e.id = :id
  """)
  Optional<TransactionErpEntity> findForManualResolutionById(@Param("id") UUID id);


  @Query("""
    select e.id
      from TransactionErpEntity e
      join e.transactionAcq ta
     where e.statusTransaction in :reconciledStatuses
       and ta.statusTransaction in :reconciledStatuses
       and e.modality is not null
       and e.modality <> :excludedModality
       and ta.modality is not null
       and ta.modality <> :excludedModality
       and (e.feeReconciliationStatus is null or e.feeReconciliationStatus in :pendingFeeStatuses)
       and (ta.feeReconciliationStatus is null or ta.feeReconciliationStatus in :pendingFeeStatuses)
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findIdsForErpAcquirerFeeReconciliation(
    @Param("reconciledStatuses") Collection<Integer> reconciledStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("pendingFeeStatuses") Collection<Integer> pendingFeeStatuses
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
      left join fetch e.installments
      join fetch e.transactionAcq ta
      left join fetch ta.acquirer
      left join fetch ta.flag
      left join fetch ta.company
      left join fetch ta.establishment
      left join fetch ta.adjustment
      left join fetch ta.installments
      left join fetch ta.salesSummary tas
      left join fetch tas.bankingDomicile
     where e.id in :ids
       and e.statusTransaction in :reconciledStatuses
       and ta.statusTransaction in :reconciledStatuses
       and e.modality is not null
       and e.modality <> :excludedModality
       and ta.modality is not null
       and ta.modality <> :excludedModality
       and (e.feeReconciliationStatus is null or e.feeReconciliationStatus in :pendingFeeStatuses)
       and (ta.feeReconciliationStatus is null or ta.feeReconciliationStatus in :pendingFeeStatuses)
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findBatchForErpAcquirerFeeReconciliation(
    @Param("ids") Collection<UUID> ids,
    @Param("reconciledStatuses") Collection<Integer> reconciledStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("pendingFeeStatuses") Collection<Integer> pendingFeeStatuses
  );

  @Query("""
    select distinct e
      from TransactionErpEntity e
      join fetch e.transactionAcq ta
      left join fetch e.installments
      left join fetch e.adjustment
     where ta.id in :transactionAcqIds
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findByTransactionAcqIdsForCancellationReconciliation(
    @Param("transactionAcqIds") Collection<UUID> transactionAcqIds
  );

}
