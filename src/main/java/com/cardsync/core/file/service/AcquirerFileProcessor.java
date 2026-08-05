package com.cardsync.core.file.service;

/**
 * Marcador implementado por cada serviço de processamento de arquivos de uma adquirente
 * (Rede, Cielo, e futuras). {@link ProcessFileAcquirerService} injeta automaticamente todos
 * os beans que implementam esta interface — uma adquirente nova só precisa implementá-la,
 * sem tocar em nenhum ponto de orquestração (FileStorageTask/JobSequencialService/
 * FileProcessingController).
 */
public interface AcquirerFileProcessor {

  void processFiles();
}
