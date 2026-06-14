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

  /**
   * Retorna os pares arquivo processado x domicílio bancário identificados durante
   * a importação. O DISTINCT impede que várias linhas do mesmo CNAB sejam contadas
   * como vários arquivos para o mesmo domicílio.
   */
  @Query("""
    select distinct rb.processedFile.id, rb.bankingDomicile.id
      from ReleasesBankEntity rb
     where rb.processedFile.id in :processedFileIds
       and rb.bankingDomicile is not null
  """)
  List<Object[]> findProcessedFileBankingDomiciles(
    @Param("processedFileIds") List<UUID> processedFileIds
  );

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
    where (:reprocessAlreadyReconciled = true or rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus)
      and rb.releaseDate is not null
      and rb.releaseValue is not null
    order by rb.releaseDate asc, rb.releaseValue asc
  """)
  List<ReleasesBankEntity> findForBankReconciliation(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("reprocessAlreadyReconciled") boolean reprocessAlreadyReconciled
  );

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
    where (:reprocessAlreadyReconciled = true or rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus)
      and rb.releaseDate between :dateFrom and :dateTo
      and rb.releaseValue is not null
      and rb.company.id = :companyId
    order by rb.releaseDate asc, rb.releaseValue asc
  """)
  List<ReleasesBankEntity> findAvailableForCreditOrderBatch(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("reprocessAlreadyReconciled") boolean reprocessAlreadyReconciled,
    @Param("companyId") UUID companyId,
    @Param("dateFrom") java.time.LocalDate dateFrom,
    @Param("dateTo") java.time.LocalDate dateTo
  );

}