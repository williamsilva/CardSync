package com.cardsync.domain.repository;

import com.cardsync.domain.model.ReleasesBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReleasesBankRepository extends JpaRepository<ReleasesBankEntity, UUID>, JpaSpecificationExecutor<ReleasesBankEntity> {

  @Query("""
    select rb
    from ReleasesBankEntity rb
    left join fetch rb.company
    left join fetch rb.acquirer
    left join fetch rb.establishment
    left join fetch rb.bankingDomicile
    left join fetch rb.flag
    left join fetch rb.bank
    left join fetch rb.processedFile
    where rb.reconciliationStatus = :pendingStatus
      and rb.releaseDate is not null
      and rb.releaseValue is not null
    order by rb.releaseDate asc, rb.releaseValue asc
  """)
  List<ReleasesBankEntity> findPendingForBankReconciliation(@Param("pendingStatus") Integer pendingStatus);
}
