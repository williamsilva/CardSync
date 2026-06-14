package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.NoFileDayModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.AcquirerInput;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.AcquirerModel;
import com.cardsync.bff.controller.v1.representation.model.nofileday.NoFileDayModel;
import com.cardsync.bff.controller.v1.representation.model.nofileday.NoFileDayRequestModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.domain.service.NoFileDayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/no-file-days")
public class NoFileDayController {

  private final NoFileDayService noFileDayService;
  private final NoFileDayModelAssembler modelAssembler;
  private final PagedResourcesAssembler<NoFileDayEntity> pagedResourcesAssembler;

  @PostMapping("/search")
  @CheckSecurity.Register.Companies.CanConsult
  public PagedModel<NoFileDayModel> search(@RequestBody ListQueryDto<NoFileDayFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = noFileDayService.search(pageable, body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public NoFileDayModel create(@Valid @RequestBody NoFileDayRequestModel request) {
    return modelAssembler.toModel(noFileDayService.create(request));
  }

  @PutMapping("/{id}")
  @CheckSecurity.FileProcessing.CanProcess
  public NoFileDayModel update(@PathVariable UUID id, @Valid @RequestBody NoFileDayRequestModel request) {
    return modelAssembler.toModel(noFileDayService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.FileProcessing.CanProcess
  public void delete(@PathVariable UUID id) {
    noFileDayService.delete(id);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    noFileDayService.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    noFileDayService.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    noFileDayService.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    noFileDayService.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    noFileDayService.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    noFileDayService.blockBulk(body.ids());
  }
}