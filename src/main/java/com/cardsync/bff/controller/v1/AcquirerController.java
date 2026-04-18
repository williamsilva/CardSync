package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.AcquirerMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.AcquirerModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.AcquirerInput;
import com.cardsync.bff.controller.v1.representation.input.RelationsCompanyInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.input.RelationsEstablishmentInput;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
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
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/acquirer")
public class AcquirerController {

  private final AcquirerService service;
  private final AcquirerModelAssembler modelAssembler;
  private final AcquirerMinimalModelAssembler minimalModelAssembler;
  private final PagedResourcesAssembler<AcquirerEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Register.Acquirers.CanConsult
  public AcquirerModel getById(@PathVariable UUID id) {
    AcquirerEntity entity = service.getById(id);
    return modelAssembler.toModel(entity);
  }

  @GetMapping("/{id}/relations")
  @CheckSecurity.Register.Acquirers.CanConsult
  public AcquirerModel getRelations(@PathVariable UUID id) {
    return modelAssembler.toModel(service.getById(id));
  }

  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<AcquirerMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(service.listOptionsFilter());
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Acquirers.CanConsult
  public PagedModel<AcquirerModel> search(@RequestBody ListQueryDto<AcquirerFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Acquirers.CanCreate
  public AcquirerModel create(@Valid @RequestBody AcquirerInput body) {
    AcquirerEntity entity = service.create(body);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/{id}/company-relations")
  @CheckSecurity.Register.Acquirers.CanManageRelations
  public AcquirerModel addCompanies(@PathVariable UUID id, @Valid @RequestBody RelationsCompanyInput body) {
    return modelAssembler.toModel(service.addCompaniesRelations(id, body.companyIds()));
  }

  @DeleteMapping("/{id}/companies/{companyId}")
  @CheckSecurity.Register.Acquirers.CanManageRelations
  public AcquirerModel removeCompany(@PathVariable UUID id, @PathVariable UUID companyId) {
    return modelAssembler.toModel(service.removeCompanyRelations(id, companyId));
  }

  @PostMapping("/{id}/establishment-relations")
  @CheckSecurity.Register.Acquirers.CanManageRelations
  public AcquirerModel addEstablishmentRelations(
    @PathVariable UUID id, @Valid @RequestBody RelationsEstablishmentInput body
  ) {
    return modelAssembler.toModel(service.addEstablishmentRelations(id, body.items()));
  }

  @DeleteMapping("/{id}/establishments/{establishmentId}")
  @CheckSecurity.Register.Acquirers.CanManageRelations
  public AcquirerModel removeEstablishment(@PathVariable UUID id, @PathVariable UUID establishmentId) {
    return modelAssembler.toModel(service.removeEstablishmentRelations(id, establishmentId));
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Acquirers.CanChange
  public AcquirerModel update(@PathVariable UUID id, @Valid @RequestBody AcquirerInput body) {
    AcquirerEntity entity = service.update(id, body);
    return modelAssembler.toModel(entity);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    service.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    service.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    service.blockBulk(body.ids());
  }
}
