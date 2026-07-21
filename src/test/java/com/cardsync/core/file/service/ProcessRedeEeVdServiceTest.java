package com.cardsync.core.file.service;

import com.cardsync.domain.model.AdjustmentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a correção do bug de cancelamento de venda e-commerce nunca avaliado (Etapa 3):
 * o registro "17" do layout Rede EEVD representa o cancelamento integral de uma venda
 * e-commerce, mas antes só preenchia transactionValue — nunca cancellationValueRequested nem
 * adjustmentValue. A query de elegibilidade em AdjustmentRepository
 * (findIdsForAcquirerSaleCancellationReconciliation) exige cancellationValueRequested > 0, então
 * esses ajustes eram descartados antes mesmo de chegar em
 * AcquirerSaleCancellationService.isFullCancellation — a venda ADQ correspondente ficava
 * pendente para sempre.
 */
class ProcessRedeEeVdServiceTest {

  private final ProcessRedeEeVdService service = new ProcessRedeEeVdService(
    null, null, null, null, null, null, null, null, null, null, null, null, null, null
  );

  @Test
  void buildAdjustment17FillsCancellationValueRequestedFromTransactionValue() {
    List<String> columns = List.of(
      "17",           // 0 recordType
      "1234",         // 1 cardNumber
      "10012026",     // 2 transactionDate (ddMMyyyy)
      "555",          // 3 rvOriginal
      "9999",         // 4 pvOriginal
      "05012026",     // 5 rvDateOriginal
      "150.00",       // 6 transactionValue / valor cancelado
      "123456",       // 7 nsu
      "AUTH01",       // 8 authorization
      "TID01",        // 9 tid
      "ORDER01"       // 10 ecommerceOrderNumber
    );

    AdjustmentEntity adjustment = service.buildAdjustment17(columns, 1, null, List.of());

    assertThat(adjustment.getTransactionValue()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(adjustment.getCancellationValueRequested()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(adjustment.getEcommerce()).isTrue();
  }
}
