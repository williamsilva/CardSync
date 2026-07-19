package com.cardsync.bff.controller.v1;


import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.CursorDto;
import com.cardsync.domain.filter.query.CursorPageResponse;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.TransactionAcqSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/transaction/acq/sales")
public class TransactionAcqSalesController {

  private final TransactionAcqSalesService transactionAcqSalesService;

  /**
   * Busca com OFFSET pagination — inclui total de registros.
   * Usar na primeira carga da tela ou quando o sort for diferente de saleDate.
   */
  @PostMapping("/search")
  @CheckSecurity.Documents.AcquirersSales.CanConsult
  public PagedModel<TransactionsAcqModel> search(@RequestBody ListQueryDto<TransactionAcqSalesFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<TransactionsAcqModel> page = transactionAcqSalesService.search(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  /**
   * Busca com keyset pagination (cursor-based) — sem total, para navegação rápida.
   *
   * <p>Usar para carregar a página 2+ com sort padrão (saleDate DESC).
   * Elimina o custo de OFFSET em tabelas grandes.
   *
   * <p>Body: mesmo {@link ListQueryDto} do {@code /search}, com campos adicionais
   * {@code cursor.saleDate} e {@code cursor.id} do último item retornado.
   *
   * <p>Resposta: {@link CursorPageResponse} com {@code nextCursor} para a próxima página
   * e {@code hasMore} indicando se há mais resultados.
   */
  @PostMapping("/search-cursor")
  @CheckSecurity.Documents.AcquirersSales.CanConsult
  public CursorPageResponse<TransactionsAcqModel> searchWithCursor(
    @RequestBody ListQueryDto<TransactionAcqSalesFilter> body,
    @RequestParam(required = false) String cursorDate,
    @RequestParam(required = false) UUID cursorId
  ) {
    CursorDto cursor = (cursorDate != null && cursorId != null)
      ? new CursorDto(java.time.OffsetDateTime.parse(cursorDate), cursorId)
      : null;

    int pageSize = (body.size() != null && body.size() > 0) ? body.size() : 20;

    return transactionAcqSalesService.searchWithCursor(body, cursor, pageSize);
  }

  @PostMapping("/totals")
  @CheckSecurity.Documents.AcquirersSales.CanConsult
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<TransactionAcqSalesFilter> body) {
    return transactionAcqSalesService.totals(body);
  }
}