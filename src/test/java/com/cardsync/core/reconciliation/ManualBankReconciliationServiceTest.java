package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a possibilidade de vincular manualmente um lançamento bancário a ordens de crédito mesmo
 * quando a soma não bate exato — necessário quando o lançamento mistura vendas anteriores à
 * implantação do sistema (sem CreditOrder correspondente) com vendas atuais, o que nunca fecha
 * por definição. Exige justificativa explícita (nunca silenciosa) e valida de novo aqui, não só
 * no frontend — defesa em profundidade.
 */
class ManualBankReconciliationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);

  private final ManualBankReconciliationService service = new ManualBankReconciliationService(
    releasesBankRepository, creditOrderRepository, salesSummaryRepository, settingsService
  );

  @Test
  void reconcilesWithoutDivergenceWhenSumMatchesExactly() {
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(creditOrderRepository.findPendingZeroValueOrders(any(), any())).thenReturn(List.of());

    UUID releaseId = UUID.randomUUID();
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(releaseId);
    release.setReleaseValue(new BigDecimal("100.00"));
    when(releasesBankRepository.findById(releaseId)).thenReturn(Optional.of(release));

    UUID orderId = UUID.randomUUID();
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(orderId);
    order.setReleaseValue(new BigDecimal("100.00"));
    when(creditOrderRepository.findAllById(List.of(orderId))).thenReturn(List.of(order));

    ManualBankReconciliationResult result = service.reconcile(releaseId, List.of(orderId), null);

    assertThat(result.reconciled()).isEqualTo(1);
    assertThat(result.divergenceValue()).isNull();
    assertThat(release.getDivergenceValue()).isNull();
    assertThat(release.getDivergenceReason()).isNull();
  }

  @Test
  void rejectsDivergenceWithNullReason() {
    assertThatThrownBy(() -> attemptDivergingReconcile(null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsDivergenceWithBlankReason() {
    assertThatThrownBy(() -> attemptDivergingReconcile("   ")).isInstanceOf(BusinessException.class);
  }

  /** Cada chamada usa release/order novos — reconcile() muta order.releaseBank em memória mesmo
   * quando a divergência acaba rejeitada depois, então reusar a mesma instância entre chamadas
   * mascararia a segunda tentativa (a ordem pareceria "já conciliada" e puraria a validação). */
  private void attemptDivergingReconcile(String reason) {
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(creditOrderRepository.findPendingZeroValueOrders(any(), any())).thenReturn(List.of());

    UUID releaseId = UUID.randomUUID();
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(releaseId);
    release.setReleaseValue(new BigDecimal("100.00"));
    when(releasesBankRepository.findById(releaseId)).thenReturn(Optional.of(release));

    UUID orderId = UUID.randomUUID();
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(orderId);
    order.setReleaseValue(new BigDecimal("98.51")); // diferença de 1.49, fora da tolerância
    when(creditOrderRepository.findAllById(List.of(orderId))).thenReturn(List.of(order));

    service.reconcile(releaseId, List.of(orderId), reason);
  }

  @Test
  void acceptsDivergenceWithReasonAndRecordsIt() {
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(creditOrderRepository.findPendingZeroValueOrders(any(), any())).thenReturn(List.of());

    UUID releaseId = UUID.randomUUID();
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(releaseId);
    release.setReleaseValue(new BigDecimal("100.00"));
    when(releasesBankRepository.findById(releaseId)).thenReturn(Optional.of(release));

    UUID orderId = UUID.randomUUID();
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(orderId);
    order.setReleaseValue(new BigDecimal("98.51"));
    when(creditOrderRepository.findAllById(List.of(orderId))).thenReturn(List.of(order));

    ManualBankReconciliationResult result = service.reconcile(
      releaseId, List.of(orderId), "Diferença de vendas anteriores à implantação"
    );

    assertThat(result.reconciled()).isEqualTo(1);
    assertThat(result.divergenceValue()).isEqualByComparingTo(new BigDecimal("1.49"));
    assertThat(release.getDivergenceValue()).isEqualByComparingTo(new BigDecimal("1.49"));
    assertThat(release.getDivergenceReason()).isEqualTo("Diferença de vendas anteriores à implantação");
  }
}
