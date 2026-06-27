package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.SaleSummaryModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.SalesSummaryManualInput;
import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.reconciliation.summary.SalesSummaryManualService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.SaleSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/sales-summary")
public class SaleSummaryController {

  private final SaleSummaryService saleSummaryService;
  private final SalesSummaryManualService salesSummaryManualService;
  private final SaleSummaryModelAssembler saleSummaryModelAssembler;

  @PostMapping("/manual")
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public SaleSummaryModel createManual(@Valid @RequestBody SalesSummaryManualInput body) {
    return saleSummaryModelAssembler.toModel(salesSummaryManualService.create(body));
  }

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
