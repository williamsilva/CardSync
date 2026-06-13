package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.BankMinimalModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.BankMinimalModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.service.BankService;
import lombok.AllArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/banks")
public class BankController {

  private final BankService service;
  private final BankMinimalModelAssembler minimalModelAssembler;
  
  @GetMapping("/options-filter")
  @CheckSecurity.Authenticated
  public CollectionModel<BankMinimalModel> listOptionsFilter() {
    return minimalModelAssembler.toCollectionModel(service.listOptionsFilter());
  }
}