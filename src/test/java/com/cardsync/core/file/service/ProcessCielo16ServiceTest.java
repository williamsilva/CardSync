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
 * Cobre o mapeamento do Registro "8" (Detalhe da Transação Pix) do arquivo CIELO16 pra
 * TransactionAcqEntity — ver ProcessCielo16Service#buildTransaction. Este cliente nunca recebeu
 * Pix pela Cielo (todo histórico real de CIELO16 é só Header+Trailer), então as linhas usadas são
 * do arquivo de teste OFICIAL da Cielo
 * (ArquivoTeste_ExtratoEletronico/CIELO16D_1234567890_20260303...TXT), não produção.
 */
class ProcessCielo16ServiceTest {

  private static final String PIX_TRANSACTION_1 =
    "8123456789001260302101803E9040088820260302131856721392001    656547260302+0000000020000-0000000000010+0000000019990000000000000000000000000000002603020000000100101438790260302101803                                        S0526030200656547S CIELO202603020000000000000000656547                                        E01027058202603022053glyBLqLT5uP                                                     ";

  private static final String PIX_TRANSACTION_2 =
    "8123456789001260302111737E00360305202603021417db5ae9fc45d    809144260302+0000000113289-0000000000010+0000000113279000000000000000000000000000002603020000000100101438790260302111737                                        S0526030200809144S CIELO202603020000000000000000809144                                        E01027058202603022053glyBLqLT5uP                                                     ";

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final ProcessCielo16Service service = new ProcessCielo16Service(lookupService, null, null, null);

  @Test
  void mapsFirstPixTransactionFields() {
    stubLookups(1234567890);
    TransactionAcqEntity tx = service.buildTransaction(PIX_TRANSACTION_1, 1, new ProcessedFileEntity(), "01");

    assertThat(tx.getEstablishment().getPvNumber()).isEqualTo(1234567890);
    assertThat(tx.getTid()).isEqualTo("E9040088820260302131856721392001");
    assertThat(tx.getNsu()).isEqualTo(656547L);
    assertThat(tx.getGrossValue()).isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(tx.getDiscountValue()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(tx.getLiquidValue()).isEqualByComparingTo(new BigDecimal("199.90"));
    assertThat(tx.getMachine()).isEqualTo("01438790");
    assertThat(tx.getSaleDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(tx.getInstallment()).isEqualTo(1);
    assertThat(tx.getModality()).isEqualTo(ModalityEnum.DIGITAL_WALLET.getCode());
    assertThat(tx.getCardNumber()).isNull();
    assertThat(tx.getAuthorization()).isNull();
  }

  @Test
  void mapsSecondPixTransactionFields() {
    stubLookups(1234567890);
    TransactionAcqEntity tx = service.buildTransaction(PIX_TRANSACTION_2, 1, new ProcessedFileEntity(), "01");

    assertThat(tx.getTid()).isEqualTo("E00360305202603021417db5ae9fc45d");
    assertThat(tx.getNsu()).isEqualTo(809144L);
    assertThat(tx.getGrossValue()).isEqualByComparingTo(new BigDecimal("1132.89"));
    assertThat(tx.getDiscountValue()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(tx.getLiquidValue()).isEqualByComparingTo(new BigDecimal("1132.79"));
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

    TransactionAcqEntity tx = service.buildTransaction(PIX_TRANSACTION_1, 1, new ProcessedFileEntity(), "01");

    assertThat(tx.getCompany()).isSameAs(company);
  }

  private void stubLookups(int pvNumber) {
    AcquirerEntity acquirer = acquirer();
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(pvNumber);

    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(pvNumber)).thenReturn(establishment);
  }

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }
}
