package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.EstablishmentMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.EstablishmentModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.EstablishmentInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.EstablishmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.service.EstablishmentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/establishments")
public class EstablishmentController {

  private final EstablishmentService service;
  private final EstablishmentModelAssembler modelAssembler;
  private final EstablishmentMinimalModelAssembler minimalModelAssembler;
  private final PagedResourcesAssembler<EstablishmentEntity> pagedResourcesAssembler;

  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<EstablishmentMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(service.listOptionsFilter());
  }

  @GetMapping("/{id}")
  @CheckSecurity.Register.Establishments.CanConsult
  public EstablishmentModel getById(@PathVariable UUID id) {
    EstablishmentEntity entity = service.getById(id);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Establishments.CanConsult
  public PagedModel<EstablishmentModel> search(@RequestBody ListQueryDto<EstablishmentFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Establishments.CanCreate
  public EstablishmentModel create(@Valid @RequestBody EstablishmentInput body) {
    EstablishmentEntity entity = service.create(body);
    return modelAssembler.toModel(entity);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Establishments.CanChange
  public EstablishmentModel update(@PathVariable UUID id, @Valid @RequestBody EstablishmentInput body) {
    EstablishmentEntity entity = service.update(id, body);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    service.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Establishments.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    service.blockBulk(body.ids());
  }

}