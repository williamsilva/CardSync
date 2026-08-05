package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.ErpPendingSaleModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileBrowserItemModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileProcessingScheduleStatusModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileUploadItemResultModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileErrorModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ReprocessPendingErpResultModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeTotalizerModel;
import com.cardsync.core.file.erp.service.ErpPendingSaleService;
import com.cardsync.core.file.rede.service.RedeFinancialQueryService;
import com.cardsync.core.file.service.FileBrowserService;
import com.cardsync.core.file.service.FileStorageTask;
import com.cardsync.core.file.service.FileUploadService;
import com.cardsync.core.file.service.report.FileProcessingReportService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.ProcessedFileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/file-processing")
public class FileProcessingController {

  private final FileStorageTask fileStorageTask;
  private final FileUploadService fileUploadService;
  private final FileBrowserService fileBrowserService;
  private final FileProcessingReportService reportService;
  private final ErpPendingSaleService erpPendingSaleService;
  private final RedeFinancialQueryService redeFinancialQueryService;

  @PostMapping("/files/search")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ProcessedFileModel> searchFiles(@RequestBody ListQueryDto<ProcessedFileFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<ProcessedFileModel> page = reportService.search(pageable, body);

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

  @GetMapping("/files/calendar")
  @CheckSecurity.FileProcessing.CanRead
  public ImportedFileCalendarModel importedFilesCalendar(@RequestParam(required = false) YearMonth month) {
    return reportService.importedFilesCalendar(month);
  }

  @PostMapping("/files/totals")
  @CheckSecurity.FileProcessing.CanRead
  public ProcessedFileTotalsModel filesTotals(@RequestBody ListQueryDto<ProcessedFileFilter> body) {
    return reportService.totals(body);
  }

  @GetMapping("/files/{processedFileId}")
  @CheckSecurity.FileProcessing.CanRead
  public ProcessedFileModel find(@PathVariable UUID processedFileId) {
    return reportService.find(processedFileId);
  }

  @GetMapping("/files/{processedFileId}/summary")
  @CheckSecurity.FileProcessing.CanRead
  public ProcessedFileSummaryModel summary(@PathVariable UUID processedFileId) {
    return reportService.summary(processedFileId);
  }

  @GetMapping("/files/{processedFileId}/errors")
  @CheckSecurity.FileProcessing.CanRead
  public List<ProcessedFileErrorModel> listErrors(@PathVariable UUID processedFileId) {
    return reportService.listErrors(processedFileId);
  }

  @GetMapping("/erp/pending-sales")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<ErpPendingSaleModel> listPendingErpSales(Pageable pageable) {
    return toPagedModel(erpPendingSaleService.listPending(pageable));
  }

  @GetMapping("/rede/totalizers")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<RedeTotalizerModel> listRedeTotalizers(Pageable pageable) {
    return toPagedModel(redeFinancialQueryService.listTotalizers(pageable));
  }

  private static <T> PagedModel<T> toPagedModel(Page<T> page) {
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
  
  @GetMapping("/schedules/status")
  @CheckSecurity.FileProcessing.CanRead
  public Map<String, FileProcessingScheduleStatusModel> scheduleStatus() {
    return Map.of(
      "erp", FileProcessingScheduleStatusModel.from(fileStorageTask.erpStatus()),
      "acquirer", FileProcessingScheduleStatusModel.from(fileStorageTask.acquirerStatus()),
      "bank", FileProcessingScheduleStatusModel.from(fileStorageTask.bankStatus())
    );
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @CheckSecurity.FileProcessing.CanProcess
  public List<FileUploadItemResultModel> upload(
    @RequestParam String system,
    @RequestParam("files") MultipartFile[] files
  ) {
    return fileUploadService.upload(system, files);
  }

  @GetMapping("/browse")
  @CheckSecurity.FileProcessing.CanRead
  public List<FileBrowserItemModel> browse(
    @RequestParam String system,
    @RequestParam String folder
  ) {
    return fileBrowserService.list(system, folder);
  }

  @GetMapping("/browse/download")
  @CheckSecurity.FileProcessing.CanRead
  public ResponseEntity<Resource> download(
    @RequestParam String system,
    @RequestParam String folder,
    @RequestParam String path
  ) {
    Resource resource = fileBrowserService.loadForDownload(system, folder, path);

    // "path" pode incluir subpastas (ex.: "2024/arquivo.csv") — o nome pro Content-Disposition
    // é só o último segmento, senão o navegador tenta salvar com a barra no nome do arquivo.
    String displayName = Paths.get(path).getFileName().toString();
    ContentDisposition disposition = ContentDisposition.attachment()
      .filename(displayName, StandardCharsets.UTF_8)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .body(resource);
  }

  @PostMapping("/erp/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<Void> processErp() {
    fileStorageTask.processFileErp();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/erp/reprocess-pending")
  @CheckSecurity.FileProcessing.CanReprocess
  public ReprocessPendingErpResultModel reprocessPendingErpSales() {
    return erpPendingSaleService.reprocessPending();
  }

  @PostMapping("/acquirer/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<Void> processAcquirer() {
    fileStorageTask.processFileAcquirer();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/bank/process")
  @CheckSecurity.FileProcessing.CanProcess
  public ResponseEntity<FileProcessingScheduleStatusModel> processBank() {
    fileStorageTask.processFileBank();
    return ResponseEntity.accepted().body(FileProcessingScheduleStatusModel.from(fileStorageTask.bankStatus()));
  }

}