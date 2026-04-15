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

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    service.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Contracts.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    service.blockBulk(body.ids());
  }
}
