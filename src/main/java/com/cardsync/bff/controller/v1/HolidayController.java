package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayModel;
import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayRequestModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/holidays")
public class HolidayController {

  private final HolidayService holidayService;

  @GetMapping
  @CheckSecurity.FileProcessing.CanRead
  public List<HolidayModel> list() {
    return holidayService.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public HolidayModel create(@Valid @RequestBody HolidayRequestModel request) {
    return holidayService.create(request);
  }

  @PutMapping("/{id}")
  @CheckSecurity.FileProcessing.CanProcess
  public HolidayModel update(@PathVariable UUID id,  @Valid @RequestBody HolidayRequestModel request) {
    return holidayService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.FileProcessing.CanProcess
  public void delete(@PathVariable UUID id) {
    holidayService.delete(id);
  }
}
