package com.cardsync.core.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ponto único de disparo do processamento de arquivos de adquirente — genérico, igual ao
 * papel que {@link ProcessFileBankService} já cumpre pro Banco/CNAB. Diferente do Banco
 * (onde os 4 layouts compartilham o mesmo parser {@code Cnab240FileProcessor}, resolvido por
 * conteúdo), Rede e Cielo não compartilham parser — cada {@link AcquirerFileProcessor} já sabe
 * processar sua própria adquirente de ponta a ponta. Uma adquirente falhando não impede as
 * demais (mesmo isolamento por-unidade que {@code ProcessFileBankService.processBankFolder}
 * já aplica por banco).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileAcquirerService {

  private final List<AcquirerFileProcessor> acquirerFileProcessors;

  public void processFiles() {
    if (acquirerFileProcessors.isEmpty()) {
      throw new IllegalStateException("Nenhum processador de adquirente registrado.");
    }

    for (AcquirerFileProcessor processor : acquirerFileProcessors) {
      try {
        processor.processFiles();
      } catch (Exception ex) {
        log.error("❌ Erro ao processar arquivos da adquirente {}: {}",
          processor.getClass().getSimpleName(), ex.getMessage(), ex);
      }
    }

    log.info("✅ Processamento de adquirentes finalizado: adquirentesConfiguradas={}", acquirerFileProcessors.size());
  }
}
