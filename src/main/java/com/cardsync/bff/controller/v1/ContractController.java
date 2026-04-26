package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.ContractModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.ContractInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.ContractModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.service.ContractService;
import jakarta.validation.Valid;
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
    return modelAssembler.toModel(service.getById(id));
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Contracts.CanConsult
  public PagedModel<ContractModel> search(@RequestBody ListQueryDto<ContractFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Contracts.CanCreate
  public ContractModel create(@Valid @RequestBody ContractInput body) {
    return modelAssembler.toModel(service.create(body));
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Contracts.CanChange
  public ContractModel update(@PathVariable UUID id, @Valid @RequestBody ContractInput body) {
    return modelAssembler.toModel(service.update(id, body));
  }

  @DeleteMapping("/{id}")
  @CheckSecurity.Register.Contracts.CanDelete
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @PostMapping("/{id}/validity")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void validity(@PathVariable UUID id) {
    service.validity(id);
  }

  @PostMapping("/{id}/closed")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void closed(@PathVariable UUID id) {
    service.closed(id);
  }

  @PostMapping("/{id}/expired")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void expired(@PathVariable UUID id) {
    service.expired(id);
  }

  @PostMapping("/validity")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void validityBulk(@Valid @RequestBody ListIdsInput body) {
    service.validityBulk(body.ids());
  }

  @PostMapping("/closed")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void closedBulk(@Valid @RequestBody ListIdsInput body) {
    service.closedBulk(body.ids());
  }

  @PostMapping("/expired")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void expiredBulk(@Valid @RequestBody ListIdsInput body) {
    service.expiredBulk(body.ids());
  }
}
