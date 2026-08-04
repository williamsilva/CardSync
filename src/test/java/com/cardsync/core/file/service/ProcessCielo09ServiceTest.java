package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
import com.cardsync.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o mapeamento do Registro D (UR Agenda) do arquivo CIELO09 (Saldo em aberto) pra
 * OpenBalanceEntity — ver ProcessCielo09Service#buildOpenBalance. Diferente do CIELO04, aqui o
 * Registro D é o próprio registro de conteúdo (sem Registro E) — a linha usada é real, extraída de
 * `CIELO09D_1051583117_20260101_20260101_20260101.TXT.txt` (PV 1051583117).
 */
class ProcessCielo09ServiceTest {

  private static final String REGISTRO_D =
    "D1051583117360338010001093603380100010936033801000109001002105158311703+0000000031690+0000000000787+000000003090303418639000000000000000024515100000402           01027058000191360338010001092026-01-0200172002105158311736033801000109000000000000000000000000000000000000201202631122025020120261051583117NNN00000000000000R                                                                                 ";

  private final FileLookupService lookupService = mock(FileLookupService.class);
  private final BankingDomicileResolver bankingDomicileResolver = mock(BankingDomicileResolver.class);
  private final ProcessCielo09Service service =
    new ProcessCielo09Service(lookupService, bankingDomicileResolver, null, null, null);

  @Test
  void mapsOpenBalanceFieldsFromRealLine() {
    stubLookups(1051583117);
    BankingDomicileEntity domicile = new BankingDomicileEntity();
    domicile.setId(UUID.randomUUID());
    when(bankingDomicileResolver.resolve(eq("0341"), eq(86390), eq(245151), org.mockito.ArgumentMatchers.any()))
      .thenReturn(Optional.empty());
    when(bankingDomicileResolver.resolve(eq("0341"), eq(8639), eq(245151), org.mockito.ArgumentMatchers.any()))
      .thenReturn(Optional.of(domicile));

    OpenBalanceEntity openBalance = service.buildOpenBalance(REGISTRO_D, 1, new ProcessedFileEntity());

    assertThat(openBalance.getPvNumber()).isEqualTo(1051583117);
    assertThat(openBalance.getSettlementType()).isEqualTo(2);
    assertThat(openBalance.getPaymentStatus()).isEqualTo(3);
    assertThat(openBalance.getGrossValue()).isEqualByComparingTo(new BigDecimal("316.90"));
    assertThat(openBalance.getLiquidValue()).isEqualByComparingTo(new BigDecimal("309.03"));
    assertThat(openBalance.getNumberOfReleases()).isEqualTo(4);
    assertThat(openBalance.getLaunchType()).isEqualTo("02");
    assertThat(openBalance.getOpenBalanceIndicator()).isEqualTo("R");
    assertThat(openBalance.getPaymentDate()).isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(openBalance.getOriginalDueDate()).isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(openBalance.getBankingDomicile()).isSameAs(domicile);

    // Código de bandeira "001" não tem descrição na Tabela III do manual (campo reservado) —
    // flagByAcquirerCode não é stubado pra ele, então safeFlag precisa engolir a ausência.
    assertThat(openBalance.getFlag()).isNull();
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

    OpenBalanceEntity openBalance = service.buildOpenBalance(REGISTRO_D, 1, new ProcessedFileEntity());

    assertThat(openBalance.getCompany()).isSameAs(company);
  }

  private void stubLookups(int pvNumber) {
    AcquirerEntity acquirer = acquirer();
    EstablishmentEntity establishment = new EstablishmentEntity();
    establishment.setPvNumber(pvNumber);

    when(lookupService.acquirerByIdentifier("CIELO")).thenReturn(acquirer);
    when(lookupService.establishmentByPvNumber(pvNumber)).thenReturn(establishment);
    when(lookupService.origin("CIELO")).thenReturn(new OriginFileEntity());
  }

  private AcquirerEntity acquirer() {
    AcquirerEntity acquirer = new AcquirerEntity();
    acquirer.setId(UUID.randomUUID());
    acquirer.setFantasyName("Cielo");
    return acquirer;
  }
}
