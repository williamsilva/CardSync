package com.cardsync.domain.repository;

import com.cardsync.domain.model.TransactionAcqEntity;
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
public interface TransactionAcqRepository extends JpaRepository<TransactionAcqEntity, UUID>, JpaSpecificationExecutor<TransactionAcqEntity> {

  Optional<TransactionAcqEntity> findFirstByNsuAndAuthorization(Long nsu, String authorization);

  Optional<TransactionAcqEntity> findFirstByNsu(Long nsu);

  Optional<TransactionAcqEntity> findFirstByAuthorization(String authorization);

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
       and (:includeAlreadyReconciled = true
            or (a.saleReconciliationDate is null
                and (a.statusTransaction is null or a.statusTransaction in :pendingStatuses)))
  """)
  List<TransactionAcqEntity> findCandidatesForErpAcquirerReconciliationByNsus(
    @Param("nsus") Collection<Long> nsus,
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses
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
       and (:includeAlreadyReconciled = true
            or (a.saleReconciliationDate is null
                and (a.statusTransaction is null or a.statusTransaction in :pendingStatuses)))
  """)
  List<TransactionAcqEntity> findCandidatesForErpAcquirerReconciliationByAuthorizations(
    @Param("authorizations") Collection<String> authorizations,
    @Param("includeAlreadyReconciled") boolean includeAlreadyReconciled,
    @Param("pendingStatuses") Collection<Integer> pendingStatuses
  );
}
