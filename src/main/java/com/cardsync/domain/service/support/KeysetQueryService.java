package com.cardsync.domain.service.support;

import com.cardsync.domain.filter.query.CursorDto;
import com.cardsync.domain.filter.query.CursorPageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Motor genérico de keyset pagination para ordenação padrão DESC por saleDate + id.
 *
 * <p>Substitui LIMIT/OFFSET por um predicado de cursor:
 * <pre>
 *   WHERE (sale_date < :cursor_date)
 *      OR (sale_date = :cursor_date AND id < :cursor_id)
 * </pre>
 *
 * <p>Isso é eficiente porque usa os índices existentes
 * (idx_acq_sale_date_modality, idx_acq_modality_status_sale_date)
 * sem ler e descartar linhas anteriores.
 *
 * <p>Limitação: só funciona com sort padrão (saleDate DESC, id DESC).
 * Quando o usuário escolhe outro campo de sort, cai de volta para OFFSET.
 *
 * @param <E> tipo da entidade JPA
 * @param <M> tipo do model de resposta
 */
@Component
public class KeysetQueryService {

  @PersistenceContext
  private EntityManager entityManager;

  /**
   * Executa uma busca com keyset pagination.
   *
   * @param entityClass    classe da entidade
   * @param filterSpec     specification com todos os filtros (SEM fetch joins)
   * @param dataSpec       specification com fetch joins para carregamento
   * @param cursor         cursor da página anterior (null = primeira página)
   * @param pageSize       quantos itens por página
   * @param cursorField    campo do cursor no sort (ex: "saleDate")
   * @param mapper         função de conversão Entity → Model
   * @param cursorExtract  extrai o valor do cursor do último Model retornado
   */
  public <E, M> CursorPageResponse<M> fetch(
    Class<E> entityClass,
    Specification<E> filterSpec,
    Specification<E> dataSpec,
    CursorDto cursor,
    int pageSize,
    String cursorField,
    Function<E, M> mapper,
    Function<E, OffsetDateTime> cursorDateExtractor
  ) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // ── Query de dados ──────────────────────────────────────────────
    CriteriaQuery<E> cq = cb.createQuery(entityClass);
    Root<E> root = cq.from(entityClass);

    Predicate filterPredicate = filterSpec.toPredicate(root, cq, cb);
    Predicate dataPredicate   = dataSpec.toPredicate(root, cq, cb);

    Predicate combined = (filterPredicate != null && dataPredicate != null)
      ? cb.and(filterPredicate, dataPredicate)
      : (filterPredicate != null ? filterPredicate : dataPredicate);

    // Aplica cursor quando presente
    if (cursor != null && cursor.isPresent()) {
      Predicate cursorPredicate = buildCursorPredicate(cb, root, cursor, cursorField);
      combined = combined != null
        ? cb.and(combined, cursorPredicate)
        : cursorPredicate;
    }

    if (combined != null) {
      cq.where(combined);
    }

    // Ordenação: campo do cursor DESC + id DESC (garante determinismo)
    cq.orderBy(
      cb.desc(root.get(cursorField)),
      cb.desc(root.get("id"))
    );

    // Busca pageSize + 1 para detectar se há próxima página
    List<E> rows = entityManager.createQuery(cq)
      .setMaxResults(pageSize + 1)
      .getResultList();

    boolean hasMore = rows.size() > pageSize;
    List<E> page = hasMore ? rows.subList(0, pageSize) : rows;
    List<M> content = page.stream().map(mapper).toList();

    // Calcula próximo cursor a partir do último item da página atual
    CursorDto nextCursor = null;
    if (hasMore && !page.isEmpty()) {
      E last = page.getLast();
      OffsetDateTime lastDate = cursorDateExtractor.apply(last);
      // getId() via reflexão para manter o método genérico
      UUID lastId = extractId(last);
      if (lastDate != null && lastId != null) {
        nextCursor = new CursorDto(lastDate, lastId);
      }
    }

    return new CursorPageResponse<>(content, nextCursor, hasMore, pageSize);
  }

  /**
   * Predicado de cursor para ordenação DESC:
   * (cursorField < cursorDate) OR (cursorField = cursorDate AND id < cursorId)
   */
  private <E> Predicate buildCursorPredicate(
    CriteriaBuilder cb, Root<E> root, CursorDto cursor, String cursorField) {

    OffsetDateTime cursorDate = cursor.saleDate();
    UUID cursorId = cursor.id();

    // Linha com data anterior (mais antiga)
    Predicate dateBefore = cb.lessThan(
      root.<OffsetDateTime>get(cursorField), cursorDate
    );

    // Mesma data mas ID menor (UUID ordenado lexicograficamente no MySQL BINARY(16))
    Predicate sameDate = cb.equal(root.get(cursorField), cursorDate);
    Predicate idBefore = cb.lessThan(root.<UUID>get("id"), cursorId);

    return cb.or(dateBefore, cb.and(sameDate, idBefore));
  }

  @SuppressWarnings("unchecked")
  private <E> UUID extractId(E entity) {
    try {
      return (UUID) entity.getClass().getMethod("getId").invoke(entity);
    } catch (Exception e) {
      return null;
    }
  }
}