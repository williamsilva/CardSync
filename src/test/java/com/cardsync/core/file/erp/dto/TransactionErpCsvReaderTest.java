package com.cardsync.core.file.erp.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionErpCsvReaderTest {

  @TempDir
  Path tempDir;

  private final TransactionErpCsvReader reader = new TransactionErpCsvReader();

  @Test
  void shouldReadSemicolonCsvWithBusinessContext() throws Exception {
    Path file = tempDir.resolve("erp.csv");
    Files.writeString(file,
      "Transação;Origem;Adquirente;Pagto;Bandeira;Parcelas;NSU;Autorização;Valor;Data;CNPJ Empresa;Empresa;PV;Máquina\n" +
      "Pagamento;TEF;Rede;Crédito;Visa;2;123456;ABC123;R$ 100,50;10/04/2026 14:30:00;12.345.678/0001-99;Empresa Teste;7867379;PDV01\n",
      Charset.forName("Windows-1252"));

    var rows = reader.read(file);

    assertThat(rows).hasSize(1);
    var row = rows.getFirst();
    assertThat(row.getTransaction()).isEqualTo("Pagamento");
    assertThat(row.getAcquirer()).isEqualTo("Rede");
    assertThat(row.getFlag()).isEqualTo("Visa");
    assertThat(row.getInstallment()).isEqualTo(2);
    assertThat(row.getNsu()).isEqualTo(123456L);
    assertThat(row.getAuthorization()).isEqualTo("ABC123");
    assertThat(row.getGrossValue()).isEqualByComparingTo("100.50");
    assertThat(row.getCompanyCnpj()).isEqualTo("12345678000199");
    assertThat(row.getCompanyName()).isEqualTo("Empresa Teste");
    assertThat(row.getEstablishmentPvNumber()).isEqualTo(7867379);
    assertThat(row.getMachine()).isEqualTo("PDV01");
  }

  @Test
  void shouldReadCommaCsvRespectingQuotedValues() throws Exception {
    Path file = tempDir.resolve("erp-comma.csv");
    Files.writeString(file,
      "Transacao,Origem,Adquirente,Pagamento,Bandeira,Parcelas,Valor,Data,Empresa,PV\n" +
      "Pagamento,ECOMMERCE,Rede,Crédito,Visa,1,\"1.234,56\",01/04/2026,\"Empresa, Loja\",123\n",
      Charset.forName("Windows-1252"));

    var rows = reader.read(file);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getGrossValue()).isEqualByComparingTo("1234.56");
    assertThat(rows.getFirst().getCompanyName()).isEqualTo("Empresa, Loja");
    assertThat(rows.getFirst().getEstablishmentPvNumber()).isEqualTo(123);
  }

  @Test
  void shouldIgnoreReportFooterWithTransactionCount() throws Exception {
    Path file = tempDir.resolve("erp-footer.csv");
    Files.writeString(file,
      "Transação;Origem;Adquirente;Pagto;Bandeira;Parcelas;NSU;Autorização;Valor;Data\n" +
      "Pagamento;TEF;Rede;Crédito;Visa;1;123456;ABC123;R$ 100,50;01/12/25 23:56\n" +
      "862 transações\n",
      Charset.forName("Windows-1252"));

    var rows = reader.read(file);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getSaleDate()).isNotNull();
    assertThat(rows.getFirst().getSaleDate().getYear()).isEqualTo(2025);
  }
}
