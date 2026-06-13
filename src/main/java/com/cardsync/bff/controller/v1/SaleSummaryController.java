package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.SaleSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/sales-summary")
public class SaleSummaryController {

  private final SaleSummaryService saleSummaryService;

  @PostMapping("/search")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<SaleSummaryModel> search(@RequestBody ListQueryDto<SaleSummaryFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<SaleSummaryModel> page = saleSummaryService.search(pageable, body);

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
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<SaleSummaryFilter> body) {
    return saleSummaryService.totals(body);
  }
}
