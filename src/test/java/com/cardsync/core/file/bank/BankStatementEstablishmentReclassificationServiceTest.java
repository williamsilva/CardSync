package com.cardsync.core.file.bank;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o backfill que vincula o estabelecimento de lançamentos bancários de recebimento já
 * importados sem estabelecimento resolvido — causado pelo bug de extractPvCandidates que não
 * reconhecia PV colado direto a um marcador de texto sem separador (ver
 * BankTextSignalResolverTest / correção do NUMBER_PATTERN).
 */
class BankStatementEstablishmentReclassificationServiceTest {

  private final ReleasesBankRepository releasesBankRepository = mock(ReleasesBankRepository.class);
  private final EstablishmentRepository establishmentRepository = mock(EstablishmentRepository.class);
  private final AcquirerRepository acquirerRepository = mock(AcquirerRepository.class);
  private final FlagRepository flagRepository = mock(FlagRepository.class);
  private final BankTextSignalResolver textSignalResolver = new BankTextSignalResolver();
  private final BankStatementClassifierService classifierService = new BankStatementClassifierService(
    flagRepository, acquirerRepository, textSignalResolver, null, establishmentRepository
  );
  private final BankStatementEstablishmentReclassificationService service =
    new BankStatementEstablishmentReclassificationService(
      releasesBankRepository, classifierService, textSignalResolver
    );

  @Test
  void resolvesEstablishmentFromPvGluedDirectlyToTextMarker() {
    AcquirerEntity rede = new AcquirerEntity();
    rede.setId(UUID.randomUUID());

    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setId(UUID.randomUUID());
    establishment.setPvNumber(7866470);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setAcquirer(rede);
    release.setDescriptionHistoricalBank("REDE   MAST CD0007866470");

    when(establishmentRepository.findFirstByPvNumberAndAcquirer_Id(7866470, rede.getId()))
      .thenReturn(Optional.of(establishment));
    when(releasesBankRepository.findWithoutEstablishmentForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementEstablishmentResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.stillUnresolved()).isZero();
    assertThat(release.getEstablishment()).isSameAs(establishment);
    verify(releasesBankRepository).saveAll(List.of(release));
  }

  @Test
  void resolvesEstablishmentBySuffixWhenSantanderTruncatesPvToSixDigits() {
    AcquirerEntity rede = new AcquirerEntity();
    rede.setId(UUID.randomUUID());

    // PV real cadastrado (7867379), mas o Santander só grava os últimos 6 dígitos no documento
    // ("867379REDE-VISA") — não sobra em nenhum outro campo do CNAB.
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setId(UUID.randomUUID());
    establishment.setPvNumber(7867379);

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setAcquirer(rede);
    release.setDocumentComplementNumber("867379REDE-VISA");

    when(establishmentRepository.findFirstByPvNumberAndAcquirer_Id(867379, rede.getId()))
      .thenReturn(Optional.empty());
    when(establishmentRepository.findByPvNumberSuffixAndAcquirerId("867379", rede.getId()))
      .thenReturn(List.of(establishment));
    when(releasesBankRepository.findWithoutEstablishmentForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementEstablishmentResult result = service.reclassifyAll();

    assertThat(result.updated()).isEqualTo(1);
    assertThat(release.getEstablishment()).isSameAs(establishment);
  }

  @Test
  void doesNotGuessEstablishmentWhenSuffixMatchesMoreThanOne() {
    AcquirerEntity rede = new AcquirerEntity();
    rede.setId(UUID.randomUUID());

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setAcquirer(rede);
    release.setDocumentComplementNumber("867379REDE-VISA");

    when(establishmentRepository.findFirstByPvNumberAndAcquirer_Id(867379, rede.getId()))
      .thenReturn(Optional.empty());
    // Ambíguo: dois estabelecimentos diferentes batem no mesmo sufixo de 6 dígitos.
    when(establishmentRepository.findByPvNumberSuffixAndAcquirerId("867379", rede.getId()))
      .thenReturn(List.of(new EstablishmentEntity(), new EstablishmentEntity()));
    when(releasesBankRepository.findWithoutEstablishmentForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementEstablishmentResult result = service.reclassifyAll();

    assertThat(result.updated()).isZero();
    assertThat(result.stillUnresolved()).isEqualTo(1);
    assertThat(release.getEstablishment()).isNull();
  }

  @Test
  void leavesReleaseWithoutAnyPvCandidateUnresolvedAndDoesNotSave() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setId(UUID.randomUUID());
    release.setDescriptionHistoricalBank("PIX TRANSF  Mac Ser02/10");

    when(releasesBankRepository.findWithoutEstablishmentForReclassification(eq(ReleaseCategoryEnum.RECEIPT.getCode())))
      .thenReturn(List.of(release));

    ReclassifyBankStatementEstablishmentResult result = service.reclassifyAll();

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(result.stillUnresolved()).isEqualTo(1);
    assertThat(release.getEstablishment()).isNull();
    verify(releasesBankRepository, never()).saveAll(any());
  }
}
