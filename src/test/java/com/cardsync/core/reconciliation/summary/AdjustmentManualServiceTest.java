package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.input.AdjustmentManualInput;
import com.cardsync.core.file.service.FileLookupService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a lacuna encontrada com dados reais (RV 82730892, importado do relatório "pagamentos" da
 * adquirente com valor líquido R$9,77, mas com um ajuste de aluguel de maquininha de R$9,77 —
 * valor real devido R$0,00): antes, importar um ajuste manual depois que a ordem de crédito já
 * existia nunca recalculava nada — o desconto só acontecia na hora de gerar a ordem.
 */
class AdjustmentManualServiceTest {

  private final FileLookupService fileLookupService = mock(FileLookupService.class);
  private final AcquirerRepository acquirerRepository = mock(AcquirerRepository.class);
  private final CompanyRepository companyRepository = mock(CompanyRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);

  private final AdjustmentManualService service = new AdjustmentManualService(
    fileLookupService, acquirerRepository, companyRepository, salesSummaryRepository,
    adjustmentRepository, creditOrderRepository
  );

  /**
   * debitType chega já mapeado como "D"/"C" — o frontend (manual-adjustment.component.ts)
   * converte o texto bruto do CSV ("cobrança"→D, "crédito"→C) antes de enviar o JSON;
   * normalizeDebitType no backend só reforça maiúscula/1º caractere, não deriva de texto puro
   * (não daria pra distinguir "cobrança" de "crédito" só pela primeira letra — ambos começam com 'C').
   */
  private AdjustmentManualInput input(UUID acquirerId, Integer rvNumber, Integer pvNumber, String debitType, BigDecimal value) {
    return new AdjustmentManualInput(
      pvNumber, acquirerId.toString(), null, pvNumber, rvNumber,
      LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 1), value,
      null, null, null, debitType, "cobrado nos recebíveis", "aluguel de maquininha", null, "26152275343"
    );
  }

  @Test
  void recomputesPendingUnbankedCreditOrderWhenDebitAdjustmentIsImported() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(acquirerId);

    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setInstallmentTotal(1);
    order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    order.setReleaseValue(new BigDecimal("9.77"));

    when(adjustmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(acquirerRepository.findById(acquirerId)).thenReturn(java.util.Optional.of(acquirer));
    when(salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirerId, 74705318, 82730892))
      .thenReturn(java.util.Optional.of(summary));
    when(creditOrderRepository.findBySalesSummary_Id(summaryId)).thenReturn(List.of(order));

    service.create(input(acquirerId, 82730892, 74705318, "D", new BigDecimal("9.77")));

    var captor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(creditOrderRepository).saveAll(captor.capture());
    List<CreditOrderEntity> saved = captor.getValue();
    assertThat(saved).hasSize(1);
    assertThat(saved.getFirst().getReleaseValue()).isEqualByComparingTo("0.00");
  }

  @Test
  void neverTouchesCreditOrderAlreadyPaidOrBankLinked() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(acquirerId);

    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    CreditOrderEntity paidOrder = new CreditOrderEntity();
    paidOrder.setId(UUID.randomUUID());
    paidOrder.setInstallmentTotal(1);
    paidOrder.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
    paidOrder.setReleaseValue(new BigDecimal("9.77"));

    CreditOrderEntity linkedOrder = new CreditOrderEntity();
    linkedOrder.setId(UUID.randomUUID());
    linkedOrder.setInstallmentTotal(1);
    linkedOrder.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    linkedOrder.setReleaseBank(new ReleasesBankEntity());
    linkedOrder.setReleaseValue(new BigDecimal("9.77"));

    when(adjustmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(acquirerRepository.findById(acquirerId)).thenReturn(java.util.Optional.of(acquirer));
    when(salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirerId, 74705318, 82730892))
      .thenReturn(java.util.Optional.of(summary));
    when(creditOrderRepository.findBySalesSummary_Id(summaryId)).thenReturn(List.of(paidOrder, linkedOrder));

    service.create(input(acquirerId, 82730892, 74705318, "D", new BigDecimal("9.77")));

    verify(creditOrderRepository, never()).saveAll(any());
    assertThat(paidOrder.getReleaseValue()).isEqualByComparingTo("9.77");
    assertThat(linkedOrder.getReleaseValue()).isEqualByComparingTo("9.77");
  }

  /**
   * Confirmado com dados reais: o mesmo CSV foi importado duas vezes (uma vez pra corrigir a RV
   * 82730892 na mão, outra pela tela) — cada importação descontou de novo o mesmo ajuste da
   * ordem de crédito, que ficou com releaseValue negativo (R$9,77 → R$0,00 → -R$9,77). O "ID
   * ajuste" do arquivo da adquirente (rawAdjustmentCode) é a chave natural de idempotência.
   */
  @Test
  void rejectsReimportOfTheSameAdjustmentCode() {
    UUID acquirerId = UUID.randomUUID();

    when(adjustmentRepository.existsByRawAdjustmentCodeAndRvNumberOriginal("26152275343", 82730892))
      .thenReturn(true);

    org.assertj.core.api.Assertions.assertThatThrownBy(
      () -> service.create(input(acquirerId, 82730892, 74705318, "D", new BigDecimal("9.77")))
    ).isInstanceOf(com.cardsync.domain.exception.BusinessException.class);

    verify(adjustmentRepository, never()).save(any());
  }

  @Test
  void neverRecomputesForCreditTypeAdjustment() {
    UUID acquirerId = UUID.randomUUID();
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(acquirerId);

    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);

    when(adjustmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(acquirerRepository.findById(acquirerId)).thenReturn(java.util.Optional.of(acquirer));
    when(salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirerId, 74705318, 82730892))
      .thenReturn(java.util.Optional.of(summary));

    service.create(input(acquirerId, 82730892, 74705318, "C", new BigDecimal("9.77")));

    verify(creditOrderRepository, never()).findBySalesSummary_Id(any());
  }
}
