package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug "corrige só a primeira parcela faltante por chamada": antes, um
 * resumo com múltiplas parcelas ausentes exigia repetir a ação manual uma vez por parcela
 * (o loop usava break na primeira lacuna encontrada). Agora todas as lacunas são fechadas
 * numa única chamada.
 */
class CreditOrderManualServiceTest {

  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);

  private final CreditOrderManualService service = new CreditOrderManualService(
    null, creditOrderRepository, salesSummaryRepository, transactionAcqRepository, null, null
  );

  @Test
  void createsAllMissingInstallmentsInASingleCall() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvDate(LocalDate.now().minusMonths(6));

    when(salesSummaryRepository.findById(summaryId)).thenReturn(java.util.Optional.of(summary));
    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryId(summaryId)).thenReturn(3);
    // Só a parcela 1 existe — faltam 2 e 3.
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryId(summaryId)).thenReturn(Set.of(1));
    when(creditOrderRepository.save(any(CreditOrderEntity.class)))
      .thenAnswer(invocation -> {
        CreditOrderEntity co = invocation.getArgument(0);
        co.setId(UUID.randomUUID());
        return co;
      });

    CreditOrderManualResult result = service.create(new CreditOrderManualInput(List.of(summaryId)));

    assertThat(result.created()).isEqualTo(2);
    assertThat(result.createdIds()).hasSize(2);
    assertThat(result.skippedReasons()).isEmpty();
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }
}
