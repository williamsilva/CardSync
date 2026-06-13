package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

/**
 * Totais de arquivos processados, agregados por status (e por presença de linhas
 * pendentes), respeitando os mesmos filtros do POST /file-processing/files/search.

 * - processed:        arquivos com status PROCESSED
 * - warnings:         arquivos com status PROCESSED_WITH_WARNINGS
 * - errors:           arquivos com status ERROR
 * - duplicate:        arquivos com status DUPLICATE
 * - invalid:          arquivos com status INVALID
 * - pendingContract:  arquivos com linhas pendentes de contrato (pendingContractLines > 0)
 * - pendingContext:   arquivos com linhas pendentes de contexto de negócio (pendingBusinessContextLines > 0)
 */
public record ProcessedFileTotalsModel(
  long processed,
  long warnings,
  long errors,
  long duplicate,
  long invalid,
  long pendingContract,
  long pendingContext
) {
}