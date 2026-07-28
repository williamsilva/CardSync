package com.cardsync.core.file.acquirerreport.dto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirma o mapeamento posicional das 30 colunas do relatório de pagamentos da adquirente
 * (ver AcquirerPaymentReportCsvReader) contra uma linha de exemplo no mesmo formato do arquivo
 * real usado para desenhar a importação em lote de ordens de crédito.
 */
class AcquirerPaymentReportCsvReaderTest {

  private final AcquirerPaymentReportCsvReader reader = new AcquirerPaymentReportCsvReader();

  private MockMultipartFile csvFile(String... lines) {
    String content = String.join("\r\n", lines) + "\r\n";
    return new MockMultipartFile("file", "pagamentos.csv", "text/csv",
      content.getBytes(Charset.forName("Windows-1252")));
  }

  @Test
  void parsesDataRowAtDocumentedColumnPositions() throws Exception {
    String header = String.join(";",
      "Data Recebimento", "Data Venda", "Data Vencimento", "Valor Bruto Original",
      "Valor Bruto Atualizado", "Taxa MDR", "Valor MDR Descontado", "Valor Liquido",
      "Negociada", "Percentual", "NSU/CV", "TID", "Numero Pedido", "Numero Autorizacao",
      "Resumo de Vendas", "Nome Estabelecimento", "Estabelecimento", "Numero Cartao",
      "Indicador Tokenizado", "Codigo IATA", "Modalidade", "Bandeira", "Numero Parcelas",
      "Parcela", "Banco", "Agencia", "Conta Corrente", "Cancelamento", "Data Cancelamento", "Status");

    String data = String.join(";",
      "03/06/2026", "01/05/2026", "04/06/2026", "120,00", "120,00", "2,24", "2,69",
      "117,30", "N", "0", "123456", "TID123", "PED1", "AUTH1", "38949474", "LOJA TESTE",
      "12345", "****1234", "N", "", "Crédito", "Mastercard", "3", "2", "341", "1234",
      "56789", "N", "", "paga");

    List<AcquirerPaymentReportRow> rows = reader.read(csvFile(header, data));

    assertThat(rows).hasSize(1);
    AcquirerPaymentReportRow row = rows.get(0);
    assertThat(row.rvNumber()).isEqualTo(38949474);
    assertThat(row.pvNumber()).isEqualTo(12345);
    assertThat(row.installmentNumber()).isEqualTo(2);
    assertThat(row.installmentTotal()).isEqualTo(3);
    assertThat(row.releaseDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    assertThat(row.originalDueDate()).isEqualTo(LocalDate.of(2026, 6, 4));
    assertThat(row.releaseValue()).isEqualByComparingTo("117.30");
    assertThat(row.grossValue()).isEqualByComparingTo("120.00");
    assertThat(row.discountValue()).isEqualByComparingTo("2.69");
    assertThat(row.status()).isEqualTo("paga");
  }

  @Test
  void skipsHeaderRowAndBlankLines() throws Exception {
    List<AcquirerPaymentReportRow> rows = reader.read(csvFile("Resumo de Vendas;Parcela", "", ""));

    assertThat(rows).isEmpty();
  }
}
