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

  /**
   * Lançamentos de recebimento (categoria RECEIPT) sem adquirente vinculado, para o backfill de
   * reclassificação de adquirente (BankStatementAcquirerReclassificationService). O adquirente é
   * parte do contexto de casamento da conciliação automática (ver
   * BankReconciliationService#contextOf) — sem ele, um lançamento pode nunca casar mesmo tendo
   * ordem de crédito compatível em todo o resto.
   */
  @Query("""
    select rb from ReleasesBankEntity rb
    where rb.releaseCategory = :receiptCategory
      and rb.acquirer is null
  """)
  List<ReleasesBankEntity> findWithoutAcquirerForReclassification(
    @Param("receiptCategory") Integer receiptCategory
  );

  /**
   * Lançamentos pendentes elegíveis para as ferramentas de análise (divergência pré-implantação e
   * legado sem ordem de crédito) — restrito à categoria RECEIPT e às modalidades de cartão
   * ({@code CASH_DEBIT}/{@code CASH_CREDIT}/{@code ANTECIP_CRED}, mesmo escopo do Extrato Bancário
   * — ver ReleasesBankSpecs#getModalityPaymentBank), pra não desperdiçar a análise em PIX/TED/SISPAG
   * e afins, que nunca terão ordem de crédito candidata. {@code releaseDate >= :goLiveDate} segue o
   * mesmo corte de go-live aplicado nas demais listagens (ver ReleasesBankSpecs/CreditOrderSpecs/
   * TransactionAcqSpecs etc. via ImplantationDateProvider) — lançamentos anteriores à implantação
   * já são tratados como legado por natureza, não por esta análise. Também exige
   * {@code rb.acquirer is not null}: sem adquirente não há como localizar uma CreditOrder
   * candidata (ver findWithoutAcquirerForReclassification, a ferramenta que existe justamente
   * pra corrigir esses lançamentos antes de entrarem nesta análise).
   */
  @Query("""
    select rb
    from ReleasesBankEntity rb
    left join fetch rb.company
    left join fetch rb.acquirer
    left join fetch rb.establishment
    left join fetch rb.bankingDomicile
    left join fetch rb.flag
    left join fetch rb.bank
    where (rb.reconciliationStatus is null or rb.reconciliationStatus = :pendingStatus)
      and rb.releaseCategory = :receiptCategory
      and rb.modalityPaymentBank in :modalityCodes
      and rb.acquirer is not null
      and rb.releaseDate is not null
      and rb.releaseValue is not null
      and rb.releaseDate >= :goLiveDate
    order by rb.releaseValue asc, rb.releaseDate asc
  """)
  List<ReleasesBankEntity> findPendingForPreImplantationDivergence(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("receiptCategory") Integer receiptCategory,
    @Param("modalityCodes") List<Integer> modalityCodes,
    @Param("goLiveDate") LocalDate goLiveDate
  );

}