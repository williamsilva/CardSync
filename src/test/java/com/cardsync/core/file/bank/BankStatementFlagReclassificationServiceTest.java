package com.cardsync.core.file.bank;

import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o backfill que corrige, nos lançamentos bancários CNAB240 já importados, a bandeira
 * errada causada pelo bug de resolveFlag (erp_code como substring numérica solta — ver
 * BankStatementClassifierServiceTest). Reaproveita o texto já persistido em
 * descriptionHistoricalBank/documentComplementNumber/complementRelease, sem precisar reler os
 * arquivos originais.
 */
class BankStatementFlagReclassificationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final FlagRepository flagRepository = mock(FlagRepository.class);
  private final BankTextSignalResolver textSignalResolver = new BankTextSignalResolver();
  private final BankStatementClassifierService classifierService = new BankStatementClassifierService(
    textSignalResolver, null, null, null, flagRepository
  );
  private final BankStatementFlagReclassificationService service = new BankStatementFlagReclassificationService(
    releasesBankRepository, classifierService, textSignalResolver, flagRepository
  );

  @Test
  void correctsWrongAmexFlagToCabalUsingAlreadyStoredHistoricalText() {
    when(flagRepository.findAll()).thenReturn(List.of(
      flag("Mastercard", 1),
      flag("Visa", 2),
      flag("American Express", 3),
      flag("Banescard", 9),
      flag("Cabal", 10)
    ));

    FlagEntity wrongFlag = flag("American Express", 3);
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setFlag(wrongFlag);
    release.setDescriptionHistoricalBank("PAGTO. C DEB");
    release.setDocumentComplementNumber("867379REDE-CABAL DEB");
    release.setComplementRelease(null);

    when(releasesBankRepository.findPendingForFlagReclassification(eq(StatusPaymentBankEnum.PENDING.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementFlagsResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.stillUnresolved()).isZero();
    assertThat(release.getFlag().getName()).isEqualTo("Cabal");
    verify(releasesBankRepository).saveAll(List.of(release));
  }

  @Test
  void leavesAlreadyCorrectFlagUntouchedAndDoesNotCountAsUpdated() {
    UUID visaId = UUID.randomUUID();
    FlagEntity visaInRepository = flag("Visa", 2);
    visaInRepository.setId(visaId);

    when(flagRepository.findAll()).thenReturn(List.of(
      flag("Mastercard", 1),
      visaInRepository
    ));

    // Mesmo id do que está no repositório de flags — representa a MESMA bandeira já correta,
    // só que como uma instância JPA distinta (o cenário real: entidade gerenciada carregada
    // separadamente do release).
    FlagEntity correctFlag = flag("Visa", 2);
    correctFlag.setId(visaId);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setFlag(correctFlag);
    release.setDescriptionHistoricalBank("PAGTO. C CRED");
    release.setDocumentComplementNumber("693702REDE-VISA CRED");

    when(releasesBankRepository.findPendingForFlagReclassification(eq(StatusPaymentBankEnum.PENDING.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementFlagsResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(release.getFlag()).isSameAs(correctFlag);
    verify(releasesBankRepository, org.mockito.Mockito.never()).saveAll(any());
  }

  private FlagEntity flag(String name, int erpCode) {
    FlagEntity flag = new FlagEntity();
    flag.setId(UUID.randomUUID());
    flag.setName(name);
    flag.setErpCode(erpCode);
    return flag;
  }
}
