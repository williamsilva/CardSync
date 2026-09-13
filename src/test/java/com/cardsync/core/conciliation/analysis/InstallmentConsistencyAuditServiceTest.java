package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.InstallmentConsistencyAuditResult;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): nada detectava sozinho uma venda cujo total de parcelas
 * declarado (installment) diverge da quantidade real de parcelas geradas - cenário que já
 * ocorreu de fato (ver ErpAcquirerResolutionServiceInstallmentTest). Esta auditoria só lê e
 * alerta, não corrige nada automaticamente.
 */
class InstallmentConsistencyAuditServiceTest {

  private final TransactionErpRepository transactionErpRepository = mock(TransactionErpRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);

  private final InstallmentConsistencyAuditService service =
    new InstallmentConsistencyAuditService(transactionErpRepository, transactionAcqRepository);

  @Test
  void reportsNoMismatchesWithoutQueryingSamples() {
    when(transactionErpRepository.countWithInstallmentCountMismatch()).thenReturn(0L);
    when(transactionAcqRepository.countWithInstallmentCountMismatch()).thenReturn(0L);

    InstallmentConsistencyAuditResult result = service.audit();

    assertThat(result.hasMismatches()).isFalse();
    assertThat(result.erpSampleIds()).isEmpty();
    assertThat(result.acquirerSampleIds()).isEmpty();
    verify(transactionErpRepository, never()).findIdsWithInstallmentCountMismatch(any());
    verify(transactionAcqRepository, never()).findIdsWithInstallmentCountMismatch(any());
  }

  @Test
  void reportsCountsAndSamplesWhenMismatchesExist() {
    UUID erpId = UUID.randomUUID();
    UUID acqId = UUID.randomUUID();

    when(transactionErpRepository.countWithInstallmentCountMismatch()).thenReturn(3L);
    when(transactionAcqRepository.countWithInstallmentCountMismatch()).thenReturn(1L);
    when(transactionErpRepository.findIdsWithInstallmentCountMismatch(any())).thenReturn(List.of(erpId));
    when(transactionAcqRepository.findIdsWithInstallmentCountMismatch(any())).thenReturn(List.of(acqId));

    InstallmentConsistencyAuditResult result = service.audit();

    assertThat(result.hasMismatches()).isTrue();
    assertThat(result.erpMismatchCount()).isEqualTo(3L);
    assertThat(result.acquirerMismatchCount()).isEqualTo(1L);
    assertThat(result.erpSampleIds()).containsExactly(erpId);
    assertThat(result.acquirerSampleIds()).containsExactly(acqId);
  }
}
