package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.bff.controller.v1.representation.model.transactions.CreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.reconciliation.summary.CreditOrderManualService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.CreditOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/credit-order")
public class CreditOrderController {

  private final CreditOrderService creditOrderService;
  private final CreditOrderManualService creditOrderManualService;

  @PostMapping("/manual")
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderManualResult createManual(@Valid @RequestBody CreditOrderManualInput body) {
    return creditOrderManualService.create(body);
  }

  @PostMapping("/search")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<CreditOrderModel> search(@RequestBody ListQueryDto<CreditOrderFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<CreditOrderModel> page = creditOrderService.search(pageable, body);

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
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<CreditOrderFilter> body) {
    return creditOrderService.totals(body);
  }
}
