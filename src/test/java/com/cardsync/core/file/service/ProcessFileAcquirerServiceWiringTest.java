package com.cardsync.core.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova com o contexto Spring real (não mocks) que toda adquirente com serviço de arquivo já
 * implementado é injetada automaticamente em {@link ProcessFileAcquirerService} — se uma
 * adquirente futura esquecer de implementar {@link AcquirerFileProcessor}, este teste não vai
 * mais bater a contagem esperada, denunciando o esquecimento antes de chegar em produção.
 */
@SpringBootTest
class ProcessFileAcquirerServiceWiringTest {

  @Autowired
  private List<AcquirerFileProcessor> acquirerFileProcessors;

  @Test
  void registersRedeAndCieloAsAcquirerFileProcessors() {
    assertThat(acquirerFileProcessors)
      .hasSize(2)
      .anyMatch(ProcessFileRedeService.class::isInstance)
      .anyMatch(ProcessFileCieloService.class::isInstance);
  }
}
