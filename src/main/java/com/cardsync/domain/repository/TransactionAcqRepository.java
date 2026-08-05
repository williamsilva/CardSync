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
import java.util.Set;
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

  @Query("select coalesce(max(t.installment), 1) from TransactionAcqEntity t where t.salesSummary.id = :summaryId")
  int findMaxInstallmentBySalesSummaryId(@Param("summaryId") UUID summaryId);

  /**
   * Mesma agregação acima, mas em lote — usada para a prévia do valor da próxima ordem de
   * crédito na listagem de Ordem de Pagamento Manual (ver CreditOrderManualService), evitando
   * uma consulta por linha da página. Resumos sem nenhuma transação não aparecem no resultado
   * (GROUP BY não gera linha vazia) — o chamador deve tratar ausência como installmentTotal=1,
   * mesmo default do coalesce acima.
   */
  @Query("""
    select t.salesSummary.id, max(t.installment)
    from TransactionAcqEntity t
    where t.salesSummary.id in :salesSummaryIds
    group by t.salesSummary.id
  """)
  List<Object[]> findMaxInstallmentBySalesSummaryIdIn(@Param("salesSummaryIds") Collection<UUID> salesSummaryIds);

  List<TransactionAcqEntity> findBySalesSummary_Id(UUID salesSummaryId);

  /**
   * Mesmo motivo do método acima, mas em lote: busca as transações de TODOS os resumos
   * afetados numa única query, em vez de uma query por resumo — usada no recomputo em lote ao
   * final de um lote de conciliação bancária (ver BankReconciliationService).
   */
  @Query("""
    select t from TransactionAcqEntity t
    left join fetch t.salesSummary
    where t.salesSummary.id in :salesSummaryIds
  """)
  List<TransactionAcqEntity> findBySalesSummary_IdIn(@Param("salesSummaryIds") Collection<UUID> salesSummaryIds);

  /**
   * Ids de SalesSummary com pelo menos uma transação PAID/DIVERGENT — candidatos a ter
   * creditOrderStatus desatualizado (ver BankReconciliationService#recomputeAllSalesSummariesFromTransactions).
   */
  @Query("""
    select distinct t.salesSummary.id from TransactionAcqEntity t
    where t.salesSummary is not null
      and t.statusPaymentBank in :statusCodes
  """)
  Set<UUID> findSalesSummaryIdsByStatusPaymentBankIn(@Param("statusCodes") Collection<Integer> statusCodes);

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
       and a.acquirer.id = :acquirerId
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
    @Param("lookbackDate") OffsetDateTime lookbackDate,
    @Param("acquirerId") UUID acquirerId
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
      left join fetch a.establishment est
      left join fetch est.company
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where a.nsu in :nsus
       and (a.modality is not null and a.modality <> :excludedModality)
       and a.saleDate >= :implantationDate
       and a.saleDate >= :lookbackDate
       and a.acquirer.id = :acquirerId
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
    @Param("lookbackDate") OffsetDateTime lookbackDate,
    @Param("acquirerId") UUID acquirerId
  );

  @Query("""
    select distinct a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment est
      left join fetch est.company
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where lower(a.authorization) in :authorizations
       and (a.modality is not null and a.modality <> :excludedModality)
       and a.saleDate >= :implantationDate
       and a.saleDate >= :lookbackDate
       and a.acquirer.id = :acquirerId
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
    @Param("lookbackDate") OffsetDateTime lookbackDate,
    @Param("acquirerId") UUID acquirerId
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
       and (
         not exists (select 1 from TransactionErpEntity erp where erp.transactionAcq = a)
         or exists (
           select 1 from TransactionErpEntity erp
            where erp.transactionAcq = a
              and (erp.statusTransaction is null or erp.statusTransaction <> :canceledStatus)
         )
       )
     order by a.saleDate asc, a.id asc
  """)
  List<UUID> findCancelledAcqIdsForPipelineReprocess(
    @Param("canceledStatus") Integer canceledStatus
  );

  @Query("""
    select a.id
      from TransactionAcqEntity a
     where a.statusTransaction = :canceledStatus
       and function('date_part', 'year', a.saleDate) = :year
       and function('date_part', 'month', a.saleDate) = :month
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

  /**
   * Busca em lote os candidatos de "other-divergences": carrega todos os NSUs da página de
   * uma vez (em vez de 1 query por linha da página) e o filtro fino por authorization/
   * acquirerId, que é específico de cada ERP, é aplicado em memória no chamador.
   */
  @Query("""
    select a
      from TransactionAcqEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.establishment
      left join fetch a.adjustment
      left join fetch a.salesSummary ss
      left join fetch ss.bankingDomicile
     where a.nsu in :nsus
     order by a.nsu, a.saleDate desc, a.id desc
  """)
  List<TransactionAcqEntity> findCandidatesForOtherDivergencePairByNsuIn(@Param("nsus") Collection<Long> nsus);
}