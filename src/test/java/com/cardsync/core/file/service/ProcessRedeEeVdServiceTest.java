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

  /**
   * Cobre a correção do identificador "011" (Rede EEVD) nunca reconhecido: essas linhas trazem
   * tarifas de POS/maquininha (motivo 28 "AL.POS/PINPAD/TX CON", motivo 20 "POS-INATIV/CONEC/PIN")
   * amarradas à RV de vendas real - mas eram descartadas como identificador não mapeado (o código
   * só reconhecia "11", de 2 dígitos, layout totalmente diferente). O valor da tarifa nunca chegava
   * a nenhum ajuste vinculado à RV, então a soma esperada das CreditOrders do dia nunca batia com
   * o valor líquido do lançamento bancário (que já vem descontado dessa tarifa).
   */
  @Test
  void buildAdjustment011ParsesPosRentalFeeFields() {
    List<String> columns = List.of(
      "011",                          // 0 identifier
      "007867379",                    // 1 pvNumber
      "060012393",                    // 2 rvNumber
      "01032026",                     // 3 adjustmentDate (ddMMyyyy)
      "000000000003718",              // 4 adjustmentValue (centavos)
      "D",                            // 5 debitType
      "28",                           // 6 adjustmentReason
      "AL.POS/PINPAD/TX CON",         // 7 adjustmentDescription
      "0000000000000000", "00000000", "000000000", "", "00000000", "260220", // 8-13 reservado
      "007867379",                    // 14 reservado
      "00000000", "000000000000000", // 15-16 reservado
      "N",                            // 17 net
      "02032026",                     // 18 creditDate
      "000000000000000", "000000000000000", "000000000000", "", "0", // 19-23 reservado
      "26060007084",                  // 24 rawAdjustmentCode
      "000000000000000", "000000000000000", "1", "1", // 25-28 reservado
      "0028",                         // 29 adjustmentReason2
      "00"                            // 30 reservado
    );

    AdjustmentEntity adjustment = service.buildAdjustment011(columns, 1, null, List.of());

    assertThat(adjustment.getPvNumber()).isEqualTo(7867379);
    assertThat(adjustment.getRvNumberOriginal()).isEqualTo(60012393);
    assertThat(adjustment.getAdjustmentValue()).isEqualByComparingTo(new BigDecimal("37.18"));
    assertThat(adjustment.getAdjustmentReason()).isEqualTo(com.cardsync.domain.model.enums.AdjustmentReasonEnum.AL_POS_PINPAD_TX_CONECT);
    assertThat(adjustment.getAdjustmentDescription()).isEqualTo("AL.POS/PINPAD/TX CON");
    assertThat(adjustment.getDebitType()).isEqualTo("D");
    assertThat(adjustment.getCreditDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 2));
  }
}
