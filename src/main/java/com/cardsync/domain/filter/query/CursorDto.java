package com.cardsync.domain.filter.query;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cursor para keyset pagination (paginação por posição).
 *
 * <p>Em vez de LIMIT/OFFSET (que lê e descarta N linhas anteriores),
 * o cursor carrega apenas a partir do último item visto, usando um predicado
 * do tipo: WHERE (sale_date < :cursorDate) OR (sale_date = :cursorDate AND id < :cursorId).
 *
 * <p>O frontend deve armazenar o cursor retornado na resposta e enviá-lo
 * na próxima requisição. Para a primeira página, omitir o cursor.
 *
 * <p>Limitação: keyset pagination exige ordenação estável por (sort_field, id).
 * Quando o sort field for diferente de {@code saleDate}, o cursor precisará
 * do valor desse campo — nesse caso o cursor não é suportado e o sistema
 * cai de volta para OFFSET automaticamente.
 */
public record CursorDto(
  OffsetDateTime saleDate,  // último saleDate visto (sort padrão)
  UUID id                   // último id visto (desempate)
) {
  public boolean isPresent() {
    return saleDate != null && id != null;
  }
}