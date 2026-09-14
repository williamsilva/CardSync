package com.cardsync.core.file.service;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.AdjustmentStatusEnum;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): AdjustmentStatusEnum.NOT_LOCATED_ERP_ACQ ("Não Localizada"
 * no frontend) existia mas nunca era atribuído por nada - todo ajuste ficava PENDING pra sempre,
 * mesmo quando o sistema já sabia, no momento da importação, que nunca vai achar a venda
 * correspondente (nem transação, nem resumo de vendas).
 */
class AdjustmentTransactionLinkServiceTest {

  private final AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);

  private final AdjustmentTransactionLinkService service =
    new AdjustmentTransactionLinkService(adjustmentRepository, transactionAcqRepository, salesSummaryRepository);

  @Test
  void marksTrulyOrphanedAdjustmentAsNotLocated() {
    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setNsu(999L);
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.PENDING);
    // sem acquirer/pv/rv/salesSummary - nem transação nem resumo de vendas são localizáveis.
    when(transactionAcqRepository.findFirstByNsu(999L)).thenReturn(Optional.empty());

    service.linkSavedAdjustments(List.of(adjustment));

    assertThat(adjustment.getAdjustmentStatus()).isEqualTo(AdjustmentStatusEnum.NOT_LOCATED_ERP_ACQ);
  }

  @Test
  void doesNotMarkAsNotLocatedWhenOnlySalesSummaryIsFound() {
    // Ex.: aluguel de POS/pinpad (motivo 28) - nunca tem NSU/transação por design, mas o RV/PV
    // ainda identifica o resumo de vendas certo. Não é "não localizada", é um caso sem transação
    // específica por natureza.
    AcquirerEntity acquirer = withId(new AcquirerEntity());
    SalesSummaryEntity summary = withId(new SalesSummaryEntity());

    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.PENDING);
    adjustment.setAcquirer(acquirer);
    adjustment.setPvNumberOriginal(7867379);
    adjustment.setRvNumberOriginal(60012393);
    // nsu ausente -> findTransaction já retorna vazio sem consultar nada.

    when(salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(
      acquirer.getId(), 7867379, 60012393
    )).thenReturn(Optional.of(summary));

    service.linkSavedAdjustments(List.of(adjustment));

    assertThat(adjustment.getSalesSummary()).isSameAs(summary);
    assertThat(adjustment.getAdjustmentStatus()).isEqualTo(AdjustmentStatusEnum.PENDING);
  }

  @Test
  void neverOverwritesAManuallyDecidedStatus() {
    AdjustmentEntity adjustment = withId(new AdjustmentEntity());
    adjustment.setNsu(1L);
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.FAVORED_CLIENT);
    when(transactionAcqRepository.findFirstByNsu(1L)).thenReturn(Optional.empty());

    service.linkSavedAdjustments(List.of(adjustment));

    assertThat(adjustment.getAdjustmentStatus()).isEqualTo(AdjustmentStatusEnum.FAVORED_CLIENT);
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
