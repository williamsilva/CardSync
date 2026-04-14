package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.ContractModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.ContractModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.service.ContractService;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/contracts")
public class ContractController {

  private final ContractService service;
  private final ContractModelAssembler modelAssembler;
  private final PagedResourcesAssembler<ContractEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Register.Contracts.CanConsult
  public ContractModel getById(@PathVariable UUID id) {
    ContractEntity entity = service.getById(id);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Contracts.CanConsult
  public PagedModel<ContractModel> search(@RequestBody ListQueryDto<ContractFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }
}