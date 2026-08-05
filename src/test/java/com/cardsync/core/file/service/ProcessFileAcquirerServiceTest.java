package com.cardsync.core.file.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProcessFileAcquirerServiceTest {

  @Test
  void processesEveryRegisteredAcquirer() {
    AcquirerFileProcessor rede = mock(AcquirerFileProcessor.class);
    AcquirerFileProcessor cielo = mock(AcquirerFileProcessor.class);
    ProcessFileAcquirerService service = new ProcessFileAcquirerService(List.of(rede, cielo));

    service.processFiles();

    verify(rede, times(1)).processFiles();
    verify(cielo, times(1)).processFiles();
  }

  @Test
  void oneAcquirerFailingDoesNotPreventTheOthersFromRunning() {
    AcquirerFileProcessor rede = mock(AcquirerFileProcessor.class);
    AcquirerFileProcessor cielo = mock(AcquirerFileProcessor.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("falha simulada")).when(rede).processFiles();

    ProcessFileAcquirerService service = new ProcessFileAcquirerService(List.of(rede, cielo));

    service.processFiles();

    verify(rede, times(1)).processFiles();
    verify(cielo, times(1)).processFiles();
  }

  @Test
  void throwsWhenNoAcquirerProcessorIsRegistered() {
    ProcessFileAcquirerService service = new ProcessFileAcquirerService(List.of());

    assertThatThrownBy(service::processFiles).isInstanceOf(IllegalStateException.class);
  }
}
