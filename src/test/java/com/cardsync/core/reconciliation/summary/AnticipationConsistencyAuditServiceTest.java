package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.model.conciliation.AnticipationConsistencyAuditResult;
import com.cardsync.domain.repository.AnticipationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): antecipação sem domicílio bancário resolvido nunca
 * concilia com o extrato bancário - antes, o único sinal disso era um log.warn na importação,
 * sem forma de encontrar depois quais antecipações ficaram travadas assim.
 */
class AnticipationConsistencyAuditServiceTest {

  private final AnticipationRepository anticipationRepository = mock(AnticipationRepository.class);
  private final AnticipationConsistencyAuditService service = new AnticipationConsistencyAuditService(anticipationRepository);

  @Test
  void reportsNoIssuesWithoutQueryingSamples() {
    when(anticipationRepository.countMissingBankingDomicile()).thenReturn(0L);

    AnticipationConsistencyAuditResult result = service.audit();

    assertThat(result.hasMissingBankingDomicile()).isFalse();
    assertThat(result.missingBankingDomicileSampleIds()).isEmpty();
    verify(anticipationRepository, never()).findIdsMissingBankingDomicile(any());
  }

  @Test
  void reportsCountAndSampleWhenAnticipationsAreStuck() {
    UUID stuckId = UUID.randomUUID();
    when(anticipationRepository.countMissingBankingDomicile()).thenReturn(2L);
    when(anticipationRepository.findIdsMissingBankingDomicile(any())).thenReturn(List.of(stuckId));

    AnticipationConsistencyAuditResult result = service.audit();

    assertThat(result.hasMissingBankingDomicile()).isTrue();
    assertThat(result.missingBankingDomicileCount()).isEqualTo(2L);
    assertThat(result.missingBankingDomicileSampleIds()).containsExactly(stuckId);
  }
}
