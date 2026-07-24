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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o isolamento por lançamento em {@code reconcileEligibleCreditOrders}: antes, uma
 * exceção inesperada num único ReleasesBankEntity (ex.: o NPE de modality_payment_bank nulo,
 * já corrigido em contextOf) derrubava reconcilePending() inteiro — uma única @Transactional
 * sem isolamento por lote — desfazendo até os matches legítimos já processados no mesmo run.
 * Aqui a falha é simulada via um matcher mockado (independente da causa raiz específica já
 * corrigida) para provar que o mecanismo de isolamento em si funciona para qualquer exceção.
 */
class BankReconciliationServiceResilienceTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final InstallmentAcqRepository installmentAcqRepository = mock(InstallmentAcqRepository.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final BankReconciliationMatcher matcher = mock(BankReconciliationMatcher.class);

  private final AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);

  private final BankReconciliationService service = new BankReconciliationService(
    entityManager,
    matcher,
    null, // properties — config é passado por parâmetro, não usado via campo aqui
    creditOrderRepository,
    releasesBankRepository,
    installmentAcqRepository,
    null, // transactionErpRepository — não usado neste caminho
    null, // transactionAcqRepository — não usado neste caminho
    settingsService,
    adjustmentRepository
  );

  @Test
  void oneReleaseThrowingDoesNotBlockTheOthersInTheSameRun() {
    when(settingsService.isReprocessBankAcquirer()).thenReturn(false);
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(settingsService.getDateToleranceDaysBefore()).thenReturn(0);
    when(settingsService.getDateToleranceDaysAfter()).thenReturn(0);

    CompanyEntity company = withId(new CompanyEntity());
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    BankEntity bank = withId(new BankEntity());
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setBank(bank);

    LocalDate dateOk = LocalDate.of(2026, 1, 10);
    LocalDate dateFails = LocalDate.of(2026, 1, 20);

    ReleasesBankEntity releaseOk = new ReleasesBankEntity();
    releaseOk.setId(UUID.randomUUID());
    releaseOk.setCompany(company);
    releaseOk.setAcquirer(acquirer);
    releaseOk.setBank(bank);
    releaseOk.setReleaseDate(dateOk);
    releaseOk.setReleaseValue(new BigDecimal("100.00"));
    // modalityPaymentBank propositalmente não setado — getModalityPaymentBank() retorna null,
    // exercitando de brinde o null-safety de contextOf(ReleasesBankEntity) já corrigido.

    ReleasesBankEntity releaseFails = new ReleasesBankEntity();
    releaseFails.setId(UUID.randomUUID());
    releaseFails.setCompany(company);
    releaseFails.setAcquirer(acquirer);
    releaseFails.setBank(bank);
    releaseFails.setReleaseDate(dateFails);
    releaseFails.setReleaseValue(new BigDecimal("200.00"));

    CreditOrderEntity orderOk = new CreditOrderEntity();
    orderOk.setId(UUID.randomUUID());
    orderOk.setCompany(company);
    orderOk.setAcquirer(acquirer);
    orderOk.setBankingDomicile(domicile);
    orderOk.setReleaseDate(dateOk);
    orderOk.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity orderFails = new CreditOrderEntity();
    orderFails.setId(UUID.randomUUID());
    orderFails.setCompany(company);
    orderFails.setAcquirer(acquirer);
    orderFails.setBankingDomicile(domicile);
    orderFails.setReleaseDate(dateFails);
    orderFails.setReleaseValue(new BigDecimal("200.00"));

    when(matcher.selectByValue(any(), any(), eq(new BigDecimal("100.00")), any(), anyLong(), anyLong()))
      .thenReturn(BankReconciliationMatcher.MatchResult.matched(List.of(orderOk), new BigDecimal("100.00"), false));
    when(matcher.selectByValue(any(), any(), eq(new BigDecimal("200.00")), any(), anyLong(), anyLong()))
      .thenThrow(new RuntimeException("falha simulada — não deve derrubar os demais lançamentos"));

    var reconciledOrderIds = new HashSet<UUID>();
    var counter = BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    service.reconcileEligibleCreditOrders(
      List.of(orderOk, orderFails),
      List.of(releaseOk, releaseFails),
      reconciledOrderIds,
      new HashSet<>(),
      new FileProcessingProperties.Reconciliation(),
      ReconciliationMatchContext.MatchStrictness.NONE,
      counter
    );

    BankReconciliationResult result = counter.toResult();
    assertThat(result.getReleasesReconciled()).isEqualTo(1);
    assertThat(result.getCreditOrdersReconciled()).isEqualTo(1);
    assertThat(reconciledOrderIds).containsExactly(orderOk.getId());
    verify(entityManager).clear();
  }

  @Test
  void markReleaseNotReconciledWhenExpiredUsesNotPaidInsteadOfPaid() {
    // NOT_PAID (BankReconciliationStatus.NOT_RECONCILED) — antes usava PAID, o mesmo status de
    // match real, mascarando um lançamento sem nenhuma ordem/parcela vinculada como conciliado.
    when(settingsService.getBankMarkNotReconciledAfterDays()).thenReturn(0);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setReleaseDate(LocalDate.now().minusDays(10));
    release.setReconciliationStatus(StatusPaymentBankEnum.PENDING);

    var counter = BankReconciliationResult.counter(BankReconciliationTriggerType.MANUAL, BankReconciliationMode.CREDIT_ORDER_ONLY);

    service.markReleaseNotReconciledWhenExpired(
      release, new FileProcessingProperties.Reconciliation(), "teste", counter
    );

    assertThat(release.getReconciliationStatus()).isEqualTo(StatusPaymentBankEnum.NOT_PAID);
    verify(releasesBankRepository).save(release);
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
