package com.cardsync.domain.repository;

import com.cardsync.domain.model.InstallmentAcqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentAcqRepository extends JpaRepository<InstallmentAcqEntity, UUID>, JpaSpecificationExecutor<InstallmentAcqEntity> {

  List<InstallmentAcqEntity> findByCreditOrder_Id(UUID creditOrderId);

  List<InstallmentAcqEntity> findByTransaction_Id(UUID transactionId);

  @Query("""
    select ia from InstallmentAcqEntity ia
    join ia.transaction tx
    where coalesce(ia.rvNumber, tx.rvNumber) = :rvNumber
      and tx.acquirer.id = :acquirerId
      and ia.installment = :installmentNumber
      and (:reprocess = true or ia.releaseBank is null)
  """)
  List<InstallmentAcqEntity> findPendingByRvNumberAcquirerAndInstallmentNumber(
    @Param("rvNumber") Integer rvNumber,
    @Param("acquirerId") UUID acquirerId,
    @Param("installmentNumber") Integer installmentNumber,
    @Param("reprocess") boolean reprocess
  );

  List<InstallmentAcqEntity> findByReleaseBank_Id(UUID releaseBankId);

  @Query("""
    select ia from InstallmentAcqEntity ia
    join fetch ia.transaction tx
    where tx.acquirer.id = :acquirerId
      and coalesce(ia.rvNumber, tx.rvNumber) in :rvNumbers
      and (:reprocess = true or ia.releaseBank is null)
  """)
  List<InstallmentAcqEntity> findByAcquirerIdAndRvNumbers(
    @Param("acquirerId") UUID acquirerId,
    @Param("rvNumbers") Collection<Integer> rvNumbers,
    @Param("reprocess") boolean reprocess
  );

  @Query("""
    select ia from InstallmentAcqEntity ia
    join fetch ia.transaction tx
    where tx.id in :transactionIds
  """)
  List<InstallmentAcqEntity> findByTransactionIdIn(@Param("transactionIds") Collection<UUID> transactionIds);

  /**
   * Versão "por empresa/lote" (substituiu a antiga findPendingForBankRelease, chamada uma vez por
   * release — até ~50 mil vezes numa execução completa, achado real 2026-09-11) — usada por
   * BankReconciliationService#reconcilePendingReleasesByInstallments para carregar o pool de
   * parcelas candidatas UMA VEZ por empresa presente no lote. Não filtra por
   * acquirer/establishment/flag/bankingDomicile no banco: essas checagens já são feitas em
   * memória contra o pool (ver isInstallmentCandidateCompatible/InstallmentMatchData), então
   * refazê-las aqui só estreitaria a query sem reduzir trabalho de verdade — o filtro que importa
   * pra reduzir volume é companyId + a janela de datas (aplicados abaixo). Busca também
   * ss.bankingDomicile (além de co.bankingDomicile) porque o filtro de bankingDomicile do
   * matching é opcional (ou co.bankingDomicile ou ss.bankingDomicile) e precisa ficar disponível
   * sem lazy-load posterior (a sessão do Hibernate é limpa a cada lote). Mantém o mesmo parâmetro
   * de reprocessamento da versão por release (:reprocess) para não mudar esse comportamento.
   */
  @Query("""
    select ia
    from InstallmentAcqEntity ia
    join fetch ia.transaction tx
    left join fetch tx.company
    left join fetch tx.acquirer
    left join fetch tx.establishment
    left join fetch tx.flag
    left join fetch tx.salesSummary ss
    left join fetch ss.bankingDomicile
    left join fetch ia.creditOrder co
    left join fetch co.bankingDomicile
    where (:reprocess = true or ia.releaseBank is null)
      and (ia.statusPaymentBank is null or ia.statusPaymentBank = :pendingStatus)
      and tx.company.id = :companyId
      and ia.expectedPaymentDate between :dateFrom and :dateTo
    order by ia.expectedPaymentDate asc, ia.liquidValue asc
  """)
  List<InstallmentAcqEntity> findPendingForCompanyAndDateRangeForInstallmentReconciliation(
    @Param("pendingStatus") Integer pendingStatus,
    @Param("companyId") UUID companyId,
    @Param("dateFrom") LocalDate dateFrom,
    @Param("dateTo") LocalDate dateTo,
    @Param("reprocess") boolean reprocess
  );
}
