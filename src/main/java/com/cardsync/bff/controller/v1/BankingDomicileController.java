package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.BankingDomicileModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.BankingDomicileInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.BankingDomicileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.service.BankingDomicileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/banking-domiciles")
public class BankingDomicileController {

  private final BankingDomicileModelAssembler modelAssembler;
  private final BankingDomicileService bankingDomicileService;
  private final PagedResourcesAssembler<BankingDomicileEntity> pagedResourcesAssembler;

  @GetMapping("/{id}")
  @CheckSecurity.Register.Companies.CanConsult
  public BankingDomicileModel getById(@PathVariable UUID id) {
    return modelAssembler.toModel(bankingDomicileService.getById(id));
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Companies.CanConsult
  public PagedModel<BankingDomicileModel> search(@RequestBody ListQueryDto<BankingDomicileFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = bankingDomicileService.search(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @CheckSecurity.Register.Companies.CanCreate
  public BankingDomicileModel create(@Valid @RequestBody BankingDomicileInput body) {
    return modelAssembler.toModel(bankingDomicileService.create(body));
  }

  @PutMapping("/{id}")
  @CheckSecurity.Register.Companies.CanChange
  public BankingDomicileModel update(
    @PathVariable UUID id,
    @Valid @RequestBody BankingDomicileInput body
  ) {
    return modelAssembler.toModel(bankingDomicileService.update(id, body));
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    bankingDomicileService.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    bankingDomicileService.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    bankingDomicileService.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    bankingDomicileService.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    bankingDomicileService.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Companies.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    bankingDomicileService.blockBulk(body.ids());
  }
}
