package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.AcquirerMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.EstablishmentMinimalModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.FlagMinimalModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.service.ContractLookupService;
import lombok.AllArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/contracts/lookups")
public class ContractLookupController {

  private final ContractLookupService service;
  private final FlagMinimalModelAssembler flagMinimalModelAssembler;
  private final AcquirerMinimalModelAssembler acquirerMinimalModelAssembler;
  private final EstablishmentMinimalModelAssembler establishmentMinimalModelAssembler;

  @GetMapping("/acquirers")
  @CheckSecurity.Authenticated
  public CollectionModel<AcquirerMinimalModel> listAcquirersByCompany(@RequestParam UUID companyId) {
    return acquirerMinimalModelAssembler.toCollectionModel(service.listAcquirersByCompany(companyId));
  }

  @GetMapping("/establishments")
  @CheckSecurity.Authenticated
  public CollectionModel<EstablishmentMinimalModel> listEstablishments(
    @RequestParam UUID companyId, @RequestParam UUID acquirerId) {
    return establishmentMinimalModelAssembler.toCollectionModel(
      service.listEstablishmentsByCompanyAndAcquirer(companyId, acquirerId)
    );
  }

  @GetMapping("/flags")
  @CheckSecurity.Authenticated
  public CollectionModel<FlagMinimalModel> listFlags(
    @RequestParam UUID companyId, @RequestParam UUID acquirerId) {
    return flagMinimalModelAssembler.toCollectionModel(
      service.listFlagsByCompanyAndAcquirer(companyId, acquirerId)
    );
  }
}
