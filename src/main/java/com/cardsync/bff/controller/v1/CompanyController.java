package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.CompanyMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.CompanyModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.CompanyInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.CompanyFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.service.CompanyService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/company")
public class CompanyController {

  private final CompanyService service;
  private final CompanyModelAssembler modelAssembler;
  private final CompanyMinimalModelAssembler minimalModelAssembler;
  private final PagedResourcesAssembler<CompanyEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Register.Companies.CanConsult
  public CompanyModel getById(@PathVariable UUID id) {
    CompanyEntity entity = service.getById(id);
    return modelAssembler.toModel(entity);
  }

  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<CompanyMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(service.listOptionsFilter());
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Companies.CanConsult
  public PagedModel<CompanyModel> search(@RequestBody ListQueryDto<CompanyFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Companies.CanCreate
  public CompanyModel create(@Valid @RequestBody CompanyInput body) {
    CompanyEntity entity = service.create(body);
    return modelAssembler.toModel(entity);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Companies.CanChange
  public CompanyModel update(@PathVariable UUID id, @Valid @RequestBody CompanyInput body) {
    CompanyEntity entity = service.update(id, body);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    service.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    service.blockBulk(body.ids());
  }

}