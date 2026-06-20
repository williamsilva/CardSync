package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.BankMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.BankModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.BankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.service.BankService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/banks")
public class BankController {

  private final BankService bankService;
  private final BankModelAssembler modelAssembler;
  private final BankMinimalModelAssembler minimalModelAssembler;
  private final PagedResourcesAssembler<BankEntity> pagedResourcesAssembler;

  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<BankMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(bankService.listOptionsFilter());
  }

  @PostMapping("/search")
  @CheckSecurity.Register.Banks.CanConsult
  public PagedModel<BankModel> search(@RequestBody ListQueryDto<BankFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = bankService.search(pageable, body);

    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    bankService.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    bankService.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    bankService.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    bankService.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    bankService.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Banks.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    bankService.blockBulk(body.ids());
  }
}