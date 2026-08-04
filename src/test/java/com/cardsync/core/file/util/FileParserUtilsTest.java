package com.cardsync.core.file.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre extractSignedMoneyLine, adicionado para o layout Cielo Extrato Eletrônico: diferente do
 * Rede (sinal embutido ou sempre positivo), a Cielo separa o sinal num campo de 1 caractere
 * imediatamente antes dos dígitos do valor (ex.: "+0000000024580"), que extractBigDecimalLine
 * não reconhece corretamente (a regex de parseMoneyInCents só aceita "-" embutido, não "+"
 * separado).
 */
class FileParserUtilsTest {

  @Test
  void extractsPositiveSignedMoney() {
    String line = "+0000000024580";
    assertThat(FileParserUtils.extractSignedMoneyLine(line, "0-14", 1)).isEqualByComparingTo(new BigDecimal("245.80"));
  }

  @Test
  void extractsNegativeSignedMoney() {
    String line = "-0000000000735";
    assertThat(FileParserUtils.extractSignedMoneyLine(line, "0-14", 1)).isEqualByComparingTo(new BigDecimal("-7.35"));
  }

  @Test
  void returnsZeroForAllZeroDigits() {
    String line = "+0000000000000";
    assertThat(FileParserUtils.extractSignedMoneyLine(line, "0-14", 1)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void returnsZeroForBlankRange() {
    String line = "              ";
    assertThat(FileParserUtils.extractSignedMoneyLine(line, "0-14", 1)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void extractsSignedMoneyEmbeddedInALongerLine() {
    // Mesmo padrão real do Registro E da Cielo: sinal + valor bruto da venda cercado de outros campos.
    String line = "XXXXXXXXXX+0000000017170YYYYYYYYYY";
    assertThat(FileParserUtils.extractSignedMoneyLine(line, "10-24", 1)).isEqualByComparingTo(new BigDecimal("171.70"));
  }

  @Test
  void stillExtractsUnsignedMoneyTheOldWay() {
    // Regressão: extractBigDecimalLine (Rede/CNAB240, sem campo de sinal separado) continua igual.
    assertThat(FileParserUtils.extractBigDecimalLine("0000012345", "0-10", 1)).isEqualByComparingTo(new BigDecimal("123.45"));
  }

  @Test
  void deriveConciliationKeyIsDeterministic() {
    String chaveUR = "360338010001092026-08-0300100072000021051583117360338010001090000000000000000000";
    assertThat(FileParserUtils.deriveConciliationKey(chaveUR)).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR));
  }

  @Test
  void deriveConciliationKeyIsAlwaysNonNegative() {
    for (String value : new String[]{"a", "abc", "360338010001092026-08-03", "!@#$%^&*()"}) {
      assertThat(FileParserUtils.deriveConciliationKey(value)).isNotNull().isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  void deriveConciliationKeyDiffersForDifferentValues() {
    assertThat(FileParserUtils.deriveConciliationKey("chave-ur-1"))
      .isNotEqualTo(FileParserUtils.deriveConciliationKey("chave-ur-2"));
  }

  @Test
  void deriveConciliationKeyIgnoresSurroundingWhitespace() {
    assertThat(FileParserUtils.deriveConciliationKey("  abc  ")).isEqualTo(FileParserUtils.deriveConciliationKey("abc"));
  }

  @Test
  void deriveConciliationKeyReturnsNullForBlank() {
    assertThat(FileParserUtils.deriveConciliationKey(null)).isNull();
    assertThat(FileParserUtils.deriveConciliationKey("   ")).isNull();
  }
}
