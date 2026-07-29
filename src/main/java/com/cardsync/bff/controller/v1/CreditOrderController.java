package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.input.ApplyCreditOrderPreImplantationLinkingRequest;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportPreviewResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.bff.controller.v1.representation.model.transactions.CreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.core.reconciliation.summary.CreditOrderManualService;
import com.cardsync.core.reconciliation.summary.CreditOrderPreImplantationLinkingApplyResult;
import com.cardsync.core.reconciliation.summary.CreditOrderPreImplantationLinkingPreviewResult;
import com.cardsync.core.reconciliation.summary.CreditOrderPreImplantationLinkingService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.CreditOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/credit-order")
public class CreditOrderController {

  private final CreditOrderService creditOrderService;
  private final CreditOrderManualService creditOrderManualService;
  private final CreditOrderPreImplantationLinkingService creditOrderPreImplantationLinkingService;

  @PostMapping("/manual")
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderManualResult createManual(@Valid @RequestBody CreditOrderManualInput body) {
    return creditOrderManualService.create(body);
  }

  @PostMapping(value = "/manual/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderImportPreviewResult previewImportManual(@RequestParam("files") MultipartFile[] files) {
    return creditOrderManualService.previewAcquirerReportImport(files);
  }

  @PostMapping(value = "/manual/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderImportResult importManual(@RequestParam("files") MultipartFile[] files) {
    return creditOrderManualService.importFromAcquirerReport(files);
  }

  /**
   * Prévia do backfill de vínculo de CreditOrder órfãs pré-implantação (rvDate anterior ao
   * go-live) — ver CreditOrderPreImplantationLinkingService. Nunca grava nada.
   */
  @PostMapping("/manual/link-preimplantation/preview")
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderPreImplantationLinkingPreviewResult previewLinkPreImplantation() {
    return creditOrderPreImplantationLinkingService.preview();
  }

  @PostMapping("/manual/link-preimplantation/apply")
  @CheckSecurity.FileProcessing.CanProcess
  public CreditOrderPreImplantationLinkingApplyResult applyLinkPreImplantation(
      @RequestBody(required = false) ApplyCreditOrderPreImplantationLinkingRequest body) {
    return creditOrderPreImplantationLinkingService.apply(body != null ? body.creditOrderIds() : null);
  }

  @PostMapping("/search")
  @CheckSecurity.Documents.CreditOrder.CanConsult
  public PagedModel<CreditOrderModel> search(@RequestBody ListQueryDto<CreditOrderFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<CreditOrderModel> page = creditOrderService.search(pageable, body);

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
  @CheckSecurity.Documents.CreditOrder.CanConsult
  public TransactionTotalsModel totals(@RequestBody ListQueryDto<CreditOrderFilter> body) {
    return creditOrderService.totals(body);
  }
}
