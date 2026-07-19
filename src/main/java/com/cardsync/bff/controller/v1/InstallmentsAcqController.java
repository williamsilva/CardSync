package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentAcqModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.InstallmentsAcqFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.InstallmentsAcqService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/transaction/acq/installments")
public class InstallmentsAcqController {

  private final InstallmentsAcqService transactionAcqInstallmentsService;

  @PostMapping("/search")
  @CheckSecurity.Documents.AcquirersInstallments.CanConsult
  public PagedModel<InstallmentAcqModel> search(@RequestBody ListQueryDto<InstallmentsAcqFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<InstallmentAcqModel> page = transactionAcqInstallmentsService.search(pageable, body);

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
  @CheckSecurity.Documents.AcquirersInstallments.CanConsult
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<InstallmentsAcqFilter> body) {
    return transactionAcqInstallmentsService.totals(body);
  }
}
