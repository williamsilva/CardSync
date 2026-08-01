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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o matching bancário quando a RV tem tarifa de POS (Rede EEVD identificador "011", ver
 * ProcessRedeEeVdService.buildAdjustment011) vinculada como ajuste de débito. Havia aqui uma
 * compensação em memória (computeNetCreditOrderValues) que descontava a tarifa do releaseValue
 * só para fins de matching, sem nunca persistir o desconto — porque, até então,
 * CreditOrderManualService/SalesSummaryCreditOrderReconciliationService geravam a ordem com o
 * valor bruto, sem descontar ajuste de débito nenhum (ver RV 338015830). Agora que os dois
 * geradores já descontam TODO ajuste de débito (não só POS_FEE) na hora de persistir
 * releaseValue, aquela compensação em memória foi removida — repeti-la aqui contaria a tarifa
 * de POS duas vezes e derrubava o match (confirmado com dados reais: RV 60012393, 121364678 e
 * outras pararam de conciliar automaticamente depois do backfill, até essa compensação
 * duplicada ser removida). O matcher agora usa CreditOrderEntity.releaseValue diretamente.
 */
class BankReconciliationServiceNetAdjustmentTest {

  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final InstallmentAcqRepository installmentAcqRepository = mock(InstallmentAcqRepository.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final EntityManager entityManager = mock(EntityManager.class);
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
    settingsService
  );

  @Test
  void releaseMatchesCreditOrderDirectlyWhenReleaseValueIsAlreadyNetOfDebitAdjustments() {
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
    summary.setId(UUID.randomUUID());

    // RV 60012393: releaseValue já sai líquido da tarifa de POS (299,42) na geração/backfill —
    // o lançamento bancário (também líquido) bate direto, sem nenhum desconto em memória.
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
    order.setReleaseValue(new BigDecimal("13121.87"));
    order.setSalesSummary(summary);

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
  void releaseStaysPendingWhenCreditOrderValueDoesNotMatchRelease() {
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
