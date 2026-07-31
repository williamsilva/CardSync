package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.EstablishmentEntity;
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
    new PendingReceiptReleaseFinder(releasesBankRepository, implantationDateProvider, settingsService);
  private final CreditOrderCandidateFinder creditOrderCandidateFinder =
    new CreditOrderCandidateFinder(creditOrderRepository, bankReconciliationService);

  private final PreImplantationDivergenceReconciliationService service =
    new PreImplantationDivergenceReconciliationService(
      creditOrderCandidateFinder, pendingReceiptReleaseFinder,
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
    return release(value, date, null);
  }

  private ReleasesBankEntity release(BigDecimal value, LocalDate date, EstablishmentEntity establishment) {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(bank);
    release.setReleaseDate(date);
    release.setReleaseValue(value);
    release.setEstablishment(establishment);
    return release;
  }

  private CreditOrderEntity order(BigDecimal value, LocalDate date) {
    return order(value, date, null);
  }

  private CreditOrderEntity order(BigDecimal value, LocalDate date, Integer pvCentralizer) {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setId(UUID.randomUUID());
    order.setCompany(company);
    order.setAcquirer(acquirer);
    order.setBankingDomicile(bankingDomicile);
    order.setReleaseDate(date);
    order.setReleaseValue(value);
    order.setPvCentralizer(pvCentralizer);
    return order;
  }

  private EstablishmentEntity establishment(int pvNumber) {
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setId(UUID.randomUUID());
    establishment.setPvNumber(pvNumber);
    return establishment;
  }

  @Test
  void previewMarksReleaseEligibleWhenReleaseValueExceedsAvailableOrdersSum() {
    LocalDate date = LocalDate.of(2025, 2, 26);
    ReleasesBankEntity release = release(new BigDecimal("30635.93"), date);
    CreditOrderEntity o1 = order(new BigDecimal("3753.94"), date);
    CreditOrderEntity o2 = order(new BigDecimal("26880.50"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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

  /**
   * Reproduz o caso real encontrado em 14/08/2024 (Acquamania Multiplo Lazer, Rede S/A/Santander/
   * Mastercard): dois lançamentos do mesmo dia/banco/adquirente/bandeira, cada um em um
   * estabelecimento (PV) diferente — não consolidados. Sem preferir candidatas do mesmo PV do
   * lançamento, o pool ignorando estabelecimento somava as ordens dos DOIS PVs pra cada
   * lançamento, inflando a soma disponível acima do valor de cada um e classificando ambos como
   * SKIPPED_NEGATIVE — mascarando a divergência real (falta de ordem) como falso excesso.
   */
  @Test
  void prefersSameEstablishmentCandidatesWhenSameDayReleasesAreSplitByPv() {
    LocalDate date = LocalDate.of(2024, 8, 14);
    EstablishmentEntity pvA = establishment(7867379);
    EstablishmentEntity pvB = establishment(93693702);

    ReleasesBankEntity releaseA = release(new BigDecimal("3582.05"), date, pvA);
    ReleasesBankEntity releaseB = release(new BigDecimal("906.96"), date, pvB);

    CreditOrderEntity oA1 = order(new BigDecimal("64.49"), date, 7867379);
    CreditOrderEntity oA2 = order(new BigDecimal("277.54"), date, 7867379);
    CreditOrderEntity oA3 = order(new BigDecimal("3074.89"), date, 7867379);
    CreditOrderEntity oB1 = order(new BigDecimal("274.82"), date, 93693702);
    CreditOrderEntity oB2 = order(new BigDecimal("352.26"), date, 93693702);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
    )).thenReturn(List.of(releaseA, releaseB));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(oA1, oA2, oA3, oB1, oB2));

    PreImplantationDivergencePreviewResult result = service.preview();

    assertThat(result.analyzed()).isEqualTo(2);
    assertThat(result.eligibleToLink()).isEqualTo(2);
    assertThat(result.skippedNegativeDifference()).isZero();
    assertThat(result.candidates()).hasSize(2);

    PreImplantationDivergenceCandidate candidateA = result.candidates().stream()
      .filter(c -> c.releaseBankId().equals(releaseA.getId())).findFirst().orElseThrow();
    assertThat(candidateA.matchedOrders()).isEqualTo(3);
    assertThat(candidateA.difference()).isEqualByComparingTo("165.13");

    PreImplantationDivergenceCandidate candidateB = result.candidates().stream()
      .filter(c -> c.releaseBankId().equals(releaseB.getId())).findFirst().orElseThrow();
    assertThat(candidateB.matchedOrders()).isEqualTo(2);
    assertThat(candidateB.difference()).isEqualByComparingTo("279.88");
  }

  /**
   * Cenário oposto: um único lançamento consolida valores de mais de um PV (confirmado com o
   * financeiro para alguns bancos, ex. Santander). O PV do próprio lançamento não bate com
   * NENHUMA ordem candidata, então o fallback ignorando estabelecimento precisa entrar em ação
   * — senão a ferramenta pararia de achar candidatas legítimas nesse caso.
   */
  @Test
  void fallsBackToIgnoringEstablishmentWhenReleaseOwnPvHasNoDirectCandidates() {
    LocalDate date = LocalDate.of(2024, 8, 14);
    EstablishmentEntity consolidatedPv = establishment(1111111);
    ReleasesBankEntity release = release(new BigDecimal("342.03"), date, consolidatedPv);

    CreditOrderEntity o1 = order(new BigDecimal("64.49"), date, 7867379);
    CreditOrderEntity o2 = order(new BigDecimal("277.54"), date, 93693702);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
    )).thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(
      eq(company.getId()), eq(StatusPaymentBankEnum.PENDING.getCode()), eq(StatusReconciliationEnum.RECONCILED.getCode()),
      any(), any()
    )).thenReturn(List.of(o1, o2));

    PreImplantationDivergencePreviewResult result = service.preview();

    assertThat(result.eligibleToLink()).isEqualTo(1);
    assertThat(result.skippedNegativeDifference()).isZero();
    assertThat(result.skippedNoCandidates()).isZero();
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().getFirst().matchedOrders()).isEqualTo(2);
    assertThat(result.candidates().getFirst().difference()).isEqualByComparingTo("0.00");
  }

  @Test
  void skipsWhenNoCandidateOrdersFound() {
    LocalDate date = LocalDate.of(2025, 3, 26);
    ReleasesBankEntity release = release(new BigDecimal("13154.12"), date);

    when(releasesBankRepository.findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()), any(), any(), any()
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
