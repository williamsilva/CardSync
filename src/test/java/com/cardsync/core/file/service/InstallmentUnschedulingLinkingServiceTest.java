package com.cardsync.core.file.service;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.InstallmentUnschedulingEntity;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): um desagendamento (Rede EEVD registro "08" - chargeback ou
 * cancelamento de uma parcela já creditada) nunca era refletido na InstallmentAcqEntity real -
 * ficava só armazenado pra relatório, nunca cancelava a parcela que a conciliação bancária usa.
 */
class InstallmentUnschedulingLinkingServiceTest {

  private final InstallmentAcqRepository installmentAcqRepository = mock(InstallmentAcqRepository.class);
  private final InstallmentUnschedulingLinkingService service = new InstallmentUnschedulingLinkingService(installmentAcqRepository);

  @Test
  void cancelsTheSingleMatchingInstallmentByAcquirerAndNsu() {
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setNsu(123456L);
    unscheduling.setTransactionDate(LocalDate.of(2026, 10, 5));

    InstallmentAcqEntity installment = withId(new InstallmentAcqEntity());
    installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());

    when(installmentAcqRepository.findByAcquirerIdAndTransactionNsu(acquirer.getId(), 123456L))
      .thenReturn(List.of(installment));

    InstallmentUnschedulingLinkingService.LinkResult result = service.linkSavedUnschedulings(List.of(unscheduling));

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.canceled()).isEqualTo(1);
    assertThat(result.ambiguous()).isZero();
    assertThat(installment.getInstallmentStatus()).isEqualTo(StatusInstallmentEnum.CANCELED.getCode());
    assertThat(installment.getCancellationDate()).isEqualTo(LocalDate.of(2026, 10, 5));
  }

  @Test
  void skipsWhenAcquirerAndNsuMatchMoreThanOneInstallment() {
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setNsu(999L);

    InstallmentAcqEntity first = withId(new InstallmentAcqEntity());
    InstallmentAcqEntity second = withId(new InstallmentAcqEntity());
    first.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
    second.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());

    when(installmentAcqRepository.findByAcquirerIdAndTransactionNsu(acquirer.getId(), 999L))
      .thenReturn(List.of(first, second));

    InstallmentUnschedulingLinkingService.LinkResult result = service.linkSavedUnschedulings(List.of(unscheduling));

    assertThat(result.canceled()).isZero();
    assertThat(result.ambiguous()).isEqualTo(1);
    assertThat(first.getInstallmentStatus()).isEqualTo(StatusInstallmentEnum.SCHEDULED.getCode());
    assertThat(second.getInstallmentStatus()).isEqualTo(StatusInstallmentEnum.SCHEDULED.getCode());
  }

  @Test
  void skipsWhenNoInstallmentMatches() {
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setNsu(1L);

    when(installmentAcqRepository.findByAcquirerIdAndTransactionNsu(acquirer.getId(), 1L)).thenReturn(List.of());

    InstallmentUnschedulingLinkingService.LinkResult result = service.linkSavedUnschedulings(List.of(unscheduling));

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.canceled()).isZero();
    assertThat(result.ambiguous()).isZero();
  }

  @Test
  void skipsAlreadyCanceledInstallmentWithoutOverwritingCancellationDate() {
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setNsu(42L);
    unscheduling.setTransactionDate(LocalDate.of(2026, 11, 1));

    InstallmentAcqEntity installment = withId(new InstallmentAcqEntity());
    installment.setInstallmentStatus(StatusInstallmentEnum.CANCELED.getCode());
    installment.setCancellationDate(LocalDate.of(2026, 9, 1));

    when(installmentAcqRepository.findByAcquirerIdAndTransactionNsu(acquirer.getId(), 42L))
      .thenReturn(List.of(installment));

    InstallmentUnschedulingLinkingService.LinkResult result = service.linkSavedUnschedulings(List.of(unscheduling));

    assertThat(result.canceled()).isZero();
    assertThat(installment.getCancellationDate()).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
