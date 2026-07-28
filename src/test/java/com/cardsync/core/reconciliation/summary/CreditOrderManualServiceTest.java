package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.mapper.model.SaleSummaryModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportPreviewResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.acquirerreport.dto.AcquirerPaymentReportCsvReader;
import com.cardsync.core.file.acquirerreport.dto.AcquirerPaymentReportRow;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.HolidayRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.infrastructure.repository.spec.SaleSummarySpecs;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug "corrige só a primeira parcela faltante por chamada": antes, um
 * resumo com múltiplas parcelas ausentes exigia repetir a ação manual uma vez por parcela
 * (o loop usava break na primeira lacuna encontrada). Agora todas as lacunas são fechadas
 * numa única chamada.
 */
class CreditOrderManualServiceTest {

  private final SalesSummaryRepository salesSummaryRepository = mock(SalesSummaryRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);
  private final CreditOrderRepository creditOrderRepository = mock(CreditOrderRepository.class);
  private final SaleSummarySpecs saleSummarySpecs = mock(SaleSummarySpecs.class);
  private final SaleSummaryModelAssembler saleSummaryModelAssembler = mock(SaleSummaryModelAssembler.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);
  private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
  private final AcquirerPaymentReportCsvReader acquirerPaymentReportCsvReader = mock(AcquirerPaymentReportCsvReader.class);

  private final CreditOrderManualService service = new CreditOrderManualService(
    saleSummarySpecs, creditOrderRepository, salesSummaryRepository, transactionAcqRepository,
    saleSummaryModelAssembler, reconciliationSettingsService, holidayRepository, acquirerPaymentReportCsvReader
  );

  @Test
  void createsAllMissingInstallmentsInASingleCall() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvDate(LocalDate.now().minusMonths(6));

    when(salesSummaryRepository.findById(summaryId)).thenReturn(java.util.Optional.of(summary));
    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryId(summaryId)).thenReturn(3);
    // Só a parcela 1 existe — faltam 2 e 3.
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryId(summaryId)).thenReturn(Set.of(1));
    when(creditOrderRepository.save(any(CreditOrderEntity.class)))
      .thenAnswer(invocation -> {
        CreditOrderEntity co = invocation.getArgument(0);
        co.setId(UUID.randomUUID());
        return co;
      });

    CreditOrderManualResult result = service.create(new CreditOrderManualInput(List.of(summaryId)));

    assertThat(result.created()).isEqualTo(2);
    assertThat(result.createdIds()).hasSize(2);
    assertThat(result.skippedReasons()).isEmpty();
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.RECONCILED);
  }

  private void stubSpecsAndAssembler(List<SalesSummaryEntity> summaries, List<SaleSummaryModel> models) {
    Specification<SalesSummaryEntity> spec = mock(Specification.class);
    when(saleSummarySpecs.fromQueryForPendingCreditOrdersTotals(any(), any(), any())).thenReturn(spec);
    when(saleSummarySpecs.fromQueryForPendingCreditOrders(any(), any(), any())).thenReturn(spec);
    when(salesSummaryRepository.count(spec)).thenReturn((long) summaries.size());
    when(salesSummaryRepository.findAll(spec, Pageable.unpaged()))
      .thenReturn(new PageImpl<>(summaries));
    for (int i = 0; i < summaries.size(); i++) {
      when(saleSummaryModelAssembler.toModel(summaries.get(i))).thenReturn(models.get(i));
    }
  }

  private SaleSummaryModel modelWithLiquidValue(UUID id, BigDecimal liquidValue) {
    SaleSummaryModel model = new SaleSummaryModel();
    model.setId(id);
    model.setLiquidValue(liquidValue);
    return model;
  }

  @Test
  void previewsNextInstallmentValueSplitEquallyAcrossInstallments() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    SaleSummaryModel model = modelWithLiquidValue(summaryId, new BigDecimal("300.00"));
    stubSpecsAndAssembler(List.of(summary), List.of(model));

    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(List.of(summaryId)))
      .thenReturn(List.<Object[]>of(new Object[] { summaryId, 3 }));

    var page = service.searchPendingSummaries(Pageable.unpaged(), null);

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().getFirst().getNextInstallmentValue()).isEqualByComparingTo("100.00");
  }

  /**
   * Caso real do RV 56649219: parcelas 1 e 3 existem, falta a 2 — a data prevista da próxima
   * ordem deve ser a da parcela 2 (baseDate + 1 mês), não a da 1 nem a da 3, e já ajustada pro
   * dia útil (04/06/2026 é feriado — Corpus Christi — dia útil real é 03/06).
   */
  @Test
  void previewsNextInstallmentDateForTheFirstMissingInstallmentNumber() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setFirstInstallmentCreditDate(LocalDate.of(2026, 5, 4));
    SaleSummaryModel model = modelWithLiquidValue(summaryId, new BigDecimal("300.00"));
    stubSpecsAndAssembler(List.of(summary), List.of(model));

    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(List.of(summaryId)))
      .thenReturn(List.<Object[]>of(new Object[] { summaryId, 3 }));
    // Parcelas 1 e 3 já existem — falta a 2.
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summaryId)))
      .thenReturn(List.<Object[]>of(new Object[] { summaryId, 1 }, new Object[] { summaryId, 3 }));
    when(holidayRepository.findActiveByDate(LocalDate.of(2026, 6, 4)))
      .thenReturn(List.of(new com.cardsync.domain.model.HolidayEntity()));
    when(holidayRepository.findActiveByDate(LocalDate.of(2026, 6, 3))).thenReturn(List.of());

    var page = service.searchPendingSummaries(Pageable.unpaged(), null);

    assertThat(page.getContent().getFirst().getNextInstallmentDate()).isEqualTo(LocalDate.of(2026, 6, 3));
  }

  @Test
  void previewsFullLiquidValueWhenSummaryHasNoTransactionsYet() {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    SaleSummaryModel model = modelWithLiquidValue(summaryId, new BigDecimal("150.50"));
    stubSpecsAndAssembler(List.of(summary), List.of(model));

    // Nenhuma transação ainda — não aparece no resultado agrupado, deve assumir installmentTotal=1.
    when(transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(List.of(summaryId)))
      .thenReturn(List.<Object[]>of());

    var page = service.searchPendingSummaries(Pageable.unpaged(), null);

    assertThat(page.getContent().getFirst().getNextInstallmentValue()).isEqualByComparingTo("150.50");
  }

  /**
   * Casos reais dos RVs 56649219/38949474: antes desse ajuste, a data prevista da parcela saía
   * 1 dia a mais que a data real da adquirente sempre que a data "redonda" (baseDate + N meses)
   * caía em fim de semana ou feriado cadastrado.
   */
  @Test
  void adjustsToPreviousBusinessDayWhenLandingOnWeekend() {
    when(holidayRepository.findActiveByDate(any())).thenReturn(List.of());

    // 04/07/2026 é sábado (dado real da adquirente: parcela liquidada em 03/07, sexta).
    LocalDate adjusted = service.adjustToPreviousBusinessDay(LocalDate.of(2026, 7, 4));

    assertThat(adjusted).isEqualTo(LocalDate.of(2026, 7, 3));
  }

  @Test
  void adjustsToPreviousBusinessDayWhenLandingOnRegisteredHoliday() {
    LocalDate corpusChristi2026 = LocalDate.of(2026, 6, 4);
    when(holidayRepository.findActiveByDate(corpusChristi2026))
      .thenReturn(List.of(new com.cardsync.domain.model.HolidayEntity()));
    when(holidayRepository.findActiveByDate(LocalDate.of(2026, 6, 3))).thenReturn(List.of());

    // 04/06/2026 não é fim de semana, mas está cadastrado como feriado (Corpus Christi).
    LocalDate adjusted = service.adjustToPreviousBusinessDay(corpusChristi2026);

    assertThat(adjusted).isEqualTo(LocalDate.of(2026, 6, 3));
  }

  @Test
  void keepsDateUnchangedWhenAlreadyABusinessDay() {
    when(holidayRepository.findActiveByDate(any())).thenReturn(List.of());

    LocalDate adjusted = service.adjustToPreviousBusinessDay(LocalDate.of(2026, 5, 4));

    assertThat(adjusted).isEqualTo(LocalDate.of(2026, 5, 4));
  }

  private AcquirerPaymentReportRow importRow(int lineNumber, Integer rvNumber, Integer pvNumber,
      Integer installmentNumber, Integer installmentTotal, LocalDate releaseDate, BigDecimal releaseValue) {
    return new AcquirerPaymentReportRow(
      "pagamentos.csv", lineNumber, rvNumber, pvNumber, installmentNumber, installmentTotal,
      releaseDate, releaseDate, releaseValue, releaseValue, BigDecimal.ZERO, "paga"
    );
  }

  /**
   * Regra confirmada com o usuário para a importação em lote: mesma elegibilidade da criação
   * manual (só gera para parcela ainda ausente), mas usando os valores REAIS do arquivo em vez
   * da fórmula de aproximação (releaseDate/releaseValue vêm direto da linha do CSV).
   */
  @Test
  void createsOrderFromImportRowUsingRealFileValuesForMissingInstallment() throws Exception {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvNumber(38949474);
    summary.setPvNumber(12345);

    AcquirerPaymentReportRow row = importRow(2, 38949474, 12345, 2, 3, LocalDate.of(2026, 6, 3), new BigDecimal("117.30"));

    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(row));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(38949474))).thenReturn(List.of(summary));
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summaryId))).thenReturn(List.of());
    when(creditOrderRepository.save(any(CreditOrderEntity.class))).thenAnswer(invocation -> {
      CreditOrderEntity co = invocation.getArgument(0);
      co.setId(UUID.randomUUID());
      return co;
    });

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.analyzed()).isEqualTo(1);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.skipped()).isZero();

    var saved = org.mockito.ArgumentCaptor.forClass(CreditOrderEntity.class);
    org.mockito.Mockito.verify(creditOrderRepository).save(saved.capture());
    assertThat(saved.getValue().getReleaseDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    assertThat(saved.getValue().getReleaseValue()).isEqualByComparingTo("117.30");
    assertThat(saved.getValue().getInstallmentNumber()).isEqualTo(2);
  }

  @Test
  void skipsImportRowWhenSummaryNotFound() throws Exception {
    AcquirerPaymentReportRow row = importRow(2, 99999999, 12345, 1, 1, LocalDate.now(), BigDecimal.TEN);
    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(row));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(99999999))).thenReturn(List.of());

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.created()).isZero();
    assertThat(result.skippedReasons()).hasSize(1);
    assertThat(result.skippedReasons().getFirst().code()).isEqualTo("SUMMARY_NOT_FOUND");
  }

  /**
   * Interpretação da regra do usuário ("seguir a mesma regra da tela"): a tela/criação manual
   * NUNCA sobrescreve uma ordem já existente, só gera para parcela ausente — o import segue a
   * mesma regra, então uma parcela do arquivo que já tem ordem é apenas ignorada e reportada.
   */
  @Test
  void skipsImportRowWhenInstallmentAlreadyHasCreditOrder() throws Exception {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvNumber(38949474);

    AcquirerPaymentReportRow row = importRow(2, 38949474, null, 1, 3, LocalDate.now(), BigDecimal.TEN);
    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(row));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(38949474))).thenReturn(List.of(summary));
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summaryId)))
      .thenReturn(List.<Object[]>of(new Object[] { summaryId, 1 }));

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.created()).isZero();
    assertThat(result.skippedReasons()).hasSize(1);
    assertThat(result.skippedReasons().getFirst().code()).isEqualTo("ALREADY_HAS_CREDIT_ORDER");
    org.mockito.Mockito.verify(creditOrderRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void skipsImportRowAsAmbiguousWhenRvNumberMatchesMultipleSummariesAndPvDoesNotDisambiguate() throws Exception {
    UUID summary1Id = UUID.randomUUID();
    UUID summary2Id = UUID.randomUUID();
    SalesSummaryEntity summary1 = new SalesSummaryEntity();
    summary1.setId(summary1Id);
    summary1.setRvNumber(38949474);
    summary1.setPvNumber(111);
    SalesSummaryEntity summary2 = new SalesSummaryEntity();
    summary2.setId(summary2Id);
    summary2.setRvNumber(38949474);
    summary2.setPvNumber(222);

    // pvNumber do arquivo não bate com nenhum dos dois candidatos.
    AcquirerPaymentReportRow row = importRow(2, 38949474, 333, 1, 1, LocalDate.now(), BigDecimal.TEN);
    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(row));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(38949474))).thenReturn(List.of(summary1, summary2));

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.created()).isZero();
    assertThat(result.skippedReasons().getFirst().code()).isEqualTo("AMBIGUOUS_RV");
  }

  /**
   * Caso real reportado pelo usuário: a conciliação bancária não fechava porque os RVs 54949685
   * (parcela 6) e 64749688 (parcela 4) vinham em DUAS linhas no relatório da adquirente (parte
   * antecipada + restante liquidado). A primeira versão da importação processava só a primeira
   * linha de cada par e descartava a segunda como "já tem ordem de crédito", subestimando o
   * valor real da parcela em exatamente R$ 257,16 no total.
   */
  @Test
  void sumsDuplicateFileLinesForTheSameRvAndInstallmentInsteadOfSkippingTheSecondOne() throws Exception {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvNumber(54949685);

    AcquirerPaymentReportRow firstLine = importRow(10, 54949685, null, 6, 10, LocalDate.of(2026, 6, 3), new BigDecimal("144.16"));
    AcquirerPaymentReportRow secondLine = importRow(11, 54949685, null, 6, 10, LocalDate.of(2026, 6, 3), new BigDecimal("89.63"));

    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(firstLine, secondLine));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(54949685))).thenReturn(List.of(summary));
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summaryId))).thenReturn(List.of());
    when(creditOrderRepository.save(any(CreditOrderEntity.class))).thenAnswer(invocation -> {
      CreditOrderEntity co = invocation.getArgument(0);
      co.setId(UUID.randomUUID());
      return co;
    });

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.analyzed()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.skipped()).isZero();

    var saved = org.mockito.ArgumentCaptor.forClass(CreditOrderEntity.class);
    org.mockito.Mockito.verify(creditOrderRepository).save(saved.capture());
    assertThat(saved.getValue().getReleaseValue()).isEqualByComparingTo("233.79");
  }

  /**
   * Vários arquivos podem ser selecionados e importados juntos numa única chamada — as linhas de
   * TODOS os arquivos são somadas/analisadas em conjunto, inclusive o merge de RV+parcela
   * duplicado entre arquivos diferentes (não só duplicado dentro do mesmo arquivo).
   */
  @Test
  void importsMultipleFilesTogetherMergingDuplicateInstallmentsAcrossFiles() throws Exception {
    UUID summaryId = UUID.randomUUID();
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setId(summaryId);
    summary.setRvNumber(54949685);

    AcquirerPaymentReportRow lineInFirstFile = new AcquirerPaymentReportRow(
      "arquivo1.csv", 5, 54949685, null, 6, 10,
      LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3), new BigDecimal("144.16"),
      new BigDecimal("144.16"), BigDecimal.ZERO, "paga"
    );
    AcquirerPaymentReportRow lineInSecondFile = new AcquirerPaymentReportRow(
      "arquivo2.csv", 8, 54949685, null, 6, 10,
      LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3), new BigDecimal("89.63"),
      new BigDecimal("89.63"), BigDecimal.ZERO, "paga"
    );

    MultipartFile file1 = mock(MultipartFile.class);
    MultipartFile file2 = mock(MultipartFile.class);
    when(file1.getOriginalFilename()).thenReturn("arquivo1.csv");
    when(file2.getOriginalFilename()).thenReturn("arquivo2.csv");
    when(acquirerPaymentReportCsvReader.read(file1)).thenReturn(List.of(lineInFirstFile));
    when(acquirerPaymentReportCsvReader.read(file2)).thenReturn(List.of(lineInSecondFile));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(54949685))).thenReturn(List.of(summary));
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summaryId))).thenReturn(List.of());
    when(creditOrderRepository.save(any(CreditOrderEntity.class))).thenAnswer(invocation -> {
      CreditOrderEntity co = invocation.getArgument(0);
      co.setId(UUID.randomUUID());
      return co;
    });

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { file1, file2 });

    assertThat(result.analyzed()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(1);

    var saved = org.mockito.ArgumentCaptor.forClass(CreditOrderEntity.class);
    org.mockito.Mockito.verify(creditOrderRepository).save(saved.capture());
    assertThat(saved.getValue().getReleaseValue()).isEqualByComparingTo("233.79");
  }

  /**
   * A tela agora mostra uma prévia (analisadas/seriam criadas/valor total/ignoradas) antes do
   * usuário confirmar a importação — a prévia precisa refletir a MESMA análise de elegibilidade
   * do import de fato, mas sem gravar nada no banco.
   */
  @Test
  void previewDoesNotPersistAnythingButReflectsWhatWouldBeCreated() throws Exception {
    UUID summary1Id = UUID.randomUUID();
    SalesSummaryEntity summary1 = new SalesSummaryEntity();
    summary1.setId(summary1Id);
    summary1.setRvNumber(38949474);

    AcquirerPaymentReportRow eligibleRow = importRow(2, 38949474, null, 1, 3, LocalDate.now(), new BigDecimal("100.00"));
    AcquirerPaymentReportRow notFoundRow = importRow(3, 99999999, null, 1, 1, LocalDate.now(), BigDecimal.TEN);

    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(eligibleRow, notFoundRow));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(38949474, 99999999)))
      .thenReturn(List.of(summary1));
    when(creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(List.of(summary1Id))).thenReturn(List.of());

    var file = new MockMultipartFile("files", "pagamentos.csv", "text/csv", new byte[0]);
    CreditOrderImportPreviewResult preview = service.previewAcquirerReportImport(new MultipartFile[] { file });

    assertThat(preview.fileNames()).containsExactly("pagamentos.csv");
    assertThat(preview.analyzed()).isEqualTo(2);
    assertThat(preview.wouldCreate()).isEqualTo(1);
    assertThat(preview.totalValue()).isEqualByComparingTo("100.00");
    assertThat(preview.skipped()).isEqualTo(1);
    assertThat(preview.skippedReasons().getFirst().code()).isEqualTo("SUMMARY_NOT_FOUND");

    org.mockito.Mockito.verify(creditOrderRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void skipsImportRowWithParseErrorWhenRequiredFieldIsMissing() throws Exception {
    AcquirerPaymentReportRow row = importRow(2, 38949474, 12345, null, 1, LocalDate.now(), BigDecimal.TEN);
    when(acquirerPaymentReportCsvReader.read(any())).thenReturn(List.of(row));
    when(salesSummaryRepository.findByRvNumberIn(Set.of(38949474))).thenReturn(List.of());

    CreditOrderImportResult result = service.importFromAcquirerReport(new MultipartFile[] { mock(MultipartFile.class) });

    assertThat(result.skippedReasons().getFirst().code()).isEqualTo("PARSE_ERROR");
  }
}
