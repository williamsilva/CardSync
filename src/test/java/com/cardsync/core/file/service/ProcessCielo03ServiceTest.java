package com.cardsync.core.file.service;

import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ModalityEnum;
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

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final ProcessCielo03Service service = new ProcessCielo03Service(lookupService, null, null, null);

  @Test
  void mapsDebitSaleFields() {
    stubLookups(1018802468, "007", "Sorocred");
    TransactionAcqEntity tx = service.buildTransaction(DETAIL_DEBITO, 1, new ProcessedFileEntity(), "01");

    assertThat(tx.getEstablishment().getPvNumber()).isEqualTo(1018802468);
    assertThat(tx.getFlag().getName()).isEqualTo("Sorocred");
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
    assertThat(tx.getInstallment()).isEqualTo(2);
    assertThat(tx.getModality()).isEqualTo(ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode());
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

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }
}
