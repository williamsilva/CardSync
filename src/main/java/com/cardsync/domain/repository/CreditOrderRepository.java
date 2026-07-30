package com.cardsync.domain.repository;

import com.cardsync.domain.model.CreditOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CreditOrderRepository extends JpaRepository<CreditOrderEntity, UUID>, JpaSpecificationExecutor<CreditOrderEntity> {

  boolean existsBySalesSummary_IdAndInstallmentNumber(UUID salesSummaryId, Integer installmentNumber);

  /**
   * Busca via query em vez de navegar {@code SalesSummaryEntity.getCreditOrders()} (coleção
   * lazy) — usada no laço de conciliação bancária, onde a sessão pode ser limpa
   * periodicamente (ver BankReconciliationService); navegar a coleção lazy de uma entidade
   * já desanexada geraria LazyInitializationException.
   */
  List<CreditOrderEntity> findBySalesSummary_Id(UUID salesSummaryId);

  /**
   * Mesmo motivo do método acima (evitar coleção lazy numa entidade possivelmente desanexada),
   * mas em lote: busca as ordens de TODOS os resumos afetados numa única query, em vez de uma
   * query por resumo — usada no recomputo em lote ao final de um lote de conciliação bancária.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.salesSummary
    where co.salesSummary.id in :salesSummaryIds
  """)
  List<CreditOrderEntity> findBySalesSummary_IdIn(@Param("salesSummaryIds") Collection<UUID> salesSummaryIds);

  @Query("""
    SELECT co FROM CreditOrderEntity co
    LEFT JOIN FETCH co.salesSummary
    WHERE co.releaseBank IS NULL
      AND co.statusPaymentBank = :pendingStatus
      AND (co.releaseValue IS NULL OR co.releaseValue = :zeroValue)
    """)
  List<CreditOrderEntity> findPendingZeroValueOrders(
      @Param("pendingStatus") Integer pendingStatus,
      @Param("zeroValue") BigDecimal zeroValue);

  /**
   * Agrupado por empresa e ordenado por data dentro de cada empresa (não por id global) para
   * permitir montar lotes que nunca dividam, entre dois lotes diferentes, ordens de uma mesma
   * empresa cuja data esteja próxima o bastante para caírem na janela de tolerância do mesmo
   * lançamento bancário — ver {@link com.cardsync.core.reconciliation.BankReconciliationService},
   * que empacota lotes a partir deste resultado só cortando em lacunas de data seguras.
   */
  @Query("""
    select co.company.id, co.id, co.releaseDate
    from CreditOrderEntity co
    where (:reprocess = true or co.releaseBank is null)
      and co.salesSummaryStatus = :summaryReconciledStatus
      and (:reprocess = true or co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus))
      and co.releaseDate is not null
      and co.releaseValue is not null
      and co.company is not null
      and co.bankingDomicile is not null
    order by co.company.id asc, co.releaseDate asc, co.id asc
  """)
  List<Object[]> findEligibleIdsGroupedByCompanyForBankReconciliation(
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus,
    @Param("reprocess") boolean reprocess
  );

  @Query("""
    select distinct co
    from CreditOrderEntity co
    left join fetch co.salesSummary
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.company
    left join fetch co.bankingDomicile
    where co.id in :ids
      and (:reprocess = true or co.releaseBank is null)
      and co.salesSummaryStatus = :summaryReconciledStatus
      and (:reprocess = true or co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus))
      and co.releaseDate is not null
      and co.releaseValue is not null
    order by co.releaseDate asc, co.id asc
  """)
  List<CreditOrderEntity> findEligibleByIdsForBankReconciliation(
    @Param("ids") List<UUID> ids,
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus,
    @Param("reprocess") boolean reprocess
  );

  /**
   * Candidatas para o backfill de divergência pré-implantação
   * (PreImplantationDivergenceReconciliationService): mesma empresa, ainda pendentes, sem
   * lançamento vinculado, com o resumo de venda já reconciliado (mesma precondição usada na
   * conciliação automática — ver findEligibleIdsGroupedByCompanyForBankReconciliation) e dentro
   * da janela de data configurada. Compatibilidade fina (banco/bandeira/estabelecimento/
   * modalidade) é resolvida depois em Java via
   * BankReconciliationService#isCreditOrderCandidateCompatible, para reusar a mesma lógica do
   * matcher automático.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.company
    left join fetch co.acquirer
    left join fetch co.flag
    left join fetch co.salesSummary
    left join fetch co.bankingDomicile bd
    left join fetch bd.bank
    where co.company.id = :companyId
      and co.releaseBank is null
      and co.statusPaymentBank = :pendingStatus
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.releaseDate between :from and :to
  """)
  List<CreditOrderEntity> findCandidatesForPreImplantationDivergence(
    @Param("companyId") UUID companyId,
    @Param("pendingStatus") Integer pendingStatus,
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("from") LocalDate from,
    @Param("to") LocalDate to
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.salesSummaryStatus = :status
     where co.salesSummary.id in :salesSummaryIds
       and (co.salesSummaryStatus is null or co.salesSummaryStatus <> :status)
  """)
  int updateSalesSummaryStatusBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.statusPaymentBank = :status
     where co.salesSummary.id in :salesSummaryIds
       and co.statusPaymentBank is null
  """)
  int updateNullStatusPaymentBankBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.reconciliationStatus = :status
     where co.salesSummary.id in :salesSummaryIds
       and co.reconciliationStatus is null
  """)
  int updateNullReconciliationStatusBySalesSummaryIds(
    @Param("salesSummaryIds") List<UUID> salesSummaryIds,
    @Param("status") Integer status
  );

  /**
   * Diagnóstico: conta CreditOrders sem SalesSummary vinculado.
   * Um valor > 0 indica que os arquivos de ordem de crédito foram processados mas a
   * vinculação com o resumo de vendas não foi estabelecida (possível divergência de RV/PV).
   */
  @Query("select count(co.id) from CreditOrderEntity co where co.salesSummary is null")
  long countWithoutSalesSummary();

  /**
   * Pré-vinculação: retorna IDs de CreditOrder órfãs (salesSummary = NULL) com
   * acquirer, pvCentralizer e rvNumber preenchidos, dentro do período configurado.
   */
  @Query("""
    select co.id from CreditOrderEntity co
    where co.salesSummary is null
      and co.acquirer is not null
      and co.pvCentralizer is not null
      and co.rvNumber is not null
      and co.rvDate >= :implantationDate
      and co.rvDate >= :lookbackDate
    order by co.rvDate asc, co.id asc
  """)
  List<UUID> findOrphanedIdsWithinDateRange(
    @Param("implantationDate") LocalDate implantationDate,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Mesma busca acima, mas sem o filtro de lookback — usada no backfill único
   * (ignoreLookback=true) para vincular órfãs antigas que já saíram da janela normal.
   */
  @Query("""
    select co.id from CreditOrderEntity co
    where co.salesSummary is null
      and co.acquirer is not null
      and co.pvCentralizer is not null
      and co.rvNumber is not null
      and co.rvDate >= :implantationDate
    order by co.rvDate asc, co.id asc
  """)
  List<UUID> findOrphanedIdsIgnoringLookback(@Param("implantationDate") LocalDate implantationDate);

  /**
   * Órfãs (sem SalesSummary) com rvDate ANTERIOR à implantação — excluídas por desenho do
   * backfill padrão ({@link #findOrphanedIdsWithinDateRange}/{@link #findOrphanedIdsIgnoringLookback},
   * ambas exigem {@code rvDate >= implantationDate}). Usada pelo backfill dedicado de vínculo
   * pré-implantação (ver CreditOrderPreImplantationLinkingService), criado ao investigar por que
   * milhares de ordens nunca chegam a ser elegíveis pra conciliação bancária: nunca tiveram
   * SalesSummary vinculado porque seu rvDate é anterior ao go-live.
   */
  @Query("""
    select co.id from CreditOrderEntity co
    where co.salesSummary is null
      and co.acquirer is not null
      and co.pvCentralizer is not null
      and co.rvNumber is not null
      and co.rvDate < :implantationDate
    order by co.rvDate asc, co.id asc
  """)
  List<UUID> findOrphanedIdsBeforeImplantation(@Param("implantationDate") LocalDate implantationDate);

  /**
   * Pré-vinculação: carrega CreditOrder órfãs por IDs com acquirer em fetch join.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.acquirer
    where co.id in :ids
      and co.salesSummary is null
  """)
  List<CreditOrderEntity> findOrphanedByIds(@Param("ids") Collection<UUID> ids);

  /**
   * Mesma busca acima, mas também carrega company — usada pelo backfill de vínculo
   * pré-implantação, que expõe o nome da empresa na prévia.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.acquirer
    left join fetch co.company
    where co.id in :ids
      and co.salesSummary is null
  """)
  List<CreditOrderEntity> findOrphanedByIdsWithCompany(@Param("ids") Collection<UUID> ids);

  /**
   * Vinculação direta após criação manual: busca CreditOrders órfãs para um resumo
   * específico identificado por acquirer + pvCentralizer + rvNumber.
   */
  @Query("""
    select co from CreditOrderEntity co
    where co.salesSummary is null
      and co.acquirer.id = :acquirerId
      and co.pvCentralizer = :pvNumber
      and co.rvNumber = :rvNumber
  """)
  List<CreditOrderEntity> findOrphanedForSummary(
    @Param("acquirerId") UUID acquirerId,
    @Param("pvNumber") Integer pvNumber,
    @Param("rvNumber") Integer rvNumber
  );

  /**
   * Diagnóstico de mismatch PV: busca CreditOrders órfãs (sem salesSummary) que
   * compartilham acquirer+rvNumber com algum SalesSummary pendente, independentemente
   * de pvCentralizer. Usado para detectar se a raiz do problema é divergência de PV.
   */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.acquirer
    where co.salesSummary is null
      and co.acquirer.id in :acquirerIds
      and co.rvNumber in :rvNumbers
  """)
  List<CreditOrderEntity> findOrphanedByAcquirerIdsAndRvNumbers(
    @Param("acquirerIds") Collection<UUID> acquirerIds,
    @Param("rvNumbers") Collection<Integer> rvNumbers
  );

  /**
   * Reparo de consistência: atualiza salesSummaryStatus para RECONCILED em ordens de crédito
   * cujo resumo de vendas já está conciliado mas a ordem ainda tem salesSummaryStatus inconsistente.
   * Ocorre quando ordens são vinculadas ao resumo após ele já ter sido conciliado.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.salesSummaryStatus = :reconciledStatus
     where co.salesSummary.creditOrderStatus = :reconciledStatus
       and co.salesSummary.rvDate >= :lookbackDate
       and (co.salesSummaryStatus is null or co.salesSummaryStatus <> :reconciledStatus)
  """)
  int syncSalesSummaryStatusForReconciledSummaries(
    @Param("reconciledStatus") Integer reconciledStatus,
    @Param("lookbackDate") LocalDate lookbackDate
  );

  /**
   * Mesmo reparo acima, sem filtro de data — usado no backfill único (ignoreLookback=true)
   * para corrigir ordens de resumos antigos que já saíram da janela normal.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update CreditOrderEntity co
       set co.salesSummaryStatus = :reconciledStatus
     where co.salesSummary.creditOrderStatus = :reconciledStatus
       and (co.salesSummaryStatus is null or co.salesSummaryStatus <> :reconciledStatus)
  """)
  int syncSalesSummaryStatusForReconciledSummariesIgnoringLookback(
    @Param("reconciledStatus") Integer reconciledStatus
  );

  /** Retorna os pvCentralizer distintos presentes em um arquivo processado (EEFI). */
  @Query("""
    select distinct co.pvCentralizer
      from CreditOrderEntity co
     where co.processedFile.id = :processedFileId
       and co.pvCentralizer is not null
  """)
  Set<Integer> findDistinctPvCentralizerByProcessedFileId(@Param("processedFileId") UUID processedFileId);

  /** Retorna pvCentralizer + acquirer distintos de um arquivo processado (EEFI) para auto-cadastro. */
  @Query("""
    select distinct co.pvCentralizer, co.acquirer
      from CreditOrderEntity co
     where co.processedFile.id = :processedFileId
       and co.pvCentralizer is not null
       and co.acquirer is not null
  """)
  List<Object[]> findDistinctPvCentralizerWithAcquirerByProcessedFileId(@Param("processedFileId") UUID processedFileId);

  /** Carrega os pvCentralizer de múltiplos arquivos de uma vez para evitar N+1 no calendário. */
  @Query("""
    select co.processedFile.id, co.pvCentralizer
      from CreditOrderEntity co
     where co.processedFile.id in :fileIds
       and co.pvCentralizer is not null
  """)
  List<Object[]> findPvCentralizerByProcessedFileIds(@Param("fileIds") Collection<UUID> fileIds);

  /** Retorna o maior installmentNumber existente para um resumo, ou 0 se nenhum existe. */
  @Query("select coalesce(max(co.installmentNumber), 0) from CreditOrderEntity co where co.salesSummary.id = :summaryId")
  int findMaxInstallmentNumberBySalesSummaryId(@Param("summaryId") UUID summaryId);

  /** Desfazer conciliação: carrega as ordens vinculadas a um lançamento bancário, com o resumo em fetch join. */
  @Query("""
    select co from CreditOrderEntity co
    left join fetch co.salesSummary
    where co.releaseBank.id = :releaseBankId
  """)
  List<CreditOrderEntity> findByReleaseBank_Id(@Param("releaseBankId") UUID releaseBankId);

  /** Retorna todos os installmentNumbers existentes para um resumo. */
  @Query("select co.installmentNumber from CreditOrderEntity co where co.salesSummary.id = :summaryId")
  Set<Integer> findInstallmentNumbersBySalesSummaryId(@Param("summaryId") UUID summaryId);

  /**
   * Mesma consulta acima, mas em lote — usada na prévia da data da próxima ordem de crédito na
   * listagem de Ordem de Pagamento Manual (ver CreditOrderManualService), evitando uma consulta
   * por linha da página. Cada linha do retorno é (salesSummary.id, installmentNumber).
   */
  @Query("select co.salesSummary.id, co.installmentNumber from CreditOrderEntity co where co.salesSummary.id in :salesSummaryIds")
  List<Object[]> findInstallmentNumbersBySalesSummaryIdIn(@Param("salesSummaryIds") Collection<UUID> salesSummaryIds);

  /**
   * Diagnóstico de impacto do modo estrito de conciliação bancária (ver
   * ReconciliationSettingsEntity.flagMatchRequired): quantas ordens hoje elegíveis para a
   * Etapa 7 ficariam sem bandeira preenchida, e portanto sem poder casar automaticamente
   * se a regra virar obrigatória.
   */
  @Query("""
    select count(co.id) from CreditOrderEntity co
    where co.releaseBank is null
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus)
      and co.flag is null
  """)
  long countEligiblePendingWithoutFlag(
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus
  );

  /**
   * Sanity check: pvCentralizer é o identificador de estabelecimento usado no matching
   * (ver ReconciliationMatchContext.establishmentPv) e é sempre preenchido na ingestão
   * (ver ProcessRedeEeFiService/SalesSummaryCreditOrderReconciliationService) — o
   * esperado é ~0. Um valor alto aqui indicaria um problema de dados anterior ao
   * problema de extração do CNAB do lado do lançamento bancário.
   */
  @Query("""
    select count(co.id) from CreditOrderEntity co
    where co.releaseBank is null
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus)
      and co.pvCentralizer is null
  """)
  long countEligiblePendingWithoutPvCentralizer(
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus
  );

  /**
   * Diagnóstico de impacto de paymentKindMatchRequired: quantas ordens elegíveis têm
   * modalidade que ainda cai em PaymentKind.UNKNOWN (ver
   * BankReconciliationService.paymentKindFromModality — hoje só o código 0/ausente).
   */
  @Query("""
    select count(co.id) from CreditOrderEntity co
    where co.releaseBank is null
      and co.salesSummaryStatus = :summaryReconciledStatus
      and co.statusPaymentBank in (:paymentPendingStatus, :paymentPartialStatus)
      and (co.salesSummary is null or co.salesSummary.modality is null or co.salesSummary.modality = 0)
  """)
  long countEligiblePendingWithUnknownPaymentKind(
    @Param("summaryReconciledStatus") Integer summaryReconciledStatus,
    @Param("paymentPendingStatus") Integer paymentPendingStatus,
    @Param("paymentPartialStatus") Integer paymentPartialStatus
  );
}