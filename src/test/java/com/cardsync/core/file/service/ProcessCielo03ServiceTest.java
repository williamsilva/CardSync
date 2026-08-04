package com.cardsync.core.file.service;

import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o mapeamento do Registro E (Detalhe do Lançamento) do arquivo CIELO03 (Captura/Previsão
 * de vendas) para TransactionAcqEntity — ver ProcessCielo03Service#buildTransaction. As linhas
 * usadas são recortes reais de extratos Cielo (PV 1051583117/1018802468, arquivos de
 * 2026-03) e cobrem os três tipos de lançamento de venda (débito/crédito/parcelada — 94,7% dos
 * registros reais amostrados).
 */
class ProcessCielo03ServiceTest {

  // Segmento E - venda débito (Tipo de lançamento "01").
  private static final String DETAIL_DEBITO =
    "E101880246800700100000547060101027058000191360338010001092026-03-09007210011018802468360338010001090000000000000000000           2603070110290549254       071NNN3NNN65501580385000020000000000                                        002150000000215+0000000024020+0000000024020+0000000023504-0000000000516+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+00000000000000925570136033801000109007606623923731581606623923731581               0084221671701001000000012070320260703202607032026070320265260307                         09032026101880246800NNN03418639000000000000000024515120083646066419005492542N8300000000000000";

  // Segmento E - venda crédito à vista (Tipo de lançamento "02"), e-commerce (TID preenchido).
  private static final String DETAIL_CREDITO =
    "E105158311700100200008856180201027058000191360338010001092026-04-01001720021051583117360338010001090000000000000000000           2603010210060116066       040NNN3NNN4078382981005032000000000010515831175GC8QCTG4E         CLOUD108000002480000000248+0000000017170+0000000017170+0000000016744-0000000000426+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+00000000000001801240136033801000109001606070494976516606070494976516               0071170183002002000000036010320260103202601032026010320260260301                         01042026105158311700NNN03418639000000000000000024515124487686060483001323990N1000000000000000";

  // Segmento E - venda parcelada (Tipo de lançamento "03"), parcela 1 de 2.
  private static final String DETAIL_PARCELADA =
    "E105158311700200201024503590301027058000191360338010001092026-04-06002720021051583117360338010001090000000000000000000           2603060310490056302       012NNN3NNN5346968665010002000000000010515831175GGU4BSSRE         CLOUD108024003150000000315+0000000016225+0000000008113+0000000007857-0000000000256+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+00000000000001009450136033801000109002606570505039259606570505039259               0071170183003003000000036060320260603202606032026060320264260306                         06042026105158311700NNN03418639000000000000000024515155502596065549573060532N8100000000000000";

  // Segmento E - Ajuste a débito (Tipo de lançamento "04"), Código de ajuste 0251 (Tabela IX:
  // multa da bandeira por retentativa). PV 1051583117, arquivo real 2025-08-29. Sem autorização
  // (não é uma venda com cartão presente), mas com NSU (626) — vira candidato a cancelamento.
  private static final String AJUSTE_DEBITO =
    "E105158311700100200000000000401027058000191360338010001092025-09-01001720021051583117360338010001090000000000000000000           2508280410260019854   0251040NNNNNNN00000000000006260000000000                                        000000000000000-0000000000074-0000000000074-0000000000074+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000-0000000000074-0000000000074+0000000000000+0000000000000+0000000000000+00000000000000002510136033801000109001000000000000000000000000000000               9980626402004000000000000270820252808202528082025280820250250829                         01092025105158311700NNN034186390000000000000000245151                       N0100000000000000";

  // Segmento E - Ajuste a crédito (Tipo de lançamento "05"), Código de ajuste 0177 (Tabela IX:
  // transferência entre PVs mesma raiz CNPJ). PV 1018802468, arquivo real 2025-10-07.
  private static final String AJUSTE_CREDITO =
    "E101880246800100200000000000501027058000191360338010001092025-10-09001020021018802468360338010001090000000000000000000           2510070510260017452   0177040NNNSNNN00000000000007780000000000                                        002000020000000+0000000000192+0000000000192+0000000000192+0000000000000+0000000000000+0000000000000+0000000000000-0000000000003+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+00000000000000000000136033801000109001000000000000000000000000000000               9980778299505000000000000061020250710202507102025071020250251006                         09102025101880246800NNN034186390000000000000000245151                       N0100000000000000";

  // Segmento E - Contestação do portador do cartão / chargeback (Tipo de lançamento "08"),
  // Código de ajuste 0301 (Tabela IX: "Venda contestada pelo banco a pedido do portador do
  // cartão"). PV 1051583117, arquivo real 2025-07-16, com autorização e NSU reais (TID presente).
  private static final String CONTESTACAO =
    "E105158311700200200000364320801027058000191360338010001092025-08-18002720021051583117360338010001090000000000000000000           2508060810410003119   0301010NNNNNNN5536360291351012000000000010515831174N83659MJE       1752716083445002480000000248-0000000001508-0000000001508-0000000001471+0000000000037+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000-0000000001508-0000000001508+0000000000000+0000000000000+0000000000000+00000000000003010136033801000109002000000000000000000000000000000               00711701830080000000000001607202506082025060820251607202502507162507160210360071837      18082025105158311700NNN034186390000000000000000245151                       N8100000000000000";

  // Segmento E - Aluguel de máquina (Tipo de lançamento "10"). PV 1018802468, arquivo real
  // 2025-10-07. Sem NSU/autorização (não é ligado a nenhuma venda específica) e sem Código de
  // ajuste (Tabela IX em branco — cai no fallback do texto da Tabela II).
  private static final String ALUGUEL_MAQUINA =
    "E101880246800700200000000001001027058000191360338010001092025-10-07007220021018802468360338010001090000000000000000000           2509301019000079165       040NNNNNNN00000000000000000000000000                                        000000000000000-0000000003190-0000000003190-0000000003190+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000+0000000000000-0000000003190-0000000003190+0000000000000+0000000000000+0000000000000+00000000000000000000036033801000109001000000000000000000000000000000               9984221671710000000000000300920253009202505102025051020250251001                         07102025101880246800NNN034186390000000000000000245151                       N0000000000000000";

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final ProcessCielo03Service service = new ProcessCielo03Service(lookupService, null, null, null, null, null, null, null);

  @Test
  void mapsDebitSaleFields() {
    // Código "007" (Tabela III) = Elo — confirmado pela faixa de BIN real da linha (655015, faixa
    // conhecida da Elo), não Sorocred.
    stubLookups(1018802468, "007", "Elo");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_DEBITO, 1, new ProcessedFileEntity(), "01");

    assertThat(tx.getEstablishment().getPvNumber()).isEqualTo(1018802468);
    assertThat(tx.getFlag().getName()).isEqualTo("Elo");
    assertThat(tx.getAuthorization()).isEqualTo("054706");
    assertThat(tx.getCardNumber()).isEqualTo("655015******8038");
    assertThat(tx.getNsu()).isEqualTo(500002L);
    assertThat(tx.getTid()).isNullOrEmpty();
    assertThat(tx.getGrossValue()).isEqualByComparingTo(new BigDecimal("240.20"));
    assertThat(tx.getLiquidValue()).isEqualByComparingTo(new BigDecimal("235.04"));
    assertThat(tx.getDiscountValue()).isEqualByComparingTo(new BigDecimal("5.16"));
    assertThat(tx.getMachine()).isEqualTo("42216717");
    assertThat(tx.getSaleDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 7));
    assertThat(tx.getInstallment()).isEqualTo(1);
    assertThat(tx.getModality()).isEqualTo(ModalityEnum.CASH_DEBIT.getCode());
    assertThat(tx.getRvNumber()).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR(DETAIL_DEBITO)));
    // Canal da venda "008" (Tabela VII) = TEF/PDV.
    assertThat(tx.getCapture()).isEqualTo(CaptureEnum.PDV.getCode());
  }

  @Test
  void mapsCreditSaleFieldsIncludingEcommerceTid() {
    stubLookups(1051583117, "001", "Visa");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_CREDITO, 1, new ProcessedFileEntity(), "02");

    assertThat(tx.getEstablishment().getPvNumber()).isEqualTo(1051583117);
    assertThat(tx.getFlag().getName()).isEqualTo("Visa");
    assertThat(tx.getAuthorization()).isEqualTo("885618");
    assertThat(tx.getCardNumber()).isEqualTo("407838******2981");
    assertThat(tx.getNsu()).isEqualTo(5032L);
    assertThat(tx.getTid()).isEqualTo("10515831175GC8QCTG4E");
    assertThat(tx.getReferenceNumber().trim()).isEqualTo("CLOUD108000");
    assertThat(tx.getGrossValue()).isEqualByComparingTo(new BigDecimal("171.70"));
    assertThat(tx.getLiquidValue()).isEqualByComparingTo(new BigDecimal("167.44"));
    assertThat(tx.getDiscountValue()).isEqualByComparingTo(new BigDecimal("4.26"));
    assertThat(tx.getSaleDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(tx.getInstallment()).isEqualTo(1);
    assertThat(tx.getModality()).isEqualTo(ModalityEnum.CASH_CREDIT.getCode());
    assertThat(tx.getRvNumber()).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR(DETAIL_CREDITO)));
    // Canal da venda "007" (Tabela VII) = E-commerce.
    assertThat(tx.getCapture()).isEqualTo(CaptureEnum.ECOMMERCE.getCode());
  }

  @Test
  void mapsInstallmentSaleFieldsUsingPerInstallmentGrossValue() {
    stubLookups(1051583117, "002", "Mastercard");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_PARCELADA, 1, new ProcessedFileEntity(), "03");

    // Valor bruto da PARCELA (81,13), não o valor total da venda (162,25, posições 248-260,
    // que o layout também traz mas não tem setter dedicado nesta fase).
    assertThat(tx.getGrossValue()).isEqualByComparingTo(new BigDecimal("81.13"));
    assertThat(tx.getLiquidValue()).isEqualByComparingTo(new BigDecimal("78.57"));
    assertThat(tx.getDiscountValue()).isEqualByComparingTo(new BigDecimal("2.56"));
    // installment = TOTAL de parcelas (02), não a parcela atual (01) — mesma convenção do Rede
    // (ProcessRedeEeVcService: installment do TransactionAcqEntity é o total da venda). A parcela
    // atual só é usada no InstallmentAcqEntity, ver buildsCurrentInstallmentSeparatelyFromTotal.
    assertThat(tx.getInstallment()).isEqualTo(2);
    // modality usa o TOTAL de parcelas (02) pra escalonar — 2-6 parcelas.
    assertThat(tx.getModality()).isEqualTo(ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode());
  }

  @Test
  void captureStaysNullForUnmappedOrNotApplicableChannel() {
    stubLookups(1051583117, "001", "Visa");
    // "998" (Tabela VII) = Não se aplica — sem equivalente em CaptureEnum, fica null em vez de
    // forçar um palpite (mesmo padrão conservador do ProcessRedeEeVdService.resolveCapture).
    String naoAplica = DETAIL_CREDITO.substring(0, 540) + "998" + DETAIL_CREDITO.substring(543);
    TransactionAcqEntity tx = service.buildTransaction(naoAplica, 1, new ProcessedFileEntity(), "02");

    assertThat(tx.getCapture()).isEqualTo(CaptureEnum.NULL.getCode());
  }

  @Test
  void rvNumberDiffersAcrossDistinctSales() {
    stubLookups(1018802468, "007", "Sorocred");
    stubLookups(1051583117, "001", "Visa");
    TransactionAcqEntity debito = service.buildTransaction(DETAIL_DEBITO, 1, new ProcessedFileEntity(), "01");
    TransactionAcqEntity credito = service.buildTransaction(DETAIL_CREDITO, 1, new ProcessedFileEntity(), "02");

    assertThat(debito.getRvNumber()).isNotNull().isNotEqualTo(credito.getRvNumber());
  }

  @Test
  void buildsInstallmentMirroringTransactionValues() {
    stubLookups(1051583117, "001", "Visa");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_CREDITO, 1, new ProcessedFileEntity(), "02");

    InstallmentAcqEntity installment = service.buildInstallment(tx, DETAIL_CREDITO, 1, "02");

    assertThat(installment.getTransaction()).isSameAs(tx);
    // Venda não parcelada ("02"): parcela atual e total coincidem em 1, mas por motivos
    // diferentes — não é mais uma cópia de tx.getInstallment(), ver buildsCurrentInstallmentSeparatelyFromTotal.
    assertThat(installment.getInstallment()).isEqualTo(1);
    assertThat(installment.getGrossValue()).isEqualByComparingTo(tx.getGrossValue());
    assertThat(installment.getLiquidValue()).isEqualByComparingTo(tx.getLiquidValue());
    assertThat(installment.getDiscountValue()).isEqualByComparingTo(tx.getDiscountValue());
    assertThat(installment.getAdjustmentValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(installment.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PENDING.getCode());
    assertThat(installment.getInstallmentStatus()).isEqualTo(StatusInstallmentEnum.SCHEDULED.getCode());
  }

  @Test
  void buildsCurrentInstallmentSeparatelyFromTotal() {
    stubLookups(1051583117, "002", "Mastercard");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_PARCELADA, 1, new ProcessedFileEntity(), "03");

    InstallmentAcqEntity installment = service.buildInstallment(tx, DETAIL_PARCELADA, 1, "03");

    // Parcela 1 de 2: o InstallmentAcqEntity guarda a parcela ATUAL (1) — é o que precisa bater
    // com o installmentNumber que o CIELO04 vai reportar quando essa parcela específica for paga —
    // enquanto o TransactionAcqEntity guarda o TOTAL (2), usado por ContractedAcquirerRateLookupService
    // e pelas telas de venda ACQ. As duas NÃO devem mais coincidir aqui.
    assertThat(installment.getInstallment()).isEqualTo(1);
    assertThat(tx.getInstallment()).isEqualTo(2);
  }

  @Test
  void buildsSalesSummaryMirroringTransactionValues() {
    stubLookups(1051583117, "001", "Visa");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_CREDITO, 1, new ProcessedFileEntity(), "02");

    SalesSummaryEntity summary = service.buildSalesSummary(tx);

    assertThat(summary.getPvNumber()).isEqualTo(tx.getEstablishment().getPvNumber());
    assertThat(summary.getRvNumber()).isEqualTo(tx.getRvNumber());
    assertThat(summary.getGrossValue()).isEqualByComparingTo(tx.getGrossValue());
    assertThat(summary.getLiquidValue()).isEqualByComparingTo(tx.getLiquidValue());
    assertThat(summary.getDiscountValue()).isEqualByComparingTo(tx.getDiscountValue());
    assertThat(summary.getAcquirer()).isSameAs(tx.getAcquirer());
    assertThat(summary.getCompany()).isSameAs(tx.getCompany());
    assertThat(summary.getFlag()).isSameAs(tx.getFlag());
    assertThat(summary.getProcessedFile()).isSameAs(tx.getProcessedFile());
    assertThat(summary.getStatusPaymentBank()).isEqualTo(StatusPaymentBankEnum.PENDING);
    assertThat(summary.getCreditOrderStatus()).isEqualTo(StatusReconciliationEnum.PENDING);
    assertThat(summary.getTransactionsStatus()).isEqualTo(StatusReconciliationEnum.PENDING);
  }

  @Test
  void mapsDebitAdjustmentAndFlagsItAsCancellationCandidate() {
    stubLookups(1051583117, "001", "Visa");
    AdjustmentEntity adjustment = service.buildAdjustment(AJUSTE_DEBITO, 1, new ProcessedFileEntity(), "04");

    assertThat(adjustment.getRecordType()).isEqualTo("04");
    assertThat(adjustment.getPvNumber()).isEqualTo(1051583117);
    assertThat(adjustment.getNsu()).isEqualTo(626L);
    assertThat(adjustment.getAdjustmentValue()).isEqualByComparingTo(new BigDecimal("-0.74"));
    assertThat(adjustment.getRawAdjustmentCode()).isEqualTo("0251");
    assertThat(adjustment.getAdjustmentDescription())
      .isEqualTo("Cobrança/devolução de multa da bandeira por excesso de retentativas de venda no mesmo cartão");
    assertThat(adjustment.getAdjustmentType()).isEqualTo("CIELO_DEBIT_ADJUSTMENT");
    // NSU presente + tipo "04" (débito) => candidato a cancelamento (AcquirerSaleCancellationService).
    assertThat(adjustment.getCancellationValueRequested()).isEqualByComparingTo(new BigDecimal("0.74"));
    assertThat(adjustment.getTransactionValue()).isEqualByComparingTo(new BigDecimal("0.74"));
    assertThat(adjustment.getRvNumberOriginal()).isEqualTo(FileParserUtils.deriveConciliationKey(chaveUR(AJUSTE_DEBITO)));
  }

  @Test
  void mapsCreditAdjustmentWithoutCancellationCandidacy() {
    stubLookups(1018802468, "001", "Visa");
    AdjustmentEntity adjustment = service.buildAdjustment(AJUSTE_CREDITO, 1, new ProcessedFileEntity(), "05");

    assertThat(adjustment.getRecordType()).isEqualTo("05");
    assertThat(adjustment.getAdjustmentValue()).isEqualByComparingTo(new BigDecimal("1.92"));
    assertThat(adjustment.getRawAdjustmentCode()).isEqualTo("0177");
    assertThat(adjustment.getAdjustmentDescription())
      .isEqualTo("Transferência de valores entre estabelecimentos da mesma raiz de CNPJ para compensação de saldo");
    assertThat(adjustment.getAdjustmentType()).isEqualTo("CIELO_CREDIT_ADJUSTMENT");
    // Crédito ao estabelecimento não é cancelamento, mesmo com NSU presente.
    assertThat(adjustment.getCancellationValueRequested()).isNull();
    assertThat(adjustment.getTransactionValue()).isNull();
  }

  @Test
  void mapsChargebackAdjustmentAsCancellationCandidateWithStrongTransactionKey() {
    stubLookups(1051583117, "001", "Visa");
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
  void mapsMachineRentalWithoutNsuOrCancellationCandidacyAndFallsBackToTabelaIIDescription() {
    stubLookups(1018802468, "001", "Visa");
    AdjustmentEntity adjustment = service.buildAdjustment(ALUGUEL_MAQUINA, 1, new ProcessedFileEntity(), "10");

    assertThat(adjustment.getRecordType()).isEqualTo("10");
    // Sem NSU (zerado no arquivo) — não é ligado a nenhuma venda específica, mesmo padrão do "011" da Rede/EEVD.
    assertThat(adjustment.getNsu()).isEqualTo(0L);
    assertThat(adjustment.getAdjustmentValue()).isEqualByComparingTo(new BigDecimal("-31.90"));
    // Código de ajuste (Tabela IX) em branco no arquivo real — cai no fallback do texto da Tabela II.
    assertThat(adjustment.getRawAdjustmentCode()).isBlank();
    assertThat(adjustment.getAdjustmentDescription()).isEqualTo("Aluguel de máquina");
    assertThat(adjustment.getAdjustmentType()).isEqualTo("CIELO_MACHINE_RENTAL");
    assertThat(adjustment.getCancellationValueRequested()).isNull();
  }

  @Test
  void resolvesCompanyFromEstablishment() {
    CompanyEntity company = new CompanyEntity();
    company.setId(UUID.randomUUID());
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(1051583117);
    establishment.setCompany(company);
    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer());
    when(lookupService.establishmentByPvNumber(1051583117)).thenReturn(establishment);
    when(lookupService.flagByAcquirerCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
      .thenThrow(new IllegalStateException("bandeira não cadastrada"));

    TransactionAcqEntity tx = service.buildTransaction(DETAIL_CREDITO, 1, new ProcessedFileEntity(), "02");

    assertThat(tx.getCompany()).isSameAs(company);
    assertThat(tx.getFlag()).isNull();
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
  }

  /** Extrai a "Chave UR" (posição 30-129 do manual, 1-based) igual ao que buildTransaction faz internamente. */
  private String chaveUR(String line) {
    return line.substring(29, 129).trim();
  }

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }
}
