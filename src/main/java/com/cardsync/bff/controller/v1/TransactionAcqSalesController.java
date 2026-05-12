package com.cardsync.bff.controller.v1;


import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionAcquirersSalesTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.TransactionAcqSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/transaction/acq/sales")
public class TransactionAcqSalesController {

  private final TransactionAcqSalesService transactionAcqSalesService;

  @PostMapping("/search")
  @CheckSecurity.FileProcessing.CanRead
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

  @PostMapping("/totals")
  @CheckSecurity.FileProcessing.CanRead
  public TransactionAcquirersSalesTotalsModel totals(@RequestBody ListQueryDto<TransactionAcqSalesFilter> body) {
    return transactionAcqSalesService.totals(body);
  }
}
