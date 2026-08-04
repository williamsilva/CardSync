package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.AnticipationRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre o mapeamento do Registro A (Resumo)/B (Detalhe)/C (Conta de Recebimento) do arquivo
 * CIELO15 (Negociação de Recebíveis) — ver ProcessCielo15Service#buildAnticipation. Este cliente
 * nunca negociou recebíveis com a Cielo (todo histórico real de CIELO15 é só Header+Trailer), então
 * as linhas usadas são do arquivo de teste OFICIAL da Cielo
 * (ArquivoTeste_ExtratoEletronico/CIELO15D_1234567890_20260219...TXT), não produção.
 */
class ProcessCielo15ServiceTest {

  private static final String HEADER =
    "012345678902026021920260219202602190001100CIELO15I                    01503S                                                                                                                                                                              ";
  private static final String REGISTRO_A =
    "A2602182602180000000000000000803250+0000000023845+00000000237930202602180107795211900300871                                                                                                                                                               ";
  private static final String REGISTRO_B =
    "B26021826022000000000000000007004+0000000023845+000000002379300216                                             Cielo1234567890-0000000000052                                                                                                              ";
  private static final String REGISTRO_C =
    "C00000000000000000000000000000+0000000023793                                                                                                                                                                                                              ";
  private static final String TRAILER =
    "900000000003+0000000000000000000000000000+00000000000000000+00000000000000000+00000000000023793                                                                                                                                                           ";

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final BankingDomicileResolver bankingDomicileResolver = mock(BankingDomicileResolver.class);
  private final ProcessCielo15Service service =
    new ProcessCielo15Service(lookupService, bankingDomicileResolver, null, null, null);

  @Test
  void mapsAnticipationFieldsFromRegistroB() {
    stubLookups(1234567890, "007", "Sorocred");
    ProcessCielo15Service.RegistroA registroA = new ProcessCielo15Service.RegistroA(LocalDate.of(2026, 2, 18), "02026021801077952119");

    AnticipationEntity anticipation = service.buildAnticipation(REGISTRO_B, 1, new ProcessedFileEntity(), registroA);

    assertThat(anticipation.getPvNumber()).isEqualTo(1234567890);
    assertThat(anticipation.getFlag().getName()).isEqualTo("Sorocred");
    assertThat(anticipation.getCredit()).isEqualTo("004");
    assertThat(anticipation.getOriginalDueDate()).isEqualTo(LocalDate.of(2026, 2, 20));
    assertThat(anticipation.getGrossValue()).isEqualByComparingTo(new BigDecimal("238.45"));
    assertThat(anticipation.getReleaseValue()).isEqualByComparingTo(new BigDecimal("237.93"));
    assertThat(anticipation.getDiscountRateValue()).isEqualByComparingTo(new BigDecimal("0.52"));
    assertThat(anticipation.getReleaseDate()).isEqualTo(LocalDate.of(2026, 2, 18));
    assertThat(anticipation.getNumberRvCorresponding()).isEqualTo(FileParserUtils.deriveConciliationKey("02026021801077952119"));
  }

  @Test
  void resolvesCompanyFromEstablishment() {
    CompanyEntity company = new CompanyEntity();
    company.setId(UUID.randomUUID());
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(1234567890);
    establishment.setCompany(company);
    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer());
    when(lookupService.establishmentByPvNumber(1234567890)).thenReturn(establishment);
    when(lookupService.flagByAcquirerCode(any(), any())).thenThrow(new IllegalStateException("bandeira não cadastrada"));

    ProcessCielo15Service.RegistroA registroA = new ProcessCielo15Service.RegistroA(LocalDate.of(2026, 2, 18), "02026021801077952119");
    AnticipationEntity anticipation = service.buildAnticipation(REGISTRO_B, 1, new ProcessedFileEntity(), registroA);

    assertThat(anticipation.getCompany()).isSameAs(company);
    assertThat(anticipation.getFlag()).isNull();
  }

  @Test
  void endToEndFileAppliesSameBankingDomicileToAllAnticipationsInTheSameNegotiationAndFlagsOrphanB(@TempDir Path tmpDir) throws Exception {
    stubLookups(1234567890, "007", "Sorocred");
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setId(UUID.randomUUID());
    when(bankingDomicileResolver.resolve(any(), any(), any(), any())).thenReturn(Optional.of(domicile));

    // Segunda linha B pra mesma negociação (o arquivo oficial só tem uma) — confirma que as duas
    // ficam no mesmo grupo A→B→B→C e herdam o mesmo domicílio da única linha C.
    String secondB = REGISTRO_B;
    // Linha B "órfã": sem Registro A antes (simulando fora de ordem) — depois do "9" não seria
    // realista, então simulamos reordenando: A, B, B, C, B(órfã), 9.
    String orphanB = REGISTRO_B;

    Path file = tmpDir.resolve("CIELO15D_test.TXT.txt");
    Files.write(file, List.of(HEADER, REGISTRO_A, REGISTRO_B, secondB, REGISTRO_C, orphanB, TRAILER), Charset.forName("windows-1252"));

    AnticipationRepository anticipationRepository = mock(AnticipationRepository.class);
    ProcessedFileRepository processedFileRepository = mock(ProcessedFileRepository.class);
    MoveFileService moveFileService = mock(MoveFileService.class);
    ProcessCielo15Service fileLevelService = new ProcessCielo15Service(
      lookupService, bankingDomicileResolver, moveFileService, anticipationRepository, processedFileRepository
    );

    fileLevelService.processFile(file, new FileProcessingProperties.FilePaths(), "test-hash");

    ArgumentCaptor<List<AnticipationEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(anticipationRepository).saveAll(captor.capture());
    List<AnticipationEntity> saved = captor.getValue();
    // A linha B órfã (sem Registro A anterior) é ignorada — só as 2 do grupo A→B→B→C persistem.
    assertThat(saved).hasSize(2);
    assertThat(saved).allSatisfy(a -> assertThat(a.getBankingDomicile()).isSameAs(domicile));

    ArgumentCaptor<ProcessedFileEntity> fileCaptor = ArgumentCaptor.forClass(ProcessedFileEntity.class);
    verify(processedFileRepository).save(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getErrors())
      .extracting(ProcessedFileErrorEntity::getErrorCode)
      .containsExactly("CIELO15_MISSING_NEGOTIATION");

    verify(moveFileService).moveAfterCommit(eq(file), any(), any());
  }

  private void stubLookups(int pvNumber, String flagAcquirerCode, String flagName) {
    AcquirerEntity acquirer = acquirer();
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(pvNumber);

    FlagEntity flag = new FlagEntity();
    flag.setId(UUID.randomUUID());
    flag.setName(flagName);

    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(pvNumber)).thenReturn(establishment);
    when(lookupService.flagByAcquirerCode(acquirer, flagAcquirerCode)).thenReturn(flag);
    when(lookupService.origin("CIELO")).thenReturn(new OriginFileEntity());
  }

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }
}
