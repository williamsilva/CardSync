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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionErpRepository extends JpaRepository<TransactionErpEntity, UUID>, JpaSpecificationExecutor<TransactionErpEntity> {

  Optional<TransactionErpEntity> findFirstByTransactionAcq_Id(UUID transactionAcqId);

  @Query("""
    select e from TransactionErpEntity e
    join fetch e.transactionAcq ta
    where ta.id in :transactionAcqIds
  """)
  List<TransactionErpEntity> findByTransactionAcqIdIn(@Param("transactionAcqIds") Collection<UUID> transactionAcqIds);

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
       and e.saleDate >= :implantationDate
       and e.saleDate >= :lookbackDate
       and upper(e.acquirer.fileIdentifier) = 'REDE'
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findRedeErpIdsForReconciliation(
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("""
    select e.id
      from TransactionErpEntity e
     where e.saleReconciliationDate is null
       and (e.statusTransaction is null or e.statusTransaction in :pendingStatuses)
       and e.capture = :manualCapture
       and e.statusTransactionReason in :sourceReasonCodes
       and (e.modality is not null and e.modality <> :excludedModality)
       and e.saleDate >= :implantationDate
       and e.saleDate >= :lookbackDate
       and upper(e.acquirer.fileIdentifier) = 'REDE'
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findRedeErpIdsForManualSwapReconciliation(
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("manualCapture") Integer manualCapture,
    @Param("sourceReasonCodes") Collection<Integer> sourceReasonCodes,
    @Param("excludedModality") Integer excludedModality,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("select t from TransactionErpEntity t where t.saleDate >= :lookback order by t.saleDate asc")
  List<TransactionErpEntity> findAllForDashboard(@Param("lookback") OffsetDateTime lookback);

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
  List<TransactionErpEntity> findRedeErpBatchForReconciliation(@Param("ids") Collection<UUID> ids);

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
       and (:includeAlreadyProcessed = true or e.feeReconciliationStatus is null or e.feeReconciliationStatus in :pendingFeeStatuses)
       and (:includeAlreadyProcessed = true or ta.feeReconciliationStatus is null or ta.feeReconciliationStatus in :pendingFeeStatuses)
       and e.saleDate >= :implantationDate
       and e.saleDate >= :lookbackDate
       and upper(e.acquirer.fileIdentifier) = 'REDE'
     order by e.saleDate asc, e.id asc
  """)
  List<UUID> findRedeErpIdsForFeeReconciliation(
    @Param("includeAlreadyProcessed") boolean includeAlreadyProcessed,
    @Param("reconciledStatuses") Collection<Integer> reconciledStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("pendingFeeStatuses") Collection<Integer> pendingFeeStatuses,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("""
    select distinct e
      from TransactionErpEntity e
      left join fetch e.acquirer
      left join fetch e.flag
      left join fetch e.company
      left join fetch e.establishment
      left join fetch e.installments
      join fetch e.transactionAcq ta
      left join fetch ta.acquirer
      left join fetch ta.flag
      left join fetch ta.company
      left join fetch ta.establishment
     where e.id in :ids
       and e.statusTransaction in :reconciledStatuses
       and ta.statusTransaction in :reconciledStatuses
       and e.modality is not null
       and e.modality <> :excludedModality
       and ta.modality is not null
       and ta.modality <> :excludedModality
       and (:includeAlreadyProcessed = true or e.feeReconciliationStatus is null or e.feeReconciliationStatus in :pendingFeeStatuses)
       and (:includeAlreadyProcessed = true or ta.feeReconciliationStatus is null or ta.feeReconciliationStatus in :pendingFeeStatuses)
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findRedeErpBatchForFeeReconciliation(
    @Param("ids") Collection<UUID> ids,
    @Param("includeAlreadyProcessed") boolean includeAlreadyProcessed,
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

  // company/establishment além de nsu+autorização+adquirente: NSU é um contador sequencial
  // por terminal/POS que pode se repetir entre empresas/estabelecimentos diferentes que
  // processam pela mesma adquirente — sem esse escopo, o reprocessamento de cancelamento
  // podia vincular e cancelar a venda ERP errada, de outro tenant.
  @Query("""
    select distinct e
      from TransactionErpEntity e
      left join fetch e.installments
      left join fetch e.adjustment
     where e.nsu = :nsu
       and lower(e.authorization) = lower(:authorization)
       and e.acquirer.id = :acquirerId
       and (:companyId is null or e.company.id = :companyId)
       and (:establishmentId is null or e.establishment.id = :establishmentId)
       and e.transactionAcq is null
     order by e.saleDate asc, e.id asc
  """)
  List<TransactionErpEntity> findUnlinkedByNsuAuthorizationAndAcquirerForCancellationReprocess(
    @Param("nsu") Long nsu,
    @Param("authorization") String authorization,
    @Param("acquirerId") UUID acquirerId,
    @Param("companyId") UUID companyId,
    @Param("establishmentId") UUID establishmentId
  );

}
