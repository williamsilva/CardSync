package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.nimbussystems.commons.legacy.filter.query.CursorDto;
import com.nimbussystems.commons.legacy.filter.query.CursorPageResponse;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.service.support.KeysetQueryService;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.TransactionAcqSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra TransactionAcqEntity (sem conhecer os
    // aliases) — sort por qualquer coluna que não seja campo direto da entidade quebra com "No
    // property 'X' found for type 'TransactionAcqEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<TransactionsAcqModel> content = total == 0
      ? List.of()
      : transactionAcqRepository.findAll(dataSpec, pageableWithoutSort)
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