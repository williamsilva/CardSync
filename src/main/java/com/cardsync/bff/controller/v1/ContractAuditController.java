package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ContractAuditModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ContractAuditModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ContractAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/contract-audit")
public class ContractAuditController {

  private final ContractAuditService contractAuditService;

  @PostMapping("/divergent-fees")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ContractAuditModel> divergentFees(@RequestBody ListQueryDto<ContractAuditModelFilter> body) {
    System.out.println("Filter " + body);
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ContractAuditModel> page = contractAuditService.divergentFees(pageable, body);

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
