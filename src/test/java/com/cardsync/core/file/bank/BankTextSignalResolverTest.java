package com.cardsync.core.file.bank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre isCreditSignal/isDebitSignal, referenciados como "ver BankTextSignalResolverTest" em
 * BankStatementModalityReclassificationServiceTest mas ainda sem teste próprio até então.
 *
 * Também cobre a correção do falso positivo descoberto ao importar extratos reais do Sicredi
 * (ver Cnab240BankLayout.SICREDI): "CR"/"CD"/"CRED" como substring livre batiam dentro de
 * qualquer palavra que contivesse essas letras (ex.: a palavra "SICREDI" contém "CRED"; o
 * favorecido "CONCRETOCOM" contém "CR"), classificando débitos de conta (pagamento de boleto,
 * TED, convênio) como se fossem venda em cartão de crédito. A correção exige que essas
 * abreviações apareçam como token isolado ("CRED") ou coladas a dígitos ("CD"/"CR" + número),
 * preservando o caso real documentado (Rede/Santander: "REDE MAST CD0007866470").
 *
 * E a mesma classe de bug em isAmexSignal/isEloSignal (usados para BANDEIRA, não modalidade):
 * "AM" batia dentro de "PAGAMENTO" e do nome da própria empresa "ACQUAMANIA", e "EL" batia
 * dentro de nomes comuns de favorecidos PIX ("MICHELE", "ANGELO", "GISELE", "CELIA",
 * "FELICIANO") — marcando pagamentos/recebimentos PIX como venda em cartão American
 * Express/Elo. Reportado pelo usuário direto na tela de Extrato Bancário.
 */
class BankTextSignalResolverTest {

  private final BankTextSignalResolver resolver = new BankTextSignalResolver();

  @Test
  void doesNotTreatSicrediBankNameAsCreditSignal() {
    String normalized = resolver.normalize("LIQUIDACAO BOLETO SICREDI");

    assertThat(resolver.isCreditSignal(normalized)).isFalse();
    assertThat(resolver.isDebitSignal(normalized)).isFalse();
  }

  @Test
  void doesNotTreatBeneficiaryNameContainingCrAsCreditSignal() {
    // Favorecido real "CONCRETOCOM LTDA" contém "CR" (con-CR-etocom) — não deve ser lido como
    // sinal de cartão de crédito para um pagamento de boleto (débito de conta).
    String normalized = resolver.normalize("LIQUIDACAO BOLETO 09345074000134 CONCRETOCOM LTDA");

    assertThat(resolver.isCreditSignal(normalized)).isFalse();
  }

  @Test
  void stillResolvesCreditFromRealAbbreviatedCdSignal() {
    // Caso real documentado: Rede/Santander abreviam a bandeira para "MAST" (não MASTER) e
    // marcam crédito com "CD" colado ao NSU/código, sem espaço.
    String normalized = resolver.normalize("REDE   MAST CD0007866470");

    assertThat(resolver.isCreditSignal(normalized)).isTrue();
  }

  @Test
  void stillResolvesDebitFromRealAbbreviatedDbSignal() {
    String normalized = resolver.normalize("REDE   ELO  DB0074705318");

    assertThat(resolver.isDebitSignal(normalized)).isTrue();
  }

  @Test
  void resolvesStandaloneCredTokenAsCreditSignal() {
    String normalized = resolver.normalize("PGTO CRED 12345");

    assertThat(resolver.isCreditSignal(normalized)).isTrue();
  }

  @Test
  void stillResolvesExplicitCreditoAndDebitoWords() {
    assertThat(resolver.isCreditSignal(resolver.normalize("REDE CREDITO MASTER"))).isTrue();
    assertThat(resolver.isDebitSignal(resolver.normalize("REDE DEBITO MASTER"))).isTrue();
    assertThat(resolver.isDebitSignal(resolver.normalize("SICREDI DEBITO VISA"))).isTrue();
  }

  @Test
  void stillResolvesBrandSignalsForCreditWithoutExplicitWord() {
    assertThat(resolver.isCreditSignal(resolver.normalize("867379REDE-VISA CRED"))).isTrue();
    assertThat(resolver.isCreditSignal(resolver.normalize("SICREDI CREDITO AMEX"))).isTrue();
  }

  @Test
  void doesNotTreatPagamentoWordOrOwnCompanyNameAsAmexSignal() {
    // "PAGAMENTO" contém "AM" (pag-AM-ento) e "ACQUAMANIA" (nome da empresa dona da conta)
    // também contém "AM" (acqu-AM-ania) — nenhum dos dois é sinal de bandeira Amex.
    assertThat(resolver.isAmexSignal(resolver.normalize("PAGAMENTO PIX"))).isFalse();
    assertThat(resolver.isAmexSignal(resolver.normalize(
      "PAGAMENTO PIX PIX_DEB 39303847000180 ACQUAMANIA MULTIPLO LAZER SA"))).isFalse();
  }

  @Test
  void doesNotTreatCommonNamesContainingElAsEloSignal() {
    assertThat(resolver.isEloSignal(resolver.normalize("PIX_CRED 11502481731 MICHELE SALMAR"))).isFalse();
    assertThat(resolver.isEloSignal(resolver.normalize("PIX_CRED 14917989710 ANGELO RODRIGUES"))).isFalse();
    assertThat(resolver.isEloSignal(resolver.normalize("PIX_CRED 34058553812 CELIA SANTOS"))).isFalse();
  }

  @Test
  void stillResolvesAmexAndEloFromRealBrandWords() {
    assertThat(resolver.isAmexSignal(resolver.normalize("SICREDI CREDITO AMEX"))).isTrue();
    assertThat(resolver.isEloSignal(resolver.normalize("SICREDI DEBITO ELO"))).isTrue();
    assertThat(resolver.isEloSignal(resolver.normalize("REDE   ELO  DB0074705318"))).isTrue();
  }

  @Test
  void resolvesPixReceiptSignalFromRealSicrediMarker() {
    String normalized = resolver.normalize("RECEBIMENTO PIX PIX_CRED 10424623722 LUANA ALVES CARVA");

    assertThat(resolver.isPixReceiptSignal(normalized)).isTrue();
    assertThat(resolver.isPixSentSignal(normalized)).isFalse();
  }

  @Test
  void resolvesPixSentSignalFromRealSicrediMarker() {
    String normalized = resolver.normalize("PAGAMENTO PIX PIX_DEB 39303847000180 ACQUAMANIA MULTIPLO LAZER SA");

    assertThat(resolver.isPixSentSignal(normalized)).isTrue();
    assertThat(resolver.isPixReceiptSignal(normalized)).isFalse();
  }

  @Test
  void doesNotTreatNonPixTextAsPixSignalEvenWithRecebimentoOrPagamento() {
    // "RECEBIMENTO"/"PAGAMENTO" sozinhos (sem "PIX") não devem virar PIX_REC/PIX_ENV — outras
    // modalidades (TED_REC, boleto, etc.) têm código próprio e não devem ser confundidas com PIX.
    assertThat(resolver.isPixReceiptSignal(resolver.normalize("RECEBIMENTO TED"))).isFalse();
    assertThat(resolver.isPixSentSignal(resolver.normalize("PAGAMENTO BOLETO"))).isFalse();
  }

  @Test
  void returnsFalseForTextWithoutAnySignal() {
    String normalized = resolver.normalize("TED RECEBIDA");

    assertThat(resolver.isCreditSignal(normalized)).isFalse();
    assertThat(resolver.isDebitSignal(normalized)).isFalse();
  }
}
