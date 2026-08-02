package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankManualImportResult;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankManualResult;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.file.bank.ManualBankStatementTextImportService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.filter.ReleasesBankManualInput;
import com.cardsync.domain.filter.ReleasesBankManualTextImportInput;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.ReleasesBankService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/releases-bank")
public class ReleasesBanksController {

  private final ReleasesBankService releasesBankService;
  private final ManualBankStatementTextImportService manualBankStatementTextImportService;

  @PostMapping("/search")
  @CheckSecurity.Documents.BankStatement.CanConsult
  public PagedModel<ReleasesBankModel> search(@RequestBody ListQueryDto<ReleasesBankFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ReleasesBankModel> page = releasesBankService.search(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  @PostMapping("/totals")
  @CheckSecurity.Documents.BankStatement.CanConsult
  public ValueTotalsModel totals(@RequestBody ListQueryDto<ReleasesBankFilter> body) {
    return releasesBankService.totals(body);
  }

  @PostMapping("/manual")
  @CheckSecurity.FileProcessing.CanProcess
  public ReleasesBankManualResult createManual(@RequestBody ReleasesBankManualInput body) {
    return releasesBankService.createManual(body);
  }

  @PostMapping("/manual/import-text")
  @CheckSecurity.FileProcessing.CanProcess
  public ReleasesBankManualImportResult importText(@RequestBody ReleasesBankManualTextImportInput body) {
    return manualBankStatementTextImportService.classifyAndCreate(body);
  }

  @DeleteMapping("/manual/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.FileProcessing.CanProcess
  public void deleteManual(@PathVariable UUID id) {
    releasesBankService.deleteManual(id);
  }
}
