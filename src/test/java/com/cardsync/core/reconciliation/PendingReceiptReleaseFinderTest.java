package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o teto de data adicionado à busca compartilhada pelas ferramentas de análise (divergência
 * pré-implantação, legado sem ordem de crédito): sem ele, um lançamento bancário muito DEPOIS do
 * go-live (confirmado com dados reais: ~2 anos depois) era oferecido pra vínculo com a
 * justificativa "venda anterior à implantação sem ordem no sistema" — quando na verdade eram
 * vendas correntes sem nenhuma relação com implantação. O teto reusa
 * ReconciliationSettingsService#getLegacyMarkingCutoffDate (go-live + legacyMarkingMonths), mesmo
 * limite já usado por NoCreditOrderLegacyMarkingService.
 */
class PendingReceiptReleaseFinderTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final ImplantationDateProvider implantationDateProvider = mock(ImplantationDateProvider.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);

  private final PendingReceiptReleaseFinder finder =
    new PendingReceiptReleaseFinder(releasesBankRepository, implantationDateProvider, reconciliationSettingsService);

  @Test
  void passesGoLiveAsLowerBoundAndLegacyMarkingCutoffAsUpperBound() {
    LocalDate goLiveDate = LocalDate.of(2024, 7, 1);
    LocalDate legacyCutoffDate = LocalDate.of(2025, 7, 1);

    when(implantationDateProvider.get()).thenReturn(goLiveDate);
    when(reconciliationSettingsService.getLegacyMarkingCutoffDate()).thenReturn(legacyCutoffDate);
    when(releasesBankRepository.findPendingForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of());

    finder.find();

    verify(releasesBankRepository).findPendingForPreImplantationDivergence(
      eq(StatusPaymentBankEnum.PENDING.getCode()), eq(ReleaseCategoryEnum.RECEIPT.getCode()),
      any(), eq(goLiveDate), eq(legacyCutoffDate)
    );
  }

  @Test
  void resolvesNullLegacyMarkingCutoffToLocalDateMaxInsteadOfPassingNull() {
    LocalDate goLiveDate = LocalDate.of(2024, 7, 1);

    when(implantationDateProvider.get()).thenReturn(goLiveDate);
    when(reconciliationSettingsService.getLegacyMarkingCutoffDate()).thenReturn(null);
    when(releasesBankRepository.findPendingForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of());

    finder.find();

    // Nunca null: "$param is null or ..." sem coluna do outro lado falha no Postgres com "não foi
    // possível determinar o tipo de dados do parâmetro" (extended query protocol não infere tipo
    // só de "? is null") — confirmado em produção/dev real. LocalDate.MAX = sem teto, sem null-check.
    verify(releasesBankRepository).findPendingForPreImplantationDivergence(
      any(), any(), any(), eq(goLiveDate), eq(LocalDate.MAX)
    );
  }

  @Test
  void returnsWhateverTheRepositoryFinds() {
    when(implantationDateProvider.get()).thenReturn(LocalDate.of(2024, 7, 1));
    when(reconciliationSettingsService.getLegacyMarkingCutoffDate()).thenReturn(LocalDate.of(2025, 7, 1));
    when(releasesBankRepository.findPendingForPreImplantationDivergence(any(), any(), any(), any(), any()))
      .thenReturn(List.of());

    assertThat(finder.find()).isEmpty();
  }
}
