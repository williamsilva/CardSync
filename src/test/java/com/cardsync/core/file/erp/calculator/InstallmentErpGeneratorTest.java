package com.cardsync.core.file.erp.calculator;

import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Achado de auditoria (2026-09-13): gerador central de parcelas ERP (usado na importação do
 * arquivo e na resolução manual ACQUIRER, ver ErpAcquirerResolutionService) não tinha nenhum
 * teste dedicado, apesar de ser onde o rateio de centavos residuais e o cálculo de data prevista
 * de pagamento (contrato x fallback) realmente acontecem.
 */
class InstallmentErpGeneratorTest {

  private final InstallmentErpGenerator generator = new InstallmentErpGenerator();

  @Test
  void splitsValuesEvenlyAcrossInstallmentsWithRemainderOnTheFirstOne() {
    TransactionErpEntity tx = transaction(3, "100.00", "97.50", "2.50", ModalityEnum.CASH_CREDIT, LocalDate.of(2026, 1, 10));

    List<InstallmentErpEntity> installments = generator.generate(tx, 30);

    assertThat(installments).hasSize(3);
    // 100.00 / 3 = 33.33 com resto 0.01 - o resto vai inteiro pra primeira parcela.
    assertThat(installments.get(0).getGrossValue()).isEqualByComparingTo("33.34");
    assertThat(installments.get(1).getGrossValue()).isEqualByComparingTo("33.33");
    assertThat(installments.get(2).getGrossValue()).isEqualByComparingTo("33.33");
    BigDecimal totalGross = installments.stream().map(InstallmentErpEntity::getGrossValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalGross).isEqualByComparingTo("100.00");

    assertThat(installments).extracting(InstallmentErpEntity::getInstallment).containsExactly(1, 2, 3);
    assertThat(installments).allSatisfy(i -> {
      assertThat(i.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PENDING.getCode());
      assertThat(i.getInstallmentStatus()).isEqualTo(StatusInstallmentEnum.SCHEDULED.getCode());
      assertThat(i.getTransaction()).isSameAs(tx);
    });
  }

  @Test
  void treatsNullOrNonPositiveInstallmentAsASingleInstallment() {
    TransactionErpEntity tx = transaction(null, "50.00", "49.00", "1.00", ModalityEnum.CASH_CREDIT, LocalDate.of(2026, 1, 10));

    List<InstallmentErpEntity> installments = generator.generate(tx, 30);

    assertThat(installments).hasSize(1);
    assertThat(installments.get(0).getGrossValue()).isEqualByComparingTo("50.00");
  }

  @Test
  void usesContractedPaymentTermDaysMultipliedByInstallmentNumberWhenAvailable() {
    TransactionErpEntity tx = transaction(3, "300.00", "294.00", "6.00", ModalityEnum.CASH_CREDIT, LocalDate.of(2026, 1, 1));

    List<InstallmentErpEntity> installments = generator.generate(tx, 31);

    assertThat(installments.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 2, 1));  // +31
    assertThat(installments.get(1).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 3, 4));  // +62
    assertThat(installments.get(2).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 4));  // +93
  }

  @Test
  void fallsBackToNextDayForCashDebitWithoutContractedTerm() {
    TransactionErpEntity tx = transaction(1, "50.00", "49.50", "0.50", ModalityEnum.CASH_DEBIT, LocalDate.of(2026, 5, 10));

    List<InstallmentErpEntity> installments = generator.generate(tx, null);

    assertThat(installments.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 11));
  }

  @Test
  void fallsBackToThirtyDaysPerInstallmentWhenNoContractedTermAndNotCashDebit() {
    TransactionErpEntity tx = transaction(2, "200.00", "194.00", "6.00", ModalityEnum.CASH_CREDIT, LocalDate.of(2026, 5, 10));

    List<InstallmentErpEntity> installments = generator.generate(tx, null);

    assertThat(installments.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 9));   // +30
    assertThat(installments.get(1).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 9));   // +60
  }

  @Test
  void treatsZeroContractedPaymentTermDaysAsAValidTermNotAsMissing() {
    // Achado real (auditoria 2026-09-13): 0 é tratado como prazo válido (vence no dia da venda),
    // nunca como "sem contrato" - por isso ContractService agora rejeita 0 na origem (ver
    // ContractServiceTest); este teste apenas documenta o comportamento atual do gerador.
    TransactionErpEntity tx = transaction(1, "10.00", "9.90", "0.10", ModalityEnum.CASH_CREDIT, LocalDate.of(2026, 5, 10));

    List<InstallmentErpEntity> installments = generator.generate(tx, 0);

    assertThat(installments.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 10));
  }

  private TransactionErpEntity transaction(
    Integer installment, String gross, String liquid, String discount, ModalityEnum modality, LocalDate saleDate
  ) {
    TransactionErpEntity tx = new TransactionErpEntity();
    tx.setInstallment(installment);
    tx.setGrossValue(new BigDecimal(gross));
    tx.setLiquidValue(new BigDecimal(liquid));
    tx.setDiscountValue(new BigDecimal(discount));
    tx.setModality(modality.getCode());
    tx.setSaleDate(saleDate.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
    return tx;
  }
}
