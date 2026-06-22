package com.cardsync.domain.repository;

import com.cardsync.domain.model.TransactionAcqEntity;
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
public interface TransactionAcqRepository extends JpaRepository<TransactionAcqEntity, UUID>, JpaSpecificationExecutor<TransactionAcqEntity> {

  Optional<TransactionAcqEntity> findFirstByNsuAndAuthorization(Long nsu, String authorization);

  /**
   * Carrega transações já persistidas cujo NSU esteja no conjunto informado.
   * Usado para deduplicação em nível de transação durante o reprocessamento de
   * arquivos (o mesmo conjunto de vendas pode chegar em arquivos com bytes
   * diferentes, escapando da guarda por hash de conteúdo).
   */
  @Query("select t from TransactionAcqEntity t where t.nsu in :nsus")
  List<TransactionAcqEntity> findExistingByNsus(@Param("nsus") Collection<Long> nsus);

  Optional<TransactionAcqEntity> findFirstByNsu(Long nsu);

  Optional<TransactionAcqEntity> findFirstBySalesSummary_IdAndNsuAndAuthorizationOrderBySaleDateDesc(
    UUID salesSummaryId,
    Long nsu,
    String authorization
  );

  Optional<TransactionAcqEntity> findFirstBySalesSummary_IdAndNsuOrderBySaleDateDesc(
    UUID salesSummaryId,
    Long nsu
  );

  Optional<TransactionAcqEntity> findFirstByAcquirer_IdAndEstablishment_PvNumberAndRvNumberAndNsuAndAuthorizationOrderBySaleDateDesc(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber,
    Long nsu,
    String authorization
  );

  Optional<TransactionAcqEntity> findFirstByAcquirer_IdAndEstablishment_PvNumberAndRvNumberAndNsuOrderBySaleDateDesc(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber,
    Long nsu
  );

  @Query("""
    select a.id
      from TransactionAcqEntity a
     where not exists (
       select 1
         from TransactionErpEntity e
        where e.transactionAcq = a
     )
       and (:includeAlreadyReconciled = true
            or (a.saleReconciliationDate is null
                and (a.statusTransaction is null or a.statusTransaction in :pendingStatuses)))
       and (a.modality is not null and a.modality <> :excludedModality)
       and (a.statusTransaction is null
            or a.statusTransaction <> :notReconciledStatus
            or a.statusTransactionReason is null
            or a.statusTransactionReason = :nullReason)
       and a.saleDate >= :implantationDate
       and a.saleDate >= :lookbackDate
       and upper(a.acquirer.fileIdentifier) = 'REDE'
     order by a.saleDate asc, a.id asc
  """)
  List<UUID> findRedeAcqIdsForMissingInErpClassification(
    Pageable pageable,
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("notReconciledStatus") Integer notReconciledStatus,
    @Param("nullReason") Integer nullReason,
    @Param("excludedModality") Integer excludedModality,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where a.id in :ids
     order by a.saleDate asc, a.id asc
  """)
  List<TransactionAcqEntity> findBatchForMissingInErpStatusClassification(@Param("ids") Collection<UUID> ids);

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where a.nsu in :nsus
       and (a.modality is not null and a.modality <> :excludedModality)
       and a.saleDate >= :implantationDate
       and a.saleDate >= :lookbackDate
       and upper(a.acquirer.fileIdentifier) = 'REDE'
       and (:includeAlreadyReconciled = true
            or (a.saleReconciliationDate is null
                and (a.statusTransaction is null or a.statusTransaction in :pendingStatuses)))
  """)
  List<TransactionAcqEntity> findRedeAcqCandidatesForReconciliationByNsus(
    @Param("nsus") Collection<Long> nsus,
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where lower(a.authorization) in :authorizations
       and (a.modality is not null and a.modality <> :excludedModality)
       and a.saleDate >= :implantationDate
       and a.saleDate >= :lookbackDate
       and upper(a.acquirer.fileIdentifier) = 'REDE'
       and (:includeAlreadyReconciled = true
            or (a.saleReconciliationDate is null
                and (a.statusTransaction is null or a.statusTransaction in :pendingStatuses)))
  """)
  List<TransactionAcqEntity> findRedeAcqCandidatesForReconciliationByAuthorizations(
    @Param("authorizations") Collection<String> authorizations,
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses,
    @Param("excludedModality") Integer excludedModality,
    @Param("implantationDate") OffsetDateTime implantationDate,
    @Param("lookbackDate") OffsetDateTime lookbackDate
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
      left join fetch a.installments
     where a.id = :id
  """)
  Optional<TransactionAcqEntity> findForManualResolutionById(@Param("id") UUID id);

  @Query("""
    select a.id
      from TransactionAcqEntity a
     where a.statusTransaction = :canceledStatus
       and function('YEAR', a.saleDate) = :year
       and function('MONTH', a.saleDate) = :month
     order by a.saleDate asc, a.id asc
  """)
  List<UUID> findCancelledAcqIdsForMonthReprocess(
    @Param("canceledStatus") Integer canceledStatus,
    @Param("year") int year,
    @Param("month") int month
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.installments
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where a.id in :ids
     order by a.saleDate asc, a.id asc
  """)
  List<TransactionAcqEntity> findBatchForCancelledMonthReprocess(
    @Param("ids") Collection<UUID> ids
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where (:nsu is null or a.nsu = :nsu)
       and (:authorization is null or lower(a.authorization) = lower(:authorization))
       and (:acquirerId is null or a.acquirer.id = :acquirerId)
     order by a.saleDate desc, a.id desc
  """)
  List<TransactionAcqEntity> findCandidatesForOtherDivergencePair(
    @Param("nsu") Long nsu,
    @Param("authorization") String authorization,
    @Param("acquirerId") UUID acquirerId,
    Pageable pageable
  );
}