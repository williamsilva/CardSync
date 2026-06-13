package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackAnalysisTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackLifecycleModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ChargebackAnalysisFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ChargebacksService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/conciliation")
public class AdjustmentChargebacksController {

  private final ChargebacksService chargebacksService;

  @PostMapping("/chargebacks-lifecycle")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ChargebackLifecycleModel> chargebackLifecycles(@RequestBody ListQueryDto<ChargebackAnalysisFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    Page<ChargebackLifecycleModel> page = chargebacksService.listChargebackLifecycles(pageable, body);

    return toPagedModel(page);
  }

  @PostMapping("/chargebacks-totals")
  @CheckSecurity.FileProcessing.CanRead
  public ChargebackAnalysisTotalsModel chargebacksTotals(@RequestBody ListQueryDto<ChargebackAnalysisFilter> body) {
    return chargebacksService.chargebacksTotals(body);
  }

  private <T> PagedModel<T> toPagedModel(Page<T> page) {
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
}