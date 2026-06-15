package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.HolidayModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayModel;
import com.cardsync.bff.controller.v1.representation.input.HolidayInput;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.HolidayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.service.HolidayService;
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
@RequestMapping("/bff/v1/holidays")
public class HolidayController {

  private final HolidayService holidayService;
  private final HolidayModelAssembler modelAssembler;
  private final PagedResourcesAssembler<HolidayEntity> pagedResourcesAssembler;

  @PostMapping("/search")
  @CheckSecurity.Register.Companies.CanConsult
  public PagedModel<HolidayModel> search(@RequestBody ListQueryDto<HolidayFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = holidayService.search(pageable, body);

    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public HolidayModel create(@Valid @RequestBody HolidayInput request) {
    return modelAssembler.toModel(holidayService.create(request));
  }

  @PutMapping("/{id}")
  @CheckSecurity.FileProcessing.CanProcess
  public HolidayModel update(@PathVariable UUID id,  @Valid @RequestBody HolidayInput request) {
    return modelAssembler.toModel(holidayService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.FileProcessing.CanProcess
  public void delete(@PathVariable UUID id) {
    holidayService.delete(id);
  }

  @PostMapping("/{id}/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activate(@PathVariable UUID id) {
    holidayService.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id) {
    holidayService.deactivate(id);
  }

  @PostMapping("/{id}/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void block(@PathVariable UUID id) {
    holidayService.block(id);
  }

  @PostMapping("/activate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    holidayService.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body) {
    holidayService.deactivateBulk(body.ids());
  }

  @PostMapping("/block")
  @CheckSecurity.Register.Acquirers.CanActiveOrInactive
  public void blockBulk(@Valid @RequestBody ListIdsInput body) {
    holidayService.blockBulk(body.ids());
  }
}