package com.cardsync.domain.repository;

import com.cardsync.domain.model.CreditOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditOrderRepository extends JpaRepository<CreditOrderEntity, UUID>, JpaSpecificationExecutor<CreditOrderEntity> {

  @Query("""
    select co
    from CreditOrderEntity co
    left join fetch co.salesSummary
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.company
    left join fetch co.bankingDomicile
    where co.releaseBank is null
      and (co.reconciliationStatus is null or co.reconciliationStatus = :pendingStatus)
      and co.company.id = :companyId
      and co.bankingDomicile.id = :bankingDomicileId
      and (:acquirerId is null or co.acquirer.id = :acquirerId)
      and co.releaseDate between :dateFrom and :dateTo
    order by co.releaseDate asc, co.releaseValue asc
  """)
  List<CreditOrderEntity> findPendingForBankRelease(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId,
    @Param("bankingDomicileId") UUID bankingDomicileId,
    @Param("dateFrom") LocalDate dateFrom,
    @Param("dateTo") LocalDate dateTo
  );
}
