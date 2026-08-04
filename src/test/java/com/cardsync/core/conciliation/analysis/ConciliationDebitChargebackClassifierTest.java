package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.enums.ChargebackEventSourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o fallback de texto livre (hasSaleChargebackTerms/type) contra o vocabulário REAL da
 * Cielo (Tabela IX/Tabela II do manual, ver ProcessCielo03Service#buildAdjustment) — nenhum dos
 * códigos numéricos da Cielo é reconhecido pelas tabelas de motivo do Rede (ChargebackReasonCode),
 * então a classificação cai inteiramente nesse fallback pra Cielo.
 */
class ConciliationDebitChargebackClassifierTest {

  private final ConciliationDebitChargebackClassifier classifier = new ConciliationDebitChargebackClassifier();

  @Test
  void classifiesRealCieloChargebackDescriptionAsChargeback() {
    // Código de ajuste "0301" (Tabela IX) — confirmado no exemplo real do código de lançamento "08".
    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setRecordType("08");
    adjustment.setAdjustmentDescription("Venda contestada pelo banco a pedido do portador do cartão");

    assertThat(classifier.isChargeback(adjustment)).isTrue();
    assertThat(classifier.type(adjustment)).isEqualTo(ChargebackEventSourceType.CHARGEBACK_ADJUSTMENT);
  }

  @Test
  void classifiesTabelaIIFallbackDescriptionAsChargebackWhenAdjustmentCodeIsBlank() {
    // "Contestação do portador do cartão" é o texto fixo usado quando o Código de ajuste (Tabela
    // IX) vem em branco (ver ProcessCielo03Service.resolveAdjustmentDescription).
    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setRecordType("08");
    adjustment.setAdjustmentDescription("Contestação do portador do cartão");

    assertThat(classifier.isChargeback(adjustment)).isTrue();
  }

  @Test
  void doesNotClassifyMachineRentalAsChargeback() {
    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setRecordType("10");
    adjustment.setAdjustmentDescription("Aluguel de máquina");

    assertThat(classifier.isChargeback(adjustment)).isFalse();
    assertThat(classifier.type(adjustment)).isEqualTo(ChargebackEventSourceType.ADJUSTMENT);
  }
}
