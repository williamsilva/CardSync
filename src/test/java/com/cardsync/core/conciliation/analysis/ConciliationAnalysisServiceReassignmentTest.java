package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.TransactionErpRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug de reatribuição entre lotes: com reprocessErpAcquirerSales=true, o
 * filtro "ainda pendente" é neutralizado nas queries de candidatas, então uma venda ADQ já
 * vinculada a ALGUM ERP volta a aparecer como candidata para outros ERPs. Sem
 * excludeAcquirerSalesClaimedOutsideBatch, um ERP de um lote posterior podia roubar essa venda
 * de um ERP de um lote anterior já commitado, violando a constraint única
 * uq_cs_transaction_erp_transaction_acq e derrubando a etapa inteira.
 */
class ConciliationAnalysisServiceReassignmentTest {

  private final TransactionErpRepository transactionErpRepository = mock(TransactionErpRepository.class);

  private final ConciliationAnalysisService service = new ConciliationAnalysisService(
    null, transactionErpRepository, null, null, null, null, null, null, null, null
  );

  @Test
  void keepsAllCandidatesWhenNotReprocessing() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());

    List<TransactionAcqEntity> result = service.excludeAcquirerSalesClaimedOutsideBatch(
      List.of(acq), List.of(), false
    );

    assertThat(result).containsExactly(acq);
    verify(transactionErpRepository, never()).findByTransactionAcqIdIn(any());
  }

  @Test
  void excludesCandidateAlreadyClaimedByErpOutsideTheBatch() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    TransactionErpEntity erpInBatch = withId(new TransactionErpEntity());
    TransactionErpEntity erpFromEarlierBatch = withId(new TransactionErpEntity());
    erpFromEarlierBatch.setTransactionAcq(acq);

    when(transactionErpRepository.findByTransactionAcqIdIn(any())).thenReturn(List.of(erpFromEarlierBatch));

    List<TransactionAcqEntity> result = service.excludeAcquirerSalesClaimedOutsideBatch(
      List.of(acq), List.of(erpInBatch), true
    );

    assertThat(result).isEmpty();
  }

  @Test
  void keepsCandidateWhenAlreadyClaimedByAnErpInTheSameBatch() {
    // Reavaliar o PAR já vinculado (mesmo ERP deste lote) é o comportamento pretendido de
    // "reprocessar" — não deve ser excluído.
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    TransactionErpEntity erpInBatch = withId(new TransactionErpEntity());
    erpInBatch.setTransactionAcq(acq);

    when(transactionErpRepository.findByTransactionAcqIdIn(any())).thenReturn(List.of(erpInBatch));

    List<TransactionAcqEntity> result = service.excludeAcquirerSalesClaimedOutsideBatch(
      List.of(acq), List.of(erpInBatch), true
    );

    assertThat(result).containsExactly(acq);
  }

  @Test
  void keepsCandidateWithNoExistingErpLink() {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    TransactionErpEntity erpInBatch = withId(new TransactionErpEntity());

    when(transactionErpRepository.findByTransactionAcqIdIn(any())).thenReturn(List.of());

    List<TransactionAcqEntity> result = service.excludeAcquirerSalesClaimedOutsideBatch(
      List.of(acq), List.of(erpInBatch), true
    );

    assertThat(result).containsExactly(acq);
  }

  private <T extends com.cardsync.domain.model.AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
