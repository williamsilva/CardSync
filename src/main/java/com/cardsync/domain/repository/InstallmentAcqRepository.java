package com.cardsync.domain.repository;

import com.cardsync.domain.model.InstallmentAcqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentAcqRepository extends JpaRepository<InstallmentAcqEntity, UUID>, JpaSpecificationExecutor<InstallmentAcqEntity> {

  List<InstallmentAcqEntity> findByCreditOrder_Id(UUID creditOrderId);

  List<InstallmentAcqEntity> findByTransaction_Id(UUID transactionId);

  List<InstallmentAcqEntity> findByReleaseBank_Id(UUID releaseBankId);

  @Query("""
    select ia
    from InstallmentAcqEntity ia
    join fetch ia.transaction tx
    left join fetch tx.company
    left join fetch tx.acquirer
    left join fetch tx.establishment
    left join fetch tx.flag
    left join fetch tx.salesSummary
    left join fetch ia.creditOrder
    where ia.releaseBank is null
      and (ia.statusPaymentBank is null or ia.statusPaymentBank = :pendingStatus)
      and tx.company.id = :companyId
      and (:acquirerId is null or tx.acquirer.id = :acquirerId)
      and (:establishmentId is null or tx.establishment.id = :establishmentId)
      and ia.expectedPaymentDate between :dateFrom and :dateTo
    order by ia.expectedPaymentDate asc, ia.liquidValue asc
  """)
  List<InstallmentAcqEntity> findPendingForBankRelease(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId,
    @Param("establishmentId") UUID establishmentId,
    @Param("dateFrom") LocalDate dateFrom,
    @Param("dateTo") LocalDate dateTo
  );
}
