package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a ferramenta de vínculo automático de lançamentos cuja soma de ordens de crédito
 * candidatas disponíveis fica abaixo do valor do lançamento (padrão de vendas anteriores à
 * implantação, sem ordem no sistema) — ver PreImplantationDivergenceReconciliationService.
 * Regras cobertas: só vincula quando a diferença é positiva (lançamento > ordens), sem teto de
 * valor, e preview() nunca chama o serviço de vínculo.
 */
class PreImplantationDivergenceReconciliationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ManualBankReconciliationService manualBankReconciliationService =
    mock(ManualBankReconciliationService.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  // contextOf/isCreditOrderCandidateCompatible não tocam nenhum campo injetado — mesmo padrão já
  // usado em BankReconciliationServiceReassignmentTest/NetAdjustmentTest para testar esses
  // métodos isoladamente sem contexto Spring.
  private final BankReconciliationService bankReconciliationService =
    new BankReconciliationService(null, null, null, null, null, null, null, null, null, null);
  private final PendingReceiptReleaseFinder pendingReceiptReleaseFinder =
    new PendingReceiptReleaseFinder(releasesBankRepository, implantationDateProvider);
  private final CreditOrderCandidateFinder creditOrderCandidateFinder =
    new CreditOrderCandidateFinder(creditOrderRepository, bankReconciliationService);

  private final PreImplantationDivergenceReconciliationService service =
    new PreImplantationDivergenceReconciliationService(
      pendingReceiptReleaseFinder, creditOrderCandidateFinder,
      manualBankReconciliationService, settingsService
    );

  private CompanyEntity company;
  private AcquirerEntity acquirer;
  private BankEntity bank;
  private BankingDomicileEntity bankingDomicile;

  @BeforeEach
  void setUp() {
    company = new CompanyEntity();
    company.setId(UUID.randomUUID());
    company.setFantasyName("Acquamania Multiplo Lazer S.A");

    acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Rede S/A");

    bank = new BankEntity();
    bank.setId(UUID.randomUUID());

    bankingDomicile = new BankingDomicileEntity();
    bankingDomicile.setId(UUID.randomUUID());
    bankingDomicile.setBank(bank);

    when(settingsService.getDateToleranceDaysBefore()).thenReturn(5);
    when(settingsService.getDateToleranceDaysAfter()).thenReturn(0);
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(settingsService.isFlagMatchRequired()).thenReturn(false);
    when(settingsService.isEstablishmentMatchRequired()).thenReturn(false);
    when(settingsService.isPaymentKindMatchRequired()).thenReturn(false);
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2024, 7, 1));
  }

  private ReleasesBankEntity release(BigDecimal value, LocalDate date) {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(bank);
    release.setReleaseDate(date);
    release.setReleaseValue(value);
    return release;
  }

  private CreditOrderEntity order(BigDecimal value, LocalDate date) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setCompany(company);
    order.setAcquirer(acquirer);
    order.setBankingDomicile(bankingDomicile);
    order.setReleaseDate(date);
    order.setReleaseValue(value);
    return order;
  }

  @Test
  void previewMarksReleaseEligibleWhenReleaseValueExceedsAvailableOrdersSum() {
    LocalDate date = LocalDate.of(2025, 2, 26);
    ReleasesBankEntity release = release(new BigDecimal("30635.93"), date);
    CreditOrderEntity o1 = order(new BigDecimal("3753.94"), date);
    CreditOrderEntity o2 = order(new BigDecimal("26880.50"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1, o2));

    PreImplantationDivergencePreviewResult result = service.preview();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.eligibleToLink()).isEqualTo(1);
    assertThat(result.skippedNegativeDifference()).isZero();
    assertThat(result.skippedNoCandidates()).isZero();
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().getFirst().releaseBankId()).isEqualTo(release.getId());
    assertThat(result.candidates().getFirst().companyName()).isEqualTo("Acquamania Multiplo Lazer S.A");
    assertThat(result.candidates().getFirst().acquirerName()).isEqualTo("Rede S/A");
    assertThat(result.candidates().getFirst().matchedOrders()).isEqualTo(2);
    assertThat(result.candidates().getFirst().difference()).isEqualByComparingTo("1.49");
    verify(manualBankReconciliationService, never()).reconcile(any(), anyList(), any());
  }

  @Test
  void skipsWhenAvailableOrdersSumExceedsReleaseValueBeyondTolerance() {
    LocalDate date = LocalDate.of(2025, 2, 14);
    ReleasesBankEntity release = release(new BigDecimal("100.00"), date);
    CreditOrderEntity o1 = order(new BigDecimal("150.00"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1));

    PreImplantationDivergencePreviewResult result = service.preview();

    assertThat(result.eligibleToLink()).isZero();
    assertThat(result.skippedNegativeDifference()).isEqualTo(1);
    assertThat(result.candidates()).isEmpty();
  }

  @Test
  void skipsWhenNoCandidateOrdersFound() {
    LocalDate date = LocalDate.of(2025, 3, 26);
    ReleasesBankEntity release = release(new BigDecimal("13154.12"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of());

    PreImplantationDivergencePreviewResult result = service.preview();

    assertThat(result.eligibleToLink()).isZero();
    assertThat(result.skippedNoCandidates()).isEqualTo(1);
  }

  @Test
  void applyCallsReconcileWithStandardReasonForEligibleReleases() {
    LocalDate date = LocalDate.of(2025, 2, 26);
    ReleasesBankEntity release = release(new BigDecimal("30635.93"), date);
    CreditOrderEntity o1 = order(new BigDecimal("3753.94"), date);
    CreditOrderEntity o2 = order(new BigDecimal("26880.50"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1, o2));

    PreImplantationDivergenceApplyResult result = service.apply(null);

    assertThat(result.linked()).isEqualTo(1);
    assertThat(result.skippedNegativeDifference()).isZero();
    assertThat(result.skippedNoCandidates()).isZero();
    verify(manualBankReconciliationService).reconcile(
      eq(release.getId()),
      eq(List.of(o1.getId(), o2.getId())),
      eq("Lançamento inclui vendas anteriores à implantação, sem ordem de crédito no sistema")
    );
  }

  @Test
  void applyDoesNotCallReconcileForSkippedReleases() {
    LocalDate date = LocalDate.of(2025, 2, 14);
    ReleasesBankEntity release = release(new BigDecimal("100.00"), date);
    CreditOrderEntity o1 = order(new BigDecimal("150.00"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1));

    service.apply(null);

    verify(manualBankReconciliationService, never()).reconcile(any(), anyList(), any());
  }

  @Test
  void applyOnlyLinksReleasesInTheGivenSelection() {
    LocalDate date = LocalDate.of(2025, 2, 26);
    ReleasesBankEntity selectedRelease = release(new BigDecimal("30635.93"), date);
    ReleasesBankEntity notSelectedRelease = release(new BigDecimal("13154.12"), date);
    CreditOrderEntity o1 = order(new BigDecimal("3753.94"), date);
    CreditOrderEntity o2 = order(new BigDecimal("10000.00"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(selectedRelease, notSelectedRelease));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1, o2));

    PreImplantationDivergenceApplyResult result = service.apply(List.of(selectedRelease.getId()));

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.linked()).isEqualTo(1);
    verify(manualBankReconciliationService).reconcile(eq(selectedRelease.getId()), anyList(), any());
    verify(manualBankReconciliationService, never()).reconcile(eq(notSelectedRelease.getId()), anyList(), any());
  }
}
