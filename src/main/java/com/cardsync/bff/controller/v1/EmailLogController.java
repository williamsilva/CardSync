package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.EmailLogModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.EmailLogModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.EmailLogFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.service.EmailLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1")
public class EmailLogController {

  private final EmailLogService service;
  private final EmailLogModelAssembler assembler;
  private final PagedResourcesAssembler<EmailLogEntity> pagedResourcesAssembler;

  @PostMapping("/email-logs")
  @CheckSecurity.Audit.Mail.CanConsultAuditMail
  public PagedModel<EmailLogModel> findAll(@RequestBody  ListQueryDto<EmailLogFilter> filter) {
    var pageable = PageableMapper.toPageable(filter.page(), filter.size(), filter.sort());
    var page = service.list(pageable, filter);

    return pagedResourcesAssembler.toModel(page, assembler);
  }

}
