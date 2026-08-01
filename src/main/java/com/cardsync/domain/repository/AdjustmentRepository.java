package com.cardsync.domain.repository;

import com.cardsync.domain.model.AdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdjustmentRepository extends JpaRepository<AdjustmentEntity, UUID>, JpaSpecificationExecutor<AdjustmentEntity> {

  @Query("""
    select adj.id
      from AdjustmentEntity adj
      join adj.transaction tx
     where adj.cancellationValueRequested is not null
       and adj.cancellationValueRequested > 0
       and (:reprocess = true
            or tx.statusTransaction is null
            or tx.statusTransaction <> :canceledStatus
            or exists (
                 select 1
                   from TransactionErpEntity erp
                  where erp.transactionAcq = tx
                    and (erp.statusTransaction is null or erp.statusTransaction <> :canceledStatus)
            ))
     order by adj.id
  """)
  List<UUID> findIdsForAcquirerSaleCancellationReconciliation(
    @Param("reprocess") boolean reprocess,
    @Param("canceledStatus") Integer canceledStatus
  );

  @Query("""
    select distinct adj
      from AdjustmentEntity adj
      join fetch adj.transaction tx
      left join fetch tx.installments
      left join fetch tx.adjustment
      left join fetch tx.salesSummary ss
      left join fetch ss.bankingDomicile
      left join fetch adj.acquirer
      left join fetch adj.company
      left join fetch adj.establishment
     where adj.id in :ids
     order by adj.id
  """)
  List<AdjustmentEntity> findBatchForAcquirerSaleCancellationReconciliation(@Param("ids") Collection<UUID> ids);

  /**
   * Soma de ajustes de débito de UM resumo — qualquer adjustmentType/motivo, não só POS_FEE.
   * Usada por CreditOrderManualService e SalesSummaryCreditOrderReconciliationService pra
   * descontar do valor da ordem de crédito gerada (releaseValue) — confirmado com dados reais:
   * RV 338015830 tinha liquidValue=52,29 e 2 ajustes de débito somando exatamente 52,29 (tarifa
   * de POS + cancelamento de venda débito), mas a ordem gerada saiu com o valor cheio, sem
   * descontar nada. Como releaseValue já sai líquido de TODO ajuste de débito na geração, o
   * matcher bancário (BankReconciliationService) usa releaseValue diretamente, sem nenhum
   * desconto adicional em memória — descontar de novo aqui contaria a tarifa de POS duas vezes.
   */
  @Query("""
    select coalesce(sum(adj.adjustmentValue), 0)
      from AdjustmentEntity adj
     where adj.salesSummary.id = :summaryId
       and adj.debitType = 'D'
  """)
  BigDecimal sumDebitAdjustmentsBySalesSummaryId(@Param("summaryId") UUID summaryId);

  /** Mesma soma acima, em lote para uma página inteira de resumos (ver CreditOrderManualService#fillNextInstallmentPreview). */
  @Query("""
    select adj.salesSummary.id, sum(adj.adjustmentValue)
      from AdjustmentEntity adj
     where adj.salesSummary.id in :summaryIds
       and adj.debitType = 'D'
     group by adj.salesSummary.id
  """)
  List<Object[]> sumDebitAdjustmentsBySalesSummaryIdIn(@Param("summaryIds") Collection<UUID> summaryIds);
}
