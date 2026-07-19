package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.InstallmentsErpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/transaction/erp/installments")
public class InstallmentsErpController {

  private final InstallmentsErpService transactionErpInstallmentsService;

  @PostMapping("/search")
  @CheckSecurity.Documents.ErpInstallments.CanConsult
  public PagedModel<InstallmentErpModel> search(@RequestBody ListQueryDto<InstallmentsErpFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<InstallmentErpModel> page = transactionErpInstallmentsService.search(pageable, body);

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
  @CheckSecurity.Documents.ErpInstallments.CanConsult
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<InstallmentsErpFilter> body) {
    return transactionErpInstallmentsService.totals(body);
  }
}
