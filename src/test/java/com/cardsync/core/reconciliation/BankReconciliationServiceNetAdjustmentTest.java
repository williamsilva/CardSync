package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug de conciliação bancária nunca fechando quando há tarifa de POS/pinpad
 * (Rede EEVD identificador "011", ver ProcessRedeEeVdService.buildAdjustment011): o valor do
 * release bancário já vem líquido da tarifa, mas CreditOrderEntity.releaseValue continua bruto —
 * sem descontar a tarifa antes do match, nenhum subconjunto de valores brutos bate com o valor do
 * release, e o lançamento fica PENDING para sempre mesmo com o ajuste corretamente vinculado à RV.
 */
class BankReconciliationServiceNetAdjustmentTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final InstallmentAcqRepository installmentAcqRepository = mock(InstallmentAcqRepository.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);
  private final BankReconciliationMatcher matcher = new BankReconciliationMatcher();

  private final BankReconciliationService service = new BankReconciliationService(
    entityManager,
    matcher,
    null,
    creditOrderRepository,
    releasesBankRepository,
    installmentAcqRepository,
    null,
    null,
    settingsService,
    adjustmentRepository
  );

  @Test
  void releaseNetOfPosFeeMatchesCreditOrderOnceFeeIsDiscounted() {
    when(settingsService.isReprocessBankAcquirer()).thenReturn(false);
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(settingsService.getDateToleranceDaysBefore()).thenReturn(0);
    when(settingsService.getDateToleranceDaysAfter()).thenReturn(0);

    CompanyEntity company = withId(new CompanyEntity());
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    BankEntity bank = withId(new BankEntity());
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setBank(bank);

    LocalDate releaseDate = LocalDate.of(2026, 3, 2);

    SalesSummaryEntity summary = new SalesSummaryEntity();
    UUID summaryId = UUID.randomUUID();
    summary.setId(summaryId);

    // RV bruta = 13421,29; tarifa de POS (motivos 20/28) = 299,42; depósito líquido = 13121,87.
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(bank);
    release.setReleaseDate(releaseDate);
    release.setReleaseValue(new BigDecimal("13121.87"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setCompany(company);
    order.setAcquirer(acquirer);
    order.setBankingDomicile(domicile);
    order.setReleaseDate(releaseDate);
    order.setReleaseValue(new BigDecimal("13421.29"));
    order.setSalesSummary(summary);

    when(adjustmentRepository.sumPosFeeBySalesSummaryAndCreditDate(Set.of(summaryId)))
      .thenReturn(List.<Object[]>of(new Object[] { summaryId, releaseDate, new BigDecimal("299.42") }));

    var reconciledOrderIds = new HashSet<UUID>();
    var counter = BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    service.reconcileEligibleCreditOrders(
      List.of(order),
      List.of(release),
      reconciledOrderIds,
      new HashSet<>(),
      new FileProcessingProperties.Reconciliation(),
      ReconciliationMatchContext.MatchStrictness.NONE,
      counter
    );

    assertThat(reconciledOrderIds).containsExactly(order.getId());
    assertThat(order.getReleaseBank()).isSameAs(release);
    assertThat(order.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PAID);
    assertThat(release.getReconciliationStatus()).isEqualTo(StatusPaymentBankEnum.PAID);
    assertThat(release.getNumberCreditOrders()).isEqualTo(1);
  }

  @Test
  void releaseStaysPendingWhenCreditOrderValueIsNotNettedOfFee() {
    when(settingsService.isReprocessBankAcquirer()).thenReturn(false);
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(settingsService.getDateToleranceDaysBefore()).thenReturn(0);
    when(settingsService.getDateToleranceDaysAfter()).thenReturn(0);

    CompanyEntity company = withId(new CompanyEntity());
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    BankEntity bank = withId(new BankEntity());
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setBank(bank);

    LocalDate releaseDate = LocalDate.of(2026, 3, 2);

    // Sem nenhum ajuste POS_FEE vinculado (adjustmentRepository não é sequer chamado, pois a
    // ordem não tem salesSummary) — o release líquido nunca bate com o valor bruto da ordem.
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(bank);
    release.setReleaseDate(releaseDate);
    release.setReleaseValue(new BigDecimal("13121.87"));

    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setCompany(company);
    order.setAcquirer(acquirer);
    order.setBankingDomicile(domicile);
    order.setReleaseDate(releaseDate);
    order.setReleaseValue(new BigDecimal("13421.29"));

    var reconciledOrderIds = new HashSet<UUID>();
    var counter = BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    service.reconcileEligibleCreditOrders(
      List.of(order),
      List.of(release),
      reconciledOrderIds,
      new HashSet<>(),
      new FileProcessingProperties.Reconciliation(),
      ReconciliationMatchContext.MatchStrictness.NONE,
      counter
    );

    assertThat(reconciledOrderIds).isEmpty();
    assertThat(order.getReleaseBank()).isNull();
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
