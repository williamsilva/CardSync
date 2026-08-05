package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre o mapeamento do Registro D (UR Agenda) + Registro E (mesmo layout do CIELO03, mas em
 * visão de pagamento) do arquivo CIELO04 (Liquidação/Pagamento) para CreditOrderEntity — ver
 * ProcessCielo04Service#buildCreditOrder. As linhas usadas são recortes reais de um extrato Cielo
 * (PV 1051583117, arquivo de 2026-08-03, autorização 437940) — a mesma venda (cartão
 * 498407******3786) validada manualmente no CIELO03 do dia 2026-07-04 durante o planejamento
 * desta fase, confirmando que a "Chave UR" é estável entre captura e pagamento.
 */
class ProcessCielo04ServiceTest {

  private static final String HEADER =
    "010515831172026080320260803202608030001265CIELO04I                    01503N                                                                                                                                                                              ";

  // Registro D (UR Agenda) real correspondente ao Registro E abaixo (mesma Chave UR + Tipo de lançamento "02").
  private static final String REGISTRO_D =
    "D1051583117360338010001093603380100010936033801000109001002105158311703+0000000013211-0000000000328+000000001288303418639000000000000000024515100000102360338010001092026-08-0300100072000021051583117360338010001090000000000000000000                    02000000000000000308202631072026030820261051583117NNN00000000000000                                                                                  ";

  // Registro E - pagamento de venda crédito (Tipo de lançamento "02"), autorização 437940.
  private static final String REGISTRO_E =
    "E1051583117001002000043794002360338010001092026-08-0300100072000021051583117360338010001090000000000000000000                    2607030210080058389       040NNN3NNN4984073786046002000000000010515831175JVVROVBAE         CLOUD112461002480000000248+0000000013211+0000000013211+0000000012883-0000000000328+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+00000000000002116440136033801000109001618470748481898618470748481898               0071170183002002000000036030720260407202603072026030720260260703                         03082026105158311700NNN03418639000000000000000024515124487686185436000665076N1000000000000000                                      ";

  private static final String TRAILER =
    "900000000002+0000000000001288300000000001+00000000000013211-00000000000000000-00000000000000000                                                                                                                                                           ";

  // Segmento E - Contestação do portador do cartão / chargeback (Tipo de lançamento "08"), lado
  // do PAGAMENTO — mesmo layout e mesma linha real usada em ProcessCielo03ServiceTest (o Registro
  // E é compartilhado entre CIELO03/04, ver ProcessCielo04Service#buildAdjustment).
  private static final String CONTESTACAO =
    "E105158311700200200000364320801027058000191360338010001092025-08-18002720021051583117360338010001090000000000000000000           2508060810410003119   0301010NNNNNNN5536360291351012000000000010515831174N83659MJE       1752716083445002480000000248-0000000001508-0000000001508-0000000001471+0000000000037+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000-0000000001508-0000000001508+0000000000000+0000000000000+0000000000000+00000000000003010136033801000109002000000000000000000000000000000               00711701830080000000000001607202506082025060820251607202502507162507160210360071837      18082025105158311700NNN034186390000000000000000245151                       N8100000000000000";

  private static final ProcessCielo04Service.RegistroD REGISTRO_D_PARSED = new ProcessCielo04Service.RegistroD(
    "0341", 86390, 245151, LocalDate.of(2026, 8, 3), 2, 3
  );

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final BankingDomicileResolver bankingDomicileResolver = mock(BankingDomicileResolver.class);
  // Mockito responde Optional.empty() por padrão pra métodos não stubados que retornam Optional —
  // não precisa de when(...) explícito aqui pra safeSalesSummary funcionar sem NPE.
  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final ProcessCielo04Service service =
    new ProcessCielo04Service(lookupService, bankingDomicileResolver, null, null, salesSummaryRepository, null, null, null);

  @Test
  void mapsCreditOrderFieldsFromRealPaymentLine() {
    stubLookups(1051583117, "001", null);
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setId(UUID.randomUUID());
    when(bankingDomicileResolver.resolve(eq("0341"), eq(86390), eq(245151), any())).thenReturn(Optional.of(domicile));

    String chaveUR = chaveUR(REGISTRO_E);
    CreditOrderEntity order = service.buildCreditOrder(
      REGISTRO_E, 1, processedFileWithDate(LocalDate.of(2026, 8, 3)), "02", chaveUR, REGISTRO_D_PARSED
    );

    assertThat(order.getPvCentralizer()).isEqualTo(1051583117);
    assertThat(order.getRvNumber()).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR));
    assertThat(order.getInstallmentNumber()).isEqualTo(1);
    // Achado real: pra venda não parcelada ("02"), o campo "número total de parcelas" da linha
    // real vem "00", não "01" — sem normalizar, a tela mostrava "1 / 0" (bug real reportado).
    assertThat(order.getInstallmentTotal()).isEqualTo(1);
    assertThat(order.getReleaseValue()).isEqualByComparingTo(new BigDecimal("128.83"));
    assertThat(order.getGrossRvValue()).isEqualByComparingTo(new BigDecimal("132.11"));
    assertThat(order.getDiscountRateValue()).isEqualByComparingTo(new BigDecimal("3.28"));
    assertThat(order.getReleaseDate()).isEqualTo(LocalDate.of(2026, 8, 3));
    assertThat(order.getCreditOrderDate()).isEqualTo(LocalDate.of(2026, 8, 3));
    assertThat(order.getTransactionType()).isEqualTo(2);
    assertThat(order.getCreditStatus()).isEqualTo(3);
    assertThat(order.getLaunchType()).isEqualTo("02");
    assertThat(order.getBankingDomicile()).isSameAs(domicile);

    OffsetDateTime expectedSaleDate = FileParserUtils.extractOffsetDateTimeLine(REGISTRO_E, 1, "565-573", "470-476");
    assertThat(order.getRvDate()).isEqualTo(expectedSaleDate.toLocalDate());
  }

  @Test
  void disambiguatesSalesSummaryByValueWhenSameRvNumberMatchesMultipleSummaries() {
    // Achado real: acquirer+pv+rvNumber achou 3 SalesSummary diferentes pro mesmo rv (Chave UR é
    // chave de lote, não por venda) — "pegar a mais recente" (comportamento antigo) colava o
    // CreditOrder na venda errada. Agora só vincula quando o valor bate com exatamente uma.
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(1051583117);
    establishment.setCompany(new CompanyEntity());
    when(lookupService.origin("CIELO")).thenReturn(new OriginFileEntity());
    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(1051583117)).thenReturn(establishment);

    Integer rvNumber = FileParserUtils.deriveConciliationKey(chaveUR(REGISTRO_E));
    SalesSummaryEntity wrongSummaryA = new SalesSummaryEntity();
    wrongSummaryA.setLiquidValue(new BigDecimal("169.76"));
    SalesSummaryEntity wrongSummaryB = new SalesSummaryEntity();
    wrongSummaryB.setLiquidValue(new BigDecimal("158.00"));
    SalesSummaryEntity correctSummary = new SalesSummaryEntity();
    correctSummary.setLiquidValue(new BigDecimal("128.83")); // mesmo valor de REGISTRO_E, ver mapsCreditOrderFieldsFromRealPaymentLine
    when(salesSummaryRepository.findByAcquirer_IdAndPvNumberAndRvNumber(acquirer.getId(), 1051583117, rvNumber))
      .thenReturn(List.of(wrongSummaryA, wrongSummaryB, correctSummary));

    CreditOrderEntity order = service.buildCreditOrder(
      REGISTRO_E, 1, processedFileWithDate(LocalDate.of(2026, 8, 3)), "02", chaveUR(REGISTRO_E), REGISTRO_D_PARSED
    );

    assertThat(order.getSalesSummary()).isSameAs(correctSummary);
  }

  @Test
  void leavesSalesSummaryNullWhenMultipleCandidatesShareTheSameRvNumberAndNoneMatchesByValue() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(1051583117);
    establishment.setCompany(new CompanyEntity());
    when(lookupService.origin("CIELO")).thenReturn(new OriginFileEntity());
    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(1051583117)).thenReturn(establishment);

    Integer rvNumber = FileParserUtils.deriveConciliationKey(chaveUR(REGISTRO_E));
    SalesSummaryEntity wrongSummaryA = new SalesSummaryEntity();
    wrongSummaryA.setLiquidValue(new BigDecimal("169.76"));
    SalesSummaryEntity wrongSummaryB = new SalesSummaryEntity();
    wrongSummaryB.setLiquidValue(new BigDecimal("158.00"));
    when(salesSummaryRepository.findByAcquirer_IdAndPvNumberAndRvNumber(acquirer.getId(), 1051583117, rvNumber))
      .thenReturn(List.of(wrongSummaryA, wrongSummaryB));

    CreditOrderEntity order = service.buildCreditOrder(
      REGISTRO_E, 1, processedFileWithDate(LocalDate.of(2026, 8, 3)), "02", chaveUR(REGISTRO_E), REGISTRO_D_PARSED
    );

    assertThat(order.getSalesSummary()).isNull();
  }

  @Test
  void flagStaysNullWhenAcquirerCodeIsNotMapped() {
    // Código "001" não tem bandeira descrita na Tabela III do manual (campo reservado) —
    // flagByAcquirerCode lança "não encontrada" e safeFlag precisa engolir isso sem quebrar.
    stubLookups(1051583117, "001", null);
    when(lookupService.flagByAcquirerCode(any(), eq("001"))).thenThrow(new IllegalStateException("bandeira não cadastrada"));

    CreditOrderEntity order = service.buildCreditOrder(
      REGISTRO_E, 1, processedFileWithDate(LocalDate.of(2026, 8, 3)), "02", chaveUR(REGISTRO_E), REGISTRO_D_PARSED
    );

    assertThat(order.getFlag()).isNull();
  }

  @Test
  void resolvesBankingDomicileByStrippingTrailingAgencyDigitWhenRawAgencyDoesNotMatch() {
    // Achado real: agência da Cielo vem com 5 dígitos ("86390"), mas o domicílio costuma estar
    // cadastrado só com os 4 primeiros ("8639", sem dígito separado como a conta tem) — o
    // BankingDomicileResolver só sabe separar dígito verificador da CONTA, não da agência.
    stubLookups(1051583117, "001", null);
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setId(UUID.randomUUID());
    when(bankingDomicileResolver.resolve(eq("0341"), eq(86390), eq(245151), any())).thenReturn(Optional.empty());
    when(bankingDomicileResolver.resolve(eq("0341"), eq(8639), eq(245151), any())).thenReturn(Optional.of(domicile));

    CreditOrderEntity order = service.buildCreditOrder(
      REGISTRO_E, 1, processedFileWithDate(LocalDate.of(2026, 8, 3)), "02", chaveUR(REGISTRO_E), REGISTRO_D_PARSED
    );

    assertThat(order.getBankingDomicile()).isSameAs(domicile);
  }

  @Test
  void mapsChargebackAdjustmentFromPaymentFileView() {
    stubLookups(1051583117, "001", null);

    AdjustmentEntity adjustment = service.buildAdjustment(CONTESTACAO, 1, new ProcessedFileEntity(), "08");

    assertThat(adjustment.getRecordType()).isEqualTo("08");
    assertThat(adjustment.getNsu()).isEqualTo(351012L);
    assertThat(adjustment.getAuthorization()).isEqualTo("036432");
    assertThat(adjustment.getAdjustmentValue()).isEqualByComparingTo(new BigDecimal("-15.08"));
    assertThat(adjustment.getRawAdjustmentCode()).isEqualTo("0301");
    assertThat(adjustment.getAdjustmentDescription()).isEqualTo("Venda contestada pelo banco a pedido do portador do cartão");
    assertThat(adjustment.getAdjustmentType()).isEqualTo("CIELO_CHARGEBACK");
    assertThat(adjustment.getCancellationValueRequested()).isEqualByComparingTo(new BigDecimal("15.08"));
  }

  @Test
  void endToEndFileLinksSaleLineToItsUrAgendaAndSkipsUnsupportedOrUnmatchedLinesWithoutBreakingTheFile(@TempDir Path tmpDir) throws Exception {
    stubLookups(1051583117, "001", null);
    when(bankingDomicileResolver.resolve(any(), any(), any(), any())).thenReturn(Optional.empty());

    // "06" (Cancelamento de venda) — código da Tabela II sem nenhuma ocorrência real no
    // histórico completo do cliente (ao contrário de "04"/"05"/"08"/"10", que agora são
    // suportados via buildAdjustment), continua genuinamente fora de escopo.
    String unsupportedLaunchType = REGISTRO_E.substring(0, 27) + "06" + REGISTRO_E.substring(29);
    String unmatchedUr = REGISTRO_E.substring(0, 29) + "9".repeat(100) + REGISTRO_E.substring(129);

    Path file = tmpDir.resolve("CIELO04D_test.TXT.txt");
    Files.write(file, List.of(HEADER, REGISTRO_D, REGISTRO_E, unsupportedLaunchType, unmatchedUr, TRAILER), Charset.forName("windows-1252"));

    CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
    ProcessedFileRepository processedFileRepository = mock(ProcessedFileRepository.class);
    MoveFileService moveFileService = mock(MoveFileService.class);
    SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
    when(salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(any(), any(), any()))
      .thenReturn(Optional.empty());
    AdjustmentRepository adjustmentRepository = mock(AdjustmentRepository.class);
    AdjustmentTransactionLinkService adjustmentTransactionLinkService = mock(AdjustmentTransactionLinkService.class);
    ProcessCielo04Service fileLevelService = new ProcessCielo04Service(
      lookupService, bankingDomicileResolver, moveFileService, creditOrderRepository, salesSummaryRepository, processedFileRepository,
      adjustmentRepository, adjustmentTransactionLinkService
    );

    FileProcessingProperties.FilePaths paths = new FileProcessingProperties.FilePaths();
    fileLevelService.processFile(file, paths, "test-hash");

    ArgumentCaptor<List<CreditOrderEntity>> ordersCaptor = ArgumentCaptor.forClass(List.class);
    verify(creditOrderRepository).saveAll(ordersCaptor.capture());
    assertThat(ordersCaptor.getValue()).hasSize(1);
    assertThat(ordersCaptor.getValue().get(0).getRvNumber()).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR(REGISTRO_E)));

    ArgumentCaptor<ProcessedFileEntity> fileCaptor = ArgumentCaptor.forClass(ProcessedFileEntity.class);
    verify(processedFileRepository).save(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getWarningLines()).isEqualTo(2);
    assertThat(fileCaptor.getValue().getErrors()).hasSize(2);
    assertThat(fileCaptor.getValue().getErrors())
      .extracting(ProcessedFileErrorEntity::getErrorCode)
      .containsExactlyInAnyOrder("CIELO04_UNSUPPORTED_LAUNCH_TYPE", "CIELO04_MISSING_UR_AGENDA");

    verify(moveFileService).moveAfterCommit(eq(file), any(), any());
    verify(moveFileService, never()).moveAfterRollback(any(), any(), any());
  }

  private void stubLookups(int pvNumber, String flagAcquirerCode, String unusedFlagName) {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");

    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(pvNumber);
    CompanyEntity company = new CompanyEntity();
    company.setId(UUID.randomUUID());
    establishment.setCompany(company);

    when(lookupService.origin("CIELO")).thenReturn(new OriginFileEntity());
    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(pvNumber)).thenReturn(establishment);
  }

  private ProcessedFileEntity processedFileWithDate(LocalDate dateFile) {
    ProcessedFileEntity processedFile = new ProcessedFileEntity();
    processedFile.setDateFile(dateFile);
    return processedFile;
  }

  /** Extrai a "Chave UR" (posição 30-129 do manual, 1-based) igual ao que buildCreditOrder faz internamente. */
  private String chaveUR(String line) {
    return line.substring(29, 129).trim();
  }
}
