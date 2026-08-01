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
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre BankingDomicileDivergenceService: diagnóstico (nunca vincula sozinho) de lançamento
 * bancário pendente que só fecha o valor ignorando o banco de uma das ordens candidatas —
 * indício de banking_domicile apontando pro banco errado (caso real: RV 86015456, o arquivo
 * EEVD da Rede declarou banco=Santander pra aquela RV, mas o repasse caiu no Sicredi; a empresa
 * já tinha os dois domicílios cadastrados).
 */
class BankingDomicileDivergenceServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final BankingDomicileRepository bankingDomicileRepository = mock(BankingDomicileRepository.class);
  private final ReconciliationSettingsService settingsService = mock(ReconciliationSettingsService.class);
  private final BankReconciliationMatcher matcher = new BankReconciliationMatcher();
  private final BankReconciliationService bankReconciliationService =
    new BankReconciliationService(null, null, null, null, null, null, null, null, null);

  private final BankingDomicileDivergenceService service = new BankingDomicileDivergenceService(
    releasesBankRepository, creditOrderRepository, bankingDomicileRepository,
    matcher, bankReconciliationService, settingsService, new FileProcessingProperties()
  );

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }

  private void stubSettings() {
    when(settingsService.getDateToleranceDaysBefore()).thenReturn(0);
    when(settingsService.getDateToleranceDaysAfter()).thenReturn(0);
    when(settingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));
    when(settingsService.isFlagMatchRequired()).thenReturn(false);
    when(settingsService.isEstablishmentMatchRequired()).thenReturn(false);
    when(settingsService.isPaymentKindMatchRequired()).thenReturn(false);
    when(settingsService.getSubsetDpMaxCents()).thenReturn(100_000_000L);
  }

  @Test
  void flagsOrderWithDifferentBankWhenItsTheOnlyWayToCloseTheValue() {
    stubSettings();

    CompanyEntity company = withId(new CompanyEntity());
    company.setFantasyName("Acquamania Multiplo Lazer S.A");
    AcquirerEntity acquirer = withId(new AcquirerEntity());

    BankEntity sicredi = withId(new BankEntity());
    sicredi.setName("Sicredi");
    BankEntity santander = withId(new BankEntity());
    santander.setName("Santander");

    BankingDomicileEntity sicrediDomicile = new BankingDomicileEntity();
    sicrediDomicile.setBank(sicredi);
    BankingDomicileEntity santanderDomicile = new BankingDomicileEntity();
    santanderDomicile.setBank(santander);

    LocalDate releaseDate = LocalDate.of(2026, 3, 30);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(sicredi);
    release.setReleaseDate(releaseDate);
    release.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity correctlyBanked = withId(new CreditOrderEntity());
    correctlyBanked.setCompany(company);
    correctlyBanked.setAcquirer(acquirer);
    correctlyBanked.setBankingDomicile(sicrediDomicile);
    correctlyBanked.setReleaseDate(releaseDate);
    correctlyBanked.setReleaseValue(new BigDecimal("60.00"));

    CreditOrderEntity wrongBanked = withId(new CreditOrderEntity());
    wrongBanked.setRvNumber(86015456);
    wrongBanked.setCompany(company);
    wrongBanked.setAcquirer(acquirer);
    wrongBanked.setBankingDomicile(santanderDomicile);
    wrongBanked.setReleaseDate(releaseDate);
    wrongBanked.setReleaseValue(new BigDecimal("40.00"));

    when(releasesBankRepository.findPendingForBankingDomicileDivergence(any(), any(), any()))
      .thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of(correctlyBanked, wrongBanked));
    when(bankingDomicileRepository.findByCompany_IdAndBank_Id(company.getId(), sicredi.getId()))
      .thenReturn(List.of(sicrediDomicile));

    BankingDomicileDivergencePreviewResult result = service.preview();

    assertThat(result.releasesAnalyzed()).isEqualTo(1);
    assertThat(result.candidatesFound()).isEqualTo(1);
    BankingDomicileDivergenceCandidate candidate = result.candidates().getFirst();
    assertThat(candidate.releaseBankId()).isEqualTo(release.getId());
    assertThat(candidate.mismatchedOrders()).hasSize(1);
    assertThat(candidate.mismatchedOrders().getFirst().creditOrderId()).isEqualTo(wrongBanked.getId());
    assertThat(candidate.mismatchedOrders().getFirst().currentBankName()).isEqualTo("Santander");
  }

  @Test
  void skipsReleaseThatAlreadyMatchesRespectingBank() {
    stubSettings();

    CompanyEntity company = withId(new CompanyEntity());
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    BankEntity sicredi = withId(new BankEntity());

    BankingDomicileEntity sicrediDomicile = new BankingDomicileEntity();
    sicrediDomicile.setBank(sicredi);

    LocalDate releaseDate = LocalDate.of(2026, 3, 30);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(sicredi);
    release.setReleaseDate(releaseDate);
    release.setReleaseValue(new BigDecimal("60.00"));

    CreditOrderEntity correctlyBanked = withId(new CreditOrderEntity());
    correctlyBanked.setCompany(company);
    correctlyBanked.setAcquirer(acquirer);
    correctlyBanked.setBankingDomicile(sicrediDomicile);
    correctlyBanked.setReleaseDate(releaseDate);
    correctlyBanked.setReleaseValue(new BigDecimal("60.00"));

    when(releasesBankRepository.findPendingForBankingDomicileDivergence(any(), any(), any()))
      .thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of(correctlyBanked));

    BankingDomicileDivergencePreviewResult result = service.preview();

    assertThat(result.candidatesFound()).isZero();
  }

  @Test
  void skipsReleaseWhenNoCombinationClosesTheValueEvenIgnoringBank() {
    stubSettings();

    CompanyEntity company = withId(new CompanyEntity());
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    BankEntity sicredi = withId(new BankEntity());
    BankEntity santander = withId(new BankEntity());

    BankingDomicileEntity santanderDomicile = new BankingDomicileEntity();
    santanderDomicile.setBank(santander);

    LocalDate releaseDate = LocalDate.of(2026, 3, 30);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setCompany(company);
    release.setAcquirer(acquirer);
    release.setBank(sicredi);
    release.setReleaseDate(releaseDate);
    release.setReleaseValue(new BigDecimal("100.00"));

    CreditOrderEntity onlyOrder = withId(new CreditOrderEntity());
    onlyOrder.setCompany(company);
    onlyOrder.setAcquirer(acquirer);
    onlyOrder.setBankingDomicile(santanderDomicile);
    onlyOrder.setReleaseDate(releaseDate);
    onlyOrder.setReleaseValue(new BigDecimal("40.00"));

    when(releasesBankRepository.findPendingForBankingDomicileDivergence(any(), any(), any()))
      .thenReturn(List.of(release));
    when(creditOrderRepository.findCandidatesForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of(onlyOrder));

    BankingDomicileDivergencePreviewResult result = service.preview();

    assertThat(result.candidatesFound()).isZero();
  }
}
