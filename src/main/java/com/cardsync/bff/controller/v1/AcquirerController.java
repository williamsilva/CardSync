package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.AcquirerModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.AcquirerInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.AcquirerModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.AcquirerFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.service.AcquirerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/acquirer")
public class AcquirerController {

  private final AcquirerService service;
  private final AcquirerModelAssembler modelAssembler;
  private final PagedResourcesAssembler<AcquirerEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Companies.CanConsult
  public AcquirerModel getById(@PathVariable UUID id) {
    AcquirerEntity entity = service.getById(id);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/search")
  @CheckSecurity.Companies.CanConsult
  public PagedModel<AcquirerModel> search(@RequestBody ListQueryDto<AcquirerFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Companies.CanCreate
  public AcquirerModel create(@Valid @RequestBody AcquirerInput body) {
    AcquirerEntity entity = service.create(body);
    return modelAssembler.toModel(entity);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Companies.CanCreate
  public AcquirerModel update(@PathVariable UUID id, @Valid @RequestBody AcquirerInput body) {
    AcquirerEntity entity = service.update(id, body);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    service.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Companies.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    service.blockBulk(body.ids());
  }
}
