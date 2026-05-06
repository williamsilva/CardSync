package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.erp.TransactionsErpModel;
import com.cardsync.bff.controller.v1.representation.model.erp.TransactionErpSalesTotalsModel;
import com.cardsync.domain.service.TransactionErpSalesService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/transaction/erp/sales")
public class TransactionErpSalesController {

  private final TransactionErpSalesService transactionErpSalesService;

  @PostMapping("/search")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<TransactionsErpModel> search(@RequestBody ListQueryDto<TransactionErpSalesFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<TransactionsErpModel> page = transactionErpSalesService.search(pageable, body);

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

  @PostMapping("/totals")
  @CheckSecurity.FileProcessing.CanRead
  public TransactionErpSalesTotalsModel totals(@RequestBody ListQueryDto<TransactionErpSalesFilter> body) {
    return transactionErpSalesService.totals(body);
  }
}
