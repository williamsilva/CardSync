package com.cardsync.core.file.service;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Achado de auditoria (2026-09-13): ProcessRedeEeVcService#buildInstallments não tinha nenhuma
 * cobertura de teste - nem o caminho de extração real (registros "014"/"020", que trazem valor e
 * data de crédito reais do arquivo), nem o fallback de rateio uniforme (quando o arquivo não traz
 * esses registros para o resumo). O fallback é o de maior risco: divide os totais da transação
 * igualmente entre as parcelas, sem nenhum dado real por parcela.
 */
class ProcessRedeEeVcServiceBuildInstallmentsTest {

  private final ProcessRedeEeVcService service = new ProcessRedeEeVcService(
    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
  );

  @Test
  void extractsProportionalValuesAndCreditDateFromLayoutRecordsWhenAvailable() {
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setPvNumber(7867379);
    summary.setRvNumber(60012393);
    summary.setGrossValue(new BigDecimal("200.00"));

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setSalesSummary(summary);
    tx.setGrossValue(new BigDecimal("50.00")); // 25% do resumo

    ProcessRedeEeVcService.EeVcRvInstallment item1 = new ProcessRedeEeVcService.EeVcRvInstallment(
      "014", 1, 7867379, 60012393, LocalDate.of(2026, 3, 1), 1,
      new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"), LocalDate.of(2026, 4, 1)
    );
    ProcessRedeEeVcService.EeVcRvInstallment item2 = new ProcessRedeEeVcService.EeVcRvInstallment(
      "014", 2, 7867379, 60012393, LocalDate.of(2026, 3, 1), 2,
      new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"), LocalDate.of(2026, 5, 1)
    );

    List<InstallmentAcqEntity> installments = service.buildInstallments(List.of(tx), List.of(item1, item2));

    List<InstallmentAcqEntity> sorted = installments.stream()
      .sorted(Comparator.comparing(InstallmentAcqEntity::getInstallment))
      .toList();
    assertThat(sorted).hasSize(2);

    // 25% de cada valor do resumo (proporcional ao gross da transação sobre o gross do resumo).
    assertThat(sorted.get(0).getGrossValue()).isEqualByComparingTo("25.00");
    assertThat(sorted.get(0).getDiscountValue()).isEqualByComparingTo("2.50");
    assertThat(sorted.get(0).getLiquidValue()).isEqualByComparingTo("22.50");
    assertThat(sorted.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(sorted.get(1).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 1));

    // Achado real já documentado no código: registros e-commerce sem desconto/MDR na própria
    // transação recebem o valor de volta a partir da soma das parcelas extraídas.
    assertThat(tx.getDiscountValue()).isEqualByComparingTo("5.00");
    assertThat(tx.getLiquidValue()).isEqualByComparingTo("45.00");
    assertThat(tx.getMdrRate()).isEqualByComparingTo("10.000000");
  }

  @Test
  void fallsBackToEvenSplitWhenNoLayoutRecordMatchesTheSummary() {
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setPvNumber(1051583117);
    summary.setRvNumber(999);
    summary.setFirstInstallmentCreditDate(LocalDate.of(2026, 6, 10));

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setSalesSummary(summary);
    tx.setInstallment(3);
    tx.setGrossValue(new BigDecimal("100.00"));
    tx.setDiscountValue(new BigDecimal("9.00"));
    tx.setLiquidValue(new BigDecimal("91.00"));

    // Nenhum EeVcRvInstallment informado para pv=1051583117/rv=999 - deve cair no fallback.
    List<InstallmentAcqEntity> installments = service.buildInstallments(List.of(tx), List.of());

    assertThat(installments).hasSize(3);
    // Achado (auditoria 2026-09-13, documentado aqui - não corrigido): divide() arredonda cada
    // parcela isoladamente (HALF_UP, sem redistribuir o resto na primeira, diferente do
    // InstallmentErpGenerator do lado ERP) - 100.00/3 = 33.33 x3 = 99.99, um centavo a menos que
    // o total da venda. Impacto financeiro mínimo, mas assimétrico em relação ao lado ERP.
    BigDecimal totalGross = installments.stream().map(InstallmentAcqEntity::getGrossValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalGross).isEqualByComparingTo("99.99");

    List<InstallmentAcqEntity> sorted = installments.stream()
      .sorted(Comparator.comparing(InstallmentAcqEntity::getInstallment))
      .toList();
    assertThat(sorted.get(0).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(sorted.get(1).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(sorted.get(2).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 8, 10));
  }

  @Test
  void fallbackUsesFirstAndOtherInstallmentValuesWhenInformedInsteadOfDividingLiquidValue() {
    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setInstallment(2);
    tx.setGrossValue(new BigDecimal("100.00"));
    tx.setDiscountValue(new BigDecimal("4.00"));
    tx.setLiquidValue(new BigDecimal("96.00"));
    tx.setFirstInstallmentValue(new BigDecimal("50.00"));
    tx.setOtherInstallmentsValue(new BigDecimal("46.00"));

    List<InstallmentAcqEntity> installments = service.buildInstallments(List.of(tx), List.of());

    List<InstallmentAcqEntity> sorted = installments.stream()
      .sorted(Comparator.comparing(InstallmentAcqEntity::getInstallment))
      .toList();
    assertThat(sorted.get(0).getLiquidValue()).isEqualByComparingTo("50.00");
    assertThat(sorted.get(1).getLiquidValue()).isEqualByComparingTo("46.00");
  }

  @Test
  void treatsNullOrNonPositiveInstallmentAsASingleInstallmentInTheFallback() {
    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setInstallment(null);
    tx.setGrossValue(new BigDecimal("70.00"));
    tx.setDiscountValue(new BigDecimal("7.00"));
    tx.setLiquidValue(new BigDecimal("63.00"));

    List<InstallmentAcqEntity> installments = service.buildInstallments(List.of(tx), List.of());

    assertThat(installments).hasSize(1);
    assertThat(installments.get(0).getGrossValue()).isEqualByComparingTo("70.00");
    assertThat(installments.get(0).getLiquidValue()).isEqualByComparingTo("63.00");
  }
}
