package com.cardsync.domain.filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha de extrato bancário em texto livre (ex.: "RECEBIMENTO REDE MAST CD0007866470...")
 * a ser classificada (adquirente/bandeira/modalidade) e criada como lançamento bancário manual —
 * ver ManualBankStatementTextImportService.
 */
public record ReleasesBankManualTextImportInput(
  UUID companyId,
  UUID bankingDomicileId,
  LocalDate releaseDate,
  String description,
  BigDecimal releaseValue
) {
}
