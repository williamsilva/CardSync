package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ReleasesBankService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/releases-bank")
public class ReleasesBanksController {

  private final ReleasesBankService releasesBankService;

  @PostMapping("/search")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ReleasesBankModel> search(@RequestBody ListQueryDto<ReleasesBankFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ReleasesBankModel> page = releasesBankService.search(pageable, body);

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
  public ValueTotalsModel totals(@RequestBody ListQueryDto<ReleasesBankFilter> body) {
    return releasesBankService.totals(body);
  }
}
