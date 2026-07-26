package com.cardsync.core.file.bank;

import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o backfill que corrige, nos lançamentos bancários de recebimento já importados, a
 * modalidade (débito/crédito) não classificada — o que os torna invisíveis no Extrato Bancário,
 * já que ReleasesBankSpecs sempre restringe a listagem a {CASH_DEBIT, CASH_CREDIT, ANTECIP_CRED}.
 * Causa raiz: BankTextSignalResolver.isCreditSignal não reconhecia "CD" (abreviação real do
 * Rede/Santander pra crédito, ex.: "REDE   MAST CD0007866470") — ver BankTextSignalResolverTest.
 */
class BankStatementModalityReclassificationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final BankTextSignalResolver textSignalResolver = new BankTextSignalResolver();
  private final BankStatementModalityReclassificationService service =
    new BankStatementModalityReclassificationService(releasesBankRepository, textSignalResolver);

  @Test
  void resolvesCreditModalityFromAbbreviatedCdSignal() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setModalityPaymentBank(ModalityPaymentBankEnum.NULL);
    release.setDescriptionHistoricalBank("REDE   MAST CD0007866470");

    when(releasesBankRepository.findUnclassifiedModalityForReclassification(
      eq(ReleaseCategoryEnum.RECEIPT.getCode()), eq(ModalityPaymentBankEnum.NULL.getCode())
    )).thenReturn(List.of(release));

    ReclassifyBankStatementModalityResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.stillUnresolved()).isZero();
    assertThat(release.getModalityPaymentBank()).isEqualTo(ModalityPaymentBankEnum.CASH_CREDIT);
    verify(releasesBankRepository).saveAll(List.of(release));
  }

  @Test
  void resolvesDebitModalityAndChecksItBeforeCredit() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setModalityPaymentBank(ModalityPaymentBankEnum.NULL);
    // Contém "ELO" (sinal de crédito) e "DB" (sinal de débito) — débito deve vencer, já que é o
    // marcador explícito e mais específico do texto real (mesma ordem de checagem do classifier).
    release.setDescriptionHistoricalBank("REDE   ELO  DB0074705318");

    when(releasesBankRepository.findUnclassifiedModalityForReclassification(
      eq(ReleaseCategoryEnum.RECEIPT.getCode()), eq(ModalityPaymentBankEnum.NULL.getCode())
    )).thenReturn(List.of(release));

    ReclassifyBankStatementModalityResult result = service.reclassifyAll();

    assertThat(result.updated()).isEqualTo(1);
    assertThat(release.getModalityPaymentBank()).isEqualTo(ModalityPaymentBankEnum.CASH_DEBIT);
  }

  @Test
  void leavesReleaseWithoutAnySignalUnresolvedAndDoesNotSave() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setModalityPaymentBank(ModalityPaymentBankEnum.NULL);
    release.setDescriptionHistoricalBank("TED RECEBIDA");

    when(releasesBankRepository.findUnclassifiedModalityForReclassification(
      eq(ReleaseCategoryEnum.RECEIPT.getCode()), eq(ModalityPaymentBankEnum.NULL.getCode())
    )).thenReturn(List.of(release));

    ReclassifyBankStatementModalityResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(result.stillUnresolved()).isEqualTo(1);
    assertThat(release.getModalityPaymentBank()).isEqualTo(ModalityPaymentBankEnum.NULL);
    verify(releasesBankRepository, never()).saveAll(any());
  }
}
