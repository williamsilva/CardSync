package com.cardsync.domain.repository;

import com.cardsync.domain.model.ReleasesBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReleasesBankRepository extends JpaRepository<ReleasesBankEntity, UUID>, JpaSpecificationExecutor<ReleasesBankEntity> {

  @Query("select rb from ReleasesBankEntity rb where rb.releaseDate >= :lookback order by rb.releaseDate asc")
  List<ReleasesBankEntity> findAllForDashboard(@Param("lookback") LocalDate lookback);

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
    order by rb.releaseValue asc, rb.releaseDate asc
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
    order by rb.releaseValue asc, rb.releaseDate asc
  """)
  List<ReleasesBankEntity> findAvailableForCreditOrderBatch(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("reprocessAlreadyReconciled") boolean reprocessAlreadyReconciled,
    @Param("companyId") UUID companyId,
    @Param("dateFrom") java.time.LocalDate dateFrom,
    @Param("dateTo") java.time.LocalDate dateTo
  );

  /**
   * Diagnóstico de impacto do modo estrito de conciliação bancária (ver
   * ReconciliationSettingsEntity.flagMatchRequired): quantos lançamentos pendentes hoje
   * ficariam sem bandeira preenchida, e portanto sem poder casar automaticamente se a
   * regra virar obrigatória.
   */
  @Query("""
    select count(rb.id) from ReleasesBankEntity rb
    where (rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus)
      and rb.flag is null
  """)
  long countPendingWithoutFlag(@Param("pendingStatus") Integer pendingStatus);

  /**
   * Diagnóstico de impacto de establishmentMatchRequired: quantos lançamentos pendentes
   * hoje estão sem estabelecimento resolvido — depende da extração de PV a partir do
   * CNAB (ver BankStatementClassifierService/BankTextSignalResolver), hoje sem amostra
   * real nem teste automatizado. Acompanhar esta contagem antes de ligar o toggle.
   */
  @Query("""
    select count(rb.id) from ReleasesBankEntity rb
    where (rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus)
      and rb.establishment is null
  """)
  long countPendingWithoutEstablishment(@Param("pendingStatus") Integer pendingStatus);

  /**
   * Lançamentos ainda pendentes, para o backfill de reclassificação de bandeira
   * (BankStatementFlagReclassificationService) — escopo reduzido para não varrer a tabela
   * inteira (que cresce sem limite com o histórico) quando só os pendentes importam de fato
   * para conciliar agora.
   */
  @Query("""
    select rb from ReleasesBankEntity rb
    left join fetch rb.flag
    where rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus
  """)
  List<ReleasesBankEntity> findPendingForFlagReclassification(@Param("pendingStatus") Integer pendingStatus);

  /**
   * Lançamentos de recebimento (categoria RECEIPT) com modalidade não classificada, para o
   * backfill de reclassificação de modalidade (BankStatementModalityReclassificationService).
   * Sem restrição por reconciliationStatus (diferente do backfill de bandeira): o universo aqui
   * é bem menor (não cresce sem limite — só lançamentos que a esteira nunca conseguiu classificar)
   * e lançamentos já pagos precisam ser corrigidos também, já que ficam invisíveis no Extrato
   * Bancário (ReleasesBankSpecs só lista modalidade em {CASH_DEBIT, CASH_CREDIT, ANTECIP_CRED}).
   */
  @Query("""
    select rb from ReleasesBankEntity rb
    where rb.releaseCategory = :receiptCategory
      and rb.modalityPaymentBank = :unclassifiedModality
  """)
  List<ReleasesBankEntity> findUnclassifiedModalityForReclassification(
    @Param("receiptCategory") Integer receiptCategory,
    @Param("unclassifiedModality") Integer unclassifiedModality
  );

  /**
   * Lançamentos de recebimento (categoria RECEIPT) sem estabelecimento vinculado, para o backfill
   * de reclassificação de estabelecimento (BankStatementEstablishmentReclassificationService).
   * Causa raiz: BankTextSignalResolver#extractPvCandidates usava \\b\\d{5,12}\\b — \\b não separa
   * letra de dígito, então PVs colados direto num marcador de texto (ex.: "CD0007866470",
   * "350834GETNET-VISA") nunca eram extraídos e o estabelecimento nunca era resolvido.
   */
  @Query("""
    select rb from ReleasesBankEntity rb
    left join fetch rb.acquirer
    where rb.releaseCategory = :receiptCategory
      and rb.establishment is null
  """)
  List<ReleasesBankEntity> findWithoutEstablishmentForReclassification(
    @Param("receiptCategory") Integer receiptCategory
  );

}