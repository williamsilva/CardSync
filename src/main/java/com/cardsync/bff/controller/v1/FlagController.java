package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.FlagMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.FlagModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.RelationsAcquirerInput;
import com.cardsync.bff.controller.v1.representation.input.RelationsCompanyInput;
import com.cardsync.bff.controller.v1.representation.input.FlagInput;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.FlagFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.service.FlagService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/flags")
public class FlagController {

  private final FlagService service;
  private final FlagModelAssembler modelAssembler;
  private final FlagMinimalModelAssembler minimalModelAssembler;
  private final PagedResourcesAssembler<FlagEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Register.Flags.CanConsult
  public FlagModel getById(@PathVariable UUID id) {
    return modelAssembler.toModel(service.getById(id));
  }

  @GetMapping("/{id}/relations")
  @CheckSecurity.Register.Flags.CanConsult
  public FlagModel getRelations(@PathVariable UUID id) {
    return modelAssembler.toModel(service.getById(id));
  }

  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<FlagMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(service.listOptionsFilter());
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Flags.CanConsult
  public PagedModel<FlagModel> search(@RequestBody ListQueryDto<FlagFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Flags.CanCreate
  public FlagModel create(@Valid @RequestBody FlagInput body) {
    return modelAssembler.toModel(service.create(body));
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Flags.CanChange
  public FlagModel update(@PathVariable UUID id, @Valid @RequestBody FlagInput body) {
    return modelAssembler.toModel(service.update(id, body));
  }

  @PostMapping("/{id}/company-relations")
  @CheckSecurity.Register.Flags.CanManageRelations
  public FlagModel addCompanies(  @PathVariable UUID id, @Valid @RequestBody RelationsCompanyInput body) {
    return modelAssembler.toModel(service.addCompaniesRelations(id, body.companyIds()));
  }

  @DeleteMapping("/{id}/companies/{companyId}")
  @CheckSecurity.Register.Flags.CanManageRelations
  public FlagModel removeCompany(@PathVariable UUID id, @PathVariable UUID companyId) {
    return modelAssembler.toModel(service.removeCompanyRelations(id, companyId));
  }

  @PostMapping("/{id}/acquirer-relations")
  @CheckSecurity.Register.Flags.CanManageRelations
  public FlagModel addAcquirerRelations(@PathVariable UUID id,  @Valid @RequestBody RelationsAcquirerInput body) {
    return modelAssembler.toModel(service.addAcquirerRelations(id, body.items()));
  }

  @DeleteMapping("/{id}/acquirers/{acquirerId}")
  @CheckSecurity.Register.Flags.CanManageRelations
  public FlagModel removeAcquirer(@PathVariable UUID id, @PathVariable UUID acquirerId) {
    return modelAssembler.toModel(service.removeAcquirerRelations(id, acquirerId));
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Flags.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Flags.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Flags.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }
}