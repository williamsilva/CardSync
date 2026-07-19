package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AnticipationModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.AnticipationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/anticipation")
public class AnticipationController {

  private final AnticipationService anticipationService;

  @PostMapping("/search")
  @CheckSecurity.Documents.Anticipation.CanConsult
  public PagedModel<AnticipationModel> search(@RequestBody ListQueryDto<AnticipationFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<AnticipationModel> page = anticipationService.search(pageable, body);

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
  @CheckSecurity.Documents.Anticipation.CanConsult
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<AnticipationFilter> body) {
    return anticipationService.totals(body);
  }
}
