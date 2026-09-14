package com.cardsync.domain.repository;

import com.cardsync.domain.model.AnticipationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnticipationRepository extends JpaRepository<AnticipationEntity, UUID>, JpaSpecificationExecutor<AnticipationEntity> {

  /**
   * Antecipações (Rede EEFI "036") que ainda não geraram sua CreditOrder sintética (Etapa 4 —
   * ver SalesSummaryCreditOrderReconciliationService.generateSyntheticCreditOrder(AnticipationEntity)).
   * Sem essa geração, o valor antecipado nunca vira CreditOrder e nunca participa da conciliação
   * bancária (Etapa 6) — fica pendente para sempre mesmo já tendo caído na conta.
   */
  @Query("""
    select a.id from AnticipationEntity a
     where (a.generatedOrders is null or a.generatedOrders = false)
       and a.releaseValue is not null
       and a.releaseValue <> 0
  """)
  List<UUID> findIdsEligibleForSyntheticCreditOrderGeneration();

  @Query("""
    select distinct a
      from AnticipationEntity a
      left join fetch a.acquirer
      left join fetch a.flag
      left join fetch a.company
      left join fetch a.bankingDomicile
      left join fetch a.processedFile
      left join fetch a.salesSummary
     where a.id in :ids
  """)
  List<AnticipationEntity> findBatchForSyntheticCreditOrderGeneration(@Param("ids") Collection<UUID> ids);

  /**
   * Achado real (auditoria 2026-09-13): quando o parser não resolve o domicílio bancário
   * (agência/conta não cadastrados), a antecipação - e a CreditOrder sintética gerada a partir
   * dela - nunca concilia com o extrato bancário. Antes, o único sinal disso era um log.warn no
   * momento da importação, sem nenhuma forma de encontrar depois quais antecipações ficaram
   * travadas assim.
   */
  @Query("""
    select count(a) from AnticipationEntity a
     where a.bankingDomicile is null
       and a.releaseValue is not null
       and a.releaseValue <> 0
  """)
  long countMissingBankingDomicile();

  @Query("""
    select a.id from AnticipationEntity a
     where a.bankingDomicile is null
       and a.releaseValue is not null
       and a.releaseValue <> 0
     order by a.releaseDate desc
  """)
  List<UUID> findIdsMissingBankingDomicile(Pageable pageable);
}
