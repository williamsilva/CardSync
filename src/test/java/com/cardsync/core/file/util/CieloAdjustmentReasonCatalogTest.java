package com.cardsync.core.file.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CieloAdjustmentReasonCatalogTest {

  @Test
  void resolvesKnownCodesFromRealExampleLines() {
    // Códigos confirmados nas 4 linhas reais usadas em ProcessCielo03ServiceTest/ProcessCielo04ServiceTest.
    assertThat(CieloAdjustmentReasonCatalog.get("0251"))
      .isEqualTo("Cobrança/devolução de multa da bandeira por excesso de retentativas de venda no mesmo cartão");
    assertThat(CieloAdjustmentReasonCatalog.get("0177"))
      .isEqualTo("Transferência de valores entre estabelecimentos da mesma raiz de CNPJ para compensação de saldo");
    assertThat(CieloAdjustmentReasonCatalog.get("0301"))
      .isEqualTo("Venda contestada pelo banco a pedido do portador do cartão");
  }

  @Test
  void trimsWhitespaceBeforeLookup() {
    assertThat(CieloAdjustmentReasonCatalog.get(" 0301 ")).isEqualTo("Venda contestada pelo banco a pedido do portador do cartão");
  }

  @Test
  void returnsNullForUnknownOrBlankCodeWithoutThrowing() {
    assertThat(CieloAdjustmentReasonCatalog.get("9999")).isNull();
    assertThat(CieloAdjustmentReasonCatalog.get("")).isNull();
    assertThat(CieloAdjustmentReasonCatalog.get("    ")).isNull();
    assertThat(CieloAdjustmentReasonCatalog.get(null)).isNull();
  }
}
