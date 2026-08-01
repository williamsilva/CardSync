package com.cardsync.core.reconciliation.summary;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a correção do bug de antecipações (Rede EEFI "036" — ProcessRedeEeFiService.
 * buildAnticipation) nunca gerando CreditOrder: o valor antecipado cai na conta, mas
 * AnticipationEntity nunca virava CreditOrderEntity, então nunca participava da conciliação
 * bancária (Etapa 6) — o campo generatedOrders existia na entidade mas nunca era lido/escrito em
 * lugar nenhum. Cobre também que a ordem sintética preserva installmentNumber (parcela
 * específica antecipada), essencial já que uma mesma RV pode ter parcelas antecipadas e outras
 * liquidadas normalmente ao mesmo tempo — não são a mesma ordem.
 */
class SalesSummaryCreditOrderReconciliationServiceTest {

  private final SalesSummaryCreditOrderReconciliationService service =
    new SalesSummaryCreditOrderReconciliationService(null, null, null, null, null, null);

  @Test
  void generateSyntheticCreditOrderFromAnticipationCopiesReleaseValueAndInstallment() {
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    FlagEntity flag = withId(new FlagEntity());
    CompanyEntity company = withId(new CompanyEntity());
    BankingDomicileEntity domicile = withId(new BankingDomicileEntity());
    SalesSummaryEntity summary = withId(new SalesSummaryEntity());

    AnticipationEntity anticipation = new AnticipationEntity();
    anticipation.setAcquirer(acquirer);
    anticipation.setFlag(flag);
    anticipation.setCompany(company);
    anticipation.setBankingDomicile(domicile);
    anticipation.setSalesSummary(summary);
    anticipation.setPvNumber(7867379);
    anticipation.setNumberRvCorresponding(57931819);
    anticipation.setDateRvCorresponding(LocalDate.of(2025, 10, 16));
    anticipation.setReleaseDate(LocalDate.of(2025, 10, 23));
    anticipation.setInstallmentNumber(2);
    anticipation.setInstallmentNumberMax(3);
    anticipation.setGrossValue(new BigDecimal("7.00"));
    anticipation.setDiscountRateValue(new BigDecimal("0.24"));
    anticipation.setReleaseValue(new BigDecimal("6.63"));

    CreditOrderEntity order = service.generateSyntheticCreditOrder(anticipation);

    assertThat(order.getSalesSummary()).isSameAs(summary);
    assertThat(order.getAcquirer()).isSameAs(acquirer);
    assertThat(order.getFlag()).isSameAs(flag);
    assertThat(order.getCompany()).isSameAs(company);
    assertThat(order.getBankingDomicile()).isSameAs(domicile);
    assertThat(order.getRvNumber()).isEqualTo(57931819);
    assertThat(order.getRvDate()).isEqualTo(LocalDate.of(2025, 10, 16));
    assertThat(order.getReleaseDate()).isEqualTo(LocalDate.of(2025, 10, 23));
    assertThat(order.getOriginalPvNumber()).isEqualTo(7867379);
    assertThat(order.getPvCentralizer()).isEqualTo(7867379);
    assertThat(order.getInstallmentNumber()).isEqualTo(2);
    assertThat(order.getInstallmentTotal()).isEqualTo(3);
    assertThat(order.getReleaseValue()).isEqualByComparingTo(new BigDecimal("6.63"));
    assertThat(order.getGrossRvValue()).isEqualByComparingTo(new BigDecimal("7.00"));
    assertThat(order.getDiscountRateValue()).isEqualByComparingTo(new BigDecimal("0.24"));
    assertThat(order.getRecordType()).isEqualTo("GEN_ANTICIPATION");
    assertThat(order.getLaunchType()).isEqualTo("GENERATED_FROM_ANTICIPATION");
    // cs_credit_order.record_type é varchar(20) e launch_type é varchar(30) — estourar
    // qualquer um deles derruba o insert em lote inteiro (visto em produção).
    assertThat(order.getRecordType()).hasSizeLessThanOrEqualTo(20);
    assertThat(order.getLaunchType()).hasSizeLessThanOrEqualTo(30);
    assertThat(order.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PENDING);
    assertThat(order.getSalesSummaryStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }

  /**
   * Caso real reportado direto na tela: RV 338015830, modalidade Débito, liquidValue=52,29, com 2
   * ajustes de débito vinculados (tarifa de POS + cancelamento de venda débito) somando
   * exatamente 52,29 — o valor real devido é R$0,00, mas a ordem sintética gerada saía com o
   * valor cheio (firstPositive ignora adjustedValue=0 e cai pro liquidValue), sem descontar nada.
   */
  @Test
  void generateSyntheticCreditOrderFromSalesSummaryDeductsDebitAdjustments() {
    SalesSummaryEntity summary = withId(new SalesSummaryEntity());
    summary.setRvNumber(338015830);
    summary.setRvDate(LocalDate.of(2025, 12, 4));
    summary.setLiquidValue(new BigDecimal("52.29"));
    summary.setGrossValue(new BigDecimal("52.80"));
    summary.setDiscountValue(new BigDecimal("0.51"));
    summary.setAdjustedValue(BigDecimal.ZERO);

    CreditOrderEntity order = service.generateSyntheticCreditOrder(summary, new BigDecimal("52.29"));

    assertThat(order.getReleaseValue()).isEqualByComparingTo("0.00");
  }

  /** Sem ajustes vinculados (caso comum), o comportamento não muda. */
  @Test
  void generateSyntheticCreditOrderFromSalesSummaryKeepsFullValueWithoutDebitAdjustments() {
    SalesSummaryEntity summary = withId(new SalesSummaryEntity());
    summary.setLiquidValue(new BigDecimal("100.00"));
    summary.setAdjustedValue(BigDecimal.ZERO);

    CreditOrderEntity order = service.generateSyntheticCreditOrder(summary, BigDecimal.ZERO);

    assertThat(order.getReleaseValue()).isEqualByComparingTo("100.00");
  }

  /** debitAdjustments nulo (nenhum ajuste encontrado no lote) não pode derrubar com NPE. */
  @Test
  void generateSyntheticCreditOrderFromSalesSummaryToleratesNullDebitAdjustments() {
    SalesSummaryEntity summary = withId(new SalesSummaryEntity());
    summary.setLiquidValue(new BigDecimal("100.00"));
    summary.setAdjustedValue(BigDecimal.ZERO);

    CreditOrderEntity order = service.generateSyntheticCreditOrder(summary, null);

    assertThat(order.getReleaseValue()).isEqualByComparingTo("100.00");
  }

  private <T extends com.cardsync.domain.model.AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
