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
 * Cobre a ferramenta de marcação automática de legado — irmã de
 * PreImplantationDivergenceReconciliationServiceTest, mas pro cenário oposto: lançamento
 * pendente sem NENHUMA ordem de crédito candidata, dentro da janela de legado configurada.
 * Regras cobertas: só marca sem candidata nenhuma (se existir alguma, é caso de divergência
 * pré-implantação, não de legado) e dentro da janela; preview() nunca chama markLegacy.
 */
class NoCreditOrderLegacyMarkingServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final ManualBankReconciliationService manualBankReconciliationService =
    mock(ManualBankReconciliationService.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final BankReconciliationService bankReconciliationService =
    new BankReconciliationService(null, null, null, null, null, null, null, null, null, null);
  private final PendingReceiptReleaseFinder pendingReceiptReleaseFinder =
    new PendingReceiptReleaseFinder(releasesBankRepository, implantationDateProvider);
  private final CreditOrderCandidateFinder creditOrderCandidateFinder =
    new CreditOrderCandidateFinder(creditOrderRepository, bankReconciliationService);

  private final NoCreditOrderLegacyMarkingService service = new NoCreditOrderLegacyMarkingService(
    pendingReceiptReleaseFinder, creditOrderCandidateFinder, manualBankReconciliationService, settingsService
  );

  private CompanyEntity company;
  private AcquirerEntity acquirer;
  private BankEntity bank;
  private BankingDomicileEntity bankingDomicile;
  private final LocalDate cutoffDate = LocalDate.of(2025, 7, 1);

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
    when(settingsService.getLegacyMarkingCutoffDate()).thenReturn(cutoffDate);
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

  private void stubNoCandidates(ReleasesBankEntity release) {
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of());
  }

  private void stubPendingReleases(ReleasesBankEntity... releases) {
    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any()
    )).thenReturn(List.of(releases));
  }

  @Test
  void previewMarksReleaseEligibleWhenNoCandidatesAndWithinLegacyWindow() {
    ReleasesBankEntity release = release(new BigDecimal("500.00"), LocalDate.of(2024, 8, 1));
    stubPendingReleases(release);
    stubNoCandidates(release);
    when(manualBankReconciliationService.isEligibleForLegacy(release, cutoffDate)).thenReturn(true);

    NoCreditOrderLegacyPreviewResult result = service.preview();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.eligibleToMark()).isEqualTo(1);
    assertThat(result.skippedHasCandidates()).isZero();
    assertThat(result.skippedOutsideLegacyWindow()).isZero();
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().getFirst().releaseBankId()).isEqualTo(release.getId());
    assertThat(result.candidates().getFirst().companyName()).isEqualTo("Acquamania Multiplo Lazer S.A");
    assertThat(result.candidates().getFirst().acquirerName()).isEqualTo("Rede S/A");
    verify(manualBankReconciliationService, never()).markLegacy(anyList());
  }

  @Test
  void skipsWhenReleaseHasAnyCandidateOrder() {
    ReleasesBankEntity release = release(new BigDecimal("500.00"), LocalDate.of(2024, 8, 1));
    CreditOrderEntity order = order(new BigDecimal("10.00"), release.getReleaseDate());

    stubPendingReleases(release);
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(order));

    NoCreditOrderLegacyPreviewResult result = service.preview();

    assertThat(result.eligibleToMark()).isZero();
    assertThat(result.skippedHasCandidates()).isEqualTo(1);
    assertThat(result.candidates()).isEmpty();
    // Não deveria nem precisar checar elegibilidade de legado quando já tem candidata.
    verify(manualBankReconciliationService, never()).isEligibleForLegacy(any(), any());
  }

  @Test
  void skipsWhenOutsideLegacyWindow() {
    ReleasesBankEntity release = release(new BigDecimal("500.00"), LocalDate.of(2025, 8, 1));
    stubPendingReleases(release);
    stubNoCandidates(release);
    when(manualBankReconciliationService.isEligibleForLegacy(release, cutoffDate)).thenReturn(false);

    NoCreditOrderLegacyPreviewResult result = service.preview();

    assertThat(result.eligibleToMark()).isZero();
    assertThat(result.skippedOutsideLegacyWindow()).isEqualTo(1);
    assertThat(result.candidates()).isEmpty();
  }

  @Test
  void applyCallsMarkLegacyWithEligibleReleaseIds() {
    ReleasesBankEntity release = release(new BigDecimal("500.00"), LocalDate.of(2024, 8, 1));
    stubPendingReleases(release);
    stubNoCandidates(release);
    when(manualBankReconciliationService.isEligibleForLegacy(release, cutoffDate)).thenReturn(true);
    when(manualBankReconciliationService.markLegacy(List.of(release.getId())))
      .thenReturn(new MarkLegacyResult(1, 0));

    NoCreditOrderLegacyApplyResult result = service.apply(null);

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.marked()).isEqualTo(1);
    assertThat(result.skippedHasCandidates()).isZero();
    assertThat(result.skippedOutsideLegacyWindow()).isZero();
    verify(manualBankReconciliationService).markLegacy(List.of(release.getId()));
  }

  @Test
  void applyDoesNotCallMarkLegacyWhenNothingEligible() {
    ReleasesBankEntity release = release(new BigDecimal("500.00"), LocalDate.of(2025, 8, 1));
    stubPendingReleases(release);
    stubNoCandidates(release);
    when(manualBankReconciliationService.isEligibleForLegacy(release, cutoffDate)).thenReturn(false);

    NoCreditOrderLegacyApplyResult result = service.apply(null);

    assertThat(result.marked()).isZero();
    verify(manualBankReconciliationService, never()).markLegacy(anyList());
  }

  @Test
  void applyOnlyMarksReleasesInTheGivenSelection() {
    ReleasesBankEntity selectedRelease = release(new BigDecimal("500.00"), LocalDate.of(2024, 8, 1));
    ReleasesBankEntity notSelectedRelease = release(new BigDecimal("800.00"), LocalDate.of(2024, 8, 2));
    stubPendingReleases(selectedRelease, notSelectedRelease);
    stubNoCandidates(selectedRelease);
    when(manualBankReconciliationService.isEligibleForLegacy(any(), eq(cutoffDate))).thenReturn(true);
    when(manualBankReconciliationService.markLegacy(List.of(selectedRelease.getId())))
      .thenReturn(new MarkLegacyResult(1, 0));

    NoCreditOrderLegacyApplyResult result = service.apply(List.of(selectedRelease.getId()));

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.marked()).isEqualTo(1);
    verify(manualBankReconciliationService).markLegacy(List.of(selectedRelease.getId()));
  }
}
