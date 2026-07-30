package com.cardsync.core.file.bank;

import com.cardsync.core.file.util.FileParserUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a leitura do layout CNAB240 (Segmento E - extrato/conciliação bancária) do Sicredi,
 * adicionado em Cnab240BankLayout seguindo o mesmo padrão de Itaú/Santander/Bradesco.
 *
 * As linhas usadas abaixo são recortes reais de extratos Sicredi (contrato 226/81351-8,
 * banco 748) e confirmam que:
 * - agência/conta/segmento seguem exatamente as mesmas posições do Santander;
 * - o código histórico (172-176) frequentemente traz letras (ex.: "01Y6", "0CX1", "0DV3" em
 *   lançamentos de cartão/PIX/convênio) e por isso não pode ser usado para resolver a
 *   modalidade de pagamento — daí SICREDI usar usesDescriptionForModality=true, como o Itaú.
 */
class Cnab240BankLayoutSicrediTest {

  // Header de arquivo (tipo 0): banco 748, agência 226-7, conta 81351-8, CNPJ 39303847000180.
  private static final String HEADER =
    "74800000         239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA  CC ALIANCA                              229072026093140361043060";

  // Segmento E - venda de cartão (débito), REDE.
  private static final String DETAIL_REDE_DEBITO =
    "7480001300001E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0107202601072026000000000000004936C20501Y6REDE DEBITO MASTER       855845600 |0001-80";

  // Segmento E - venda de cartão (crédito), REDE.
  private static final String DETAIL_REDE_CREDITO =
    "7480001300002E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0107202601072026000000000000307944C20501Z4REDE CREDITO MASTER      855840116 |0001-80";

  // Segmento E - pagamento de boleto (débito); código histórico limpo (0275).
  private static final String DETAIL_LIQUIDACAO_BOLETO =
    "7480001300006E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0107202601072026000000000000165000D1040275LIQUIDACAO BOLETO                  40455397000120 RICIERI LOCACO";

  // Segmento E - débito TED/Internet Banking.
  private static final String DETAIL_DEBITO_TED =
    "7480001300026E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0307202603072026000000000001000000D1200471DEBITO TED/IB            I00636    39303847000180 ACQUAMANIA MUL";

  // Segmento E - recebimento PIX; código histórico alfanumérico (0CX1).
  private static final String DETAIL_RECEBIMENTO_PIX =
    "7480001300027E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0607202606072026000000000000004000C2090CX1RECEBIMENTO PIX          PIX_CRED  10424623722 LUANA ALVES CARVA";

  // Segmento E - débito de convênio/consórcio; código histórico alfanumérico (0DV3).
  private static final String DETAIL_DEBITO_CONVENIOS =
    "7480001300085E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N0807202608072026000000000000324810D1040DV3DEBITO CONVENIOS         CONSORCIO 07808907000120 ADM.CONSORCIO";

  // Segmento E - débito de fatura de cartão de crédito.
  private static final String DETAIL_DEB_CTA_FATURA =
    "7480001300173E   239303847000180                    0022670000000813518 ACQUAMANIA MULTIPLO LAZER SA        DPV0174802267            N2307202623072026000000000004080992D1040347DEB.CTA.FATURA           025801146";

  @Test
  void resolvesSicrediLayoutByBankCode748() {
    // Cnab240FileProcessor/ProcessFileBankService sempre extraem o código do banco das 3
    // primeiras posições do arquivo (range "0-3"), então o valor real nunca vem com zeros à
    // esquerda — fromBankCode só normaliza caracteres não numéricos, não faz padding.
    assertThat(Cnab240BankLayout.fromBankCode("748")).isEqualTo(Cnab240BankLayout.SICREDI);
  }

  @Test
  void supportsOnlySegmentE() {
    assertThat(Cnab240BankLayout.SICREDI.isSupportedDetailSegment("E")).isTrue();
    assertThat(Cnab240BankLayout.SICREDI.isSupportedDetailSegment("A")).isFalse();
    assertThat(Cnab240BankLayout.SICREDI.isSupportedDetailSegment("J")).isFalse();
  }

  @Test
  void usesDescriptionInsteadOfHistoricalCodeForModality() {
    // O código histórico do Sicredi mistura letras (ex.: "01Y6", "0CX1", "0DV3") e não pode ser
    // tratado como um inteiro de modalidade confiável — diferente de Santander/Bradesco.
    assertThat(Cnab240BankLayout.SICREDI.isUsesDescriptionForModality()).isTrue();
  }

  @Test
  void extractsAgencyAndAccountFromHeaderUsingBankLayoutRanges() {
    // As mesmas ranges de agência/conta do layout são usadas tanto no header quanto no detalhe
    // (ver Cnab240FileProcessor.applyHeaderFile/buildRelease) — por isso também são conferidas
    // diretamente na linha de header.
    Cnab240BankLayout layout = Cnab240BankLayout.SICREDI;
    assertThat(FileParserUtils.extractIntegerLine(HEADER, layout.getAgencyRange(), 1)).isEqualTo(226);
    assertThat(FileParserUtils.extractIntegerLine(HEADER, layout.getCurrentAccountRange(), 1)).isEqualTo(81351);
    assertThat(FileParserUtils.extractStringLine(HEADER, layout.getDigitAccountRange(), 1)).isEqualTo("8");
  }

  @Test
  void extractsCardDebitSaleFields() {
    assertField(DETAIL_REDE_DEBITO, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
      new BigDecimal("49.36"), "C", 205, null, "REDE DEBITO MASTER", "855845600 |0001-80");
  }

  @Test
  void extractsCardCreditSaleFields() {
    assertField(DETAIL_REDE_CREDITO, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
      new BigDecimal("3079.44"), "C", 205, null, "REDE CREDITO MASTER", "855840116 |0001-80");
  }

  @Test
  void extractsBoletoPaymentFieldsWithCleanNumericHistoricalCode() {
    assertField(DETAIL_LIQUIDACAO_BOLETO, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
      new BigDecimal("1650.00"), "D", 104, 275, "LIQUIDACAO BOLETO", "40455397000120 RICIERI LOCACO");
  }

  @Test
  void extractsTedDebitFields() {
    assertField(DETAIL_DEBITO_TED, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3),
      new BigDecimal("10000.00"), "D", 120, 471, "DEBITO TED/IB", "I00636    39303847000180 ACQUAMANIA MUL");
  }

  @Test
  void extractsPixReceiptFieldsWithAlphanumericHistoricalCode() {
    assertField(DETAIL_RECEBIMENTO_PIX, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 6),
      new BigDecimal("40.00"), "C", 209, null, "RECEBIMENTO PIX", "PIX_CRED  10424623722 LUANA ALVES CARVA");
  }

  @Test
  void extractsConvenioDebitFieldsWithAlphanumericHistoricalCode() {
    assertField(DETAIL_DEBITO_CONVENIOS, LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 8),
      new BigDecimal("3248.10"), "D", 104, null, "DEBITO CONVENIOS", "CONSORCIO 07808907000120 ADM.CONSORCIO");
  }

  @Test
  void extractsCreditCardInvoiceDebitFields() {
    assertField(DETAIL_DEB_CTA_FATURA, LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 23),
      new BigDecimal("40809.92"), "D", 104, 347, "DEB.CTA.FATURA", "025801146");
  }

  @Test
  void extractsSharedNatureComplementAndCpmfFieldsFromAllDetailLines() {
    for (String detail : new String[]{
      DETAIL_REDE_DEBITO, DETAIL_REDE_CREDITO, DETAIL_LIQUIDACAO_BOLETO, DETAIL_DEBITO_TED,
      DETAIL_RECEBIMENTO_PIX, DETAIL_DEBITO_CONVENIOS, DETAIL_DEB_CTA_FATURA
    }) {
      Cnab240BankLayout layout = Cnab240BankLayout.SICREDI;
      assertThat(FileParserUtils.extractIntegerLine(detail, layout.getAgencyRange(), 1)).isEqualTo(226);
      assertThat(FileParserUtils.extractIntegerLine(detail, layout.getCurrentAccountRange(), 1)).isEqualTo(81351);
      assertThat(FileParserUtils.extractStringLine(detail, layout.getDigitAccountRange(), 1)).isEqualTo("8");
      assertThat(FileParserUtils.extractStringLine(detail, layout.getNatureRange(), 1)).isEqualTo("DPV");
      assertThat(FileParserUtils.extractIntegerLine(detail, layout.getComplementTypeRange(), 1)).isEqualTo(1);
      assertThat(FileParserUtils.extractStringLine(detail, layout.getComplementRange(), 1)).isEqualTo("74802267");
      assertThat(FileParserUtils.extractStringLine(detail, layout.getCpmfRange(), 1)).isEqualTo("N");
    }
  }

  private void assertField(
    String detail,
    LocalDate expectedAccountingDate,
    LocalDate expectedReleaseDate,
    BigDecimal expectedValue,
    String expectedReleaseType,
    Integer expectedReleaseCategory,
    Integer expectedHistoricalCode,
    String expectedDescription,
    String expectedDocument
  ) {
    Cnab240BankLayout layout = Cnab240BankLayout.SICREDI;

    assertThat(FileParserUtils.extractDateLine(detail, layout.getAccountingDateRange(), 1)).isEqualTo(expectedAccountingDate);
    assertThat(FileParserUtils.extractDateLine(detail, layout.getReleaseDateRange(), 1)).isEqualTo(expectedReleaseDate);
    assertThat(FileParserUtils.extractBigDecimalLine(detail, layout.getReleaseValueRange(), 1)).isEqualByComparingTo(expectedValue);
    assertThat(FileParserUtils.extractStringLine(detail, layout.getReleaseTypeRange(), 1)).isEqualTo(expectedReleaseType);
    assertThat(FileParserUtils.extractIntegerLine(detail, layout.getReleaseCategoryRange(), 1)).isEqualTo(expectedReleaseCategory);
    assertThat(FileParserUtils.extractIntegerLine(detail, layout.getHistoricalCodeRange(), 1)).isEqualTo(expectedHistoricalCode);
    assertThat(FileParserUtils.extractStringLine(detail, layout.getDescriptionRange(), 1)).isEqualTo(expectedDescription);
    assertThat(FileParserUtils.extractStringLine(detail, layout.getDocumentRange(), 1)).isEqualTo(expectedDocument);
  }
}
