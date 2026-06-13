package com.cardsync.domain.filter.query;

import java.util.List;

/**
 * Resposta de keyset pagination.
 *
 * <p>O frontend deve armazenar {@code nextCursor} e enviá-lo na próxima
 * requisição para obter a página seguinte. Quando {@code hasMore} for
 * {@code false}, não há mais páginas.
 *
 * <p>Não inclui {@code totalElements} — isso é intencional: o COUNT é a
 * operação mais cara em tabelas grandes. Para o primeiro carregamento, o
 * total ainda é calculado via o endpoint {@code /totals}.
 */
public record CursorPageResponse<T>(
  List<T> content,
  CursorDto nextCursor,   // null quando hasMore = false
  boolean hasMore,
  int pageSize
) {}