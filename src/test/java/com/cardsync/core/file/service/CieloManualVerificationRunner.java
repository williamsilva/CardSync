package com.cardsync.core.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Ferramenta manual de verificação contra o banco de dev real — não roda no {@code mvn test}
 * padrão (nome não bate com o include pattern do Surefire). Disparar explicitamente com:
 * {@code mvn -o -Dtest=CieloManualVerificationRunner#run test}
 */
@SpringBootTest
class CieloManualVerificationRunner {

  @Autowired
  private ProcessFileCieloService processFileCieloService;

  @Test
  void run() {
    processFileCieloService.processFiles();
  }
}
