package com.cardsync.core.file.bank;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.AcquirerRepository;
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
 * Cobre o backfill que vincula o adquirente de lançamentos bancários de recebimento já
 * importados sem adquirente resolvido — caso real encontrado em produção: lançamentos "PAGTO.
 * CCRED"/"PAGTO. C DEB" cujo document_complement_number traz "350834GETNET-VISA"/
 * "937256GETNET-ELO" (adquirente Getnet identificável por texto), mas que ficaram com
 * acquirer_id nulo. Reaproveita o texto já persistido em descriptionHistoricalBank/
 * documentComplementNumber/complementRelease, sem precisar reler os arquivos originais.
 */
class BankStatementAcquirerReclassificationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final AcquirerRepository acquirerRepository = mock(AcquirerRepository.class);
  private final BankTextSignalResolver textSignalResolver = new BankTextSignalResolver();
  private final BankStatementClassifierService classifierService = new BankStatementClassifierService(
    null, acquirerRepository, textSignalResolver, null, null
  );
  private final BankStatementAcquirerReclassificationService service = new BankStatementAcquirerReclassificationService(
    releasesBankRepository, classifierService, textSignalResolver, acquirerRepository
  );

  @Test
  void resolvesGetnetAcquirerFromDocumentTextForReleasesLeftWithoutAcquirer() {
    when(acquirerRepository.findAll()).thenReturn(List.of(
      acquirer("Cielo S/A", "Cielo", "Cielo"),
      acquirer("GETNET", "GETNET", "GETNET"),
      acquirer("Rede S/A", "Rede", "Rede")
    ));

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setAcquirer(null);
    release.setDescriptionHistoricalBank("PAGTO. CCRED");
    release.setDocumentComplementNumber("350834GETNET-VISA");
    release.setComplementRelease(null);

    when(releasesBankRepository.findWithoutAcquirerForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementAcquirerResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.stillUnresolved()).isZero();
    assertThat(release.getAcquirer().getFantasyName()).isEqualTo("GETNET");
    verify(releasesBankRepository).saveAll(List.of(release));
  }

  @Test
  void leavesUnresolvedWhenNoAcquirerSignalInText() {
    when(acquirerRepository.findAll()).thenReturn(List.of(
      acquirer("Cielo S/A", "Cielo", "Cielo"),
      acquirer("GETNET", "GETNET", "GETNET")
    ));

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setAcquirer(null);
    release.setDescriptionHistoricalBank("CREDITO CARTAO");
    release.setDocumentComplementNumber("000000");
    release.setComplementRelease("00002525000000000000");

    when(releasesBankRepository.findWithoutAcquirerForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementAcquirerResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(result.stillUnresolved()).isEqualTo(1);
    assertThat(release.getAcquirer()).isNull();
    verify(releasesBankRepository, never()).saveAll(any());
  }

  private AcquirerEntity acquirer(String fantasyName, String socialReason, String fileIdentifier) {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName(fantasyName);
    acquirer.setSocialReason(socialReason);
    acquirer.setFileIdentifier(fileIdentifier);
    return acquirer;
  }
}
