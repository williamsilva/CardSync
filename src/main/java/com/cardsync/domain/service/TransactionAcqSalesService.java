package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.CursorDto;
import com.cardsync.domain.filter.query.CursorPageResponse;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.service.support.KeysetQueryService;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.TransactionAcqSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionAcqSalesService {

  private final TransactionAcqSpecs transactionAcqSpecs;
  private final KeysetQueryService keysetQueryService;
  private final TransactionTotalsQueryService totalsQueryService;
  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionsAcqModelAssembler transactionsAcqModelAssembler;

  /**
   * Busca com OFFSET pagination (padrão existente).
   * Usar quando o sort for diferente de saleDate ou na primeira carga com total.
   */
  @Transactional(readOnly = true)
  public Page<TransactionsAcqModel> search(Pageable pageable, ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> filterSpec = transactionAcqSpecs.fromQueryForTotals(query);
    Specification<TransactionAcqEntity> dataSpec   = transactionAcqSpecs.fromQuery(query);

    long total = transactionAcqRepository.count(filterSpec);

    List<TransactionsAcqModel> content = total == 0
      ? List.of()
      : transactionAcqRepository.findAll(dataSpec, pageable)
      .stream()
      .map(transactionsAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * Busca com keyset pagination (cursor-based).
   *
   * <p>Usar para navegação de páginas 2+ com sort padrão (saleDate DESC).
   * Elimina o custo de OFFSET em tabelas grandes — em vez de pular N linhas,
   * filtra diretamente pelo último item visto via índice.
   *
   * <p>Fluxo:
   * <ol>
   *   <li>Primeira página: chamar {@link #search} com OFFSET para obter o total e o conteúdo.
   *   <li>Páginas seguintes: chamar este método com o cursor retornado na resposta anterior.
   * </ol>
   *
   * <p>O cursor é composto por {@code (saleDate, id)} do último item retornado.
   * Quando {@code cursor == null}, comporta-se como a primeira página sem total.
   */
  @Transactional(readOnly = true)
  public CursorPageResponse<TransactionsAcqModel> searchWithCursor(
    ListQueryDto<TransactionAcqSalesFilter> query,
    CursorDto cursor,
    int pageSize
  ) {
    Specification<TransactionAcqEntity> filterSpec = transactionAcqSpecs.fromQueryForTotals(query);
    Specification<TransactionAcqEntity> dataSpec   = transactionAcqSpecs.fromQuery(query);

    return keysetQueryService.fetch(
      TransactionAcqEntity.class,
      filterSpec,
      dataSpec,
      cursor,
      pageSize,
      "saleDate",
      transactionsAcqModelAssembler::toModel,
      TransactionAcqEntity::getSaleDate
    );
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = transactionAcqSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionAcqEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}