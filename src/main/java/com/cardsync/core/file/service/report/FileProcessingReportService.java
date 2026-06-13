package com.cardsync.core.file.service.report;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarDayModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarItemModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileDayGroupStatusModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileEntityStatusModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileGroupStatusModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileErrorModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileTotalsModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ProcessedFileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.ProcessedFileErrorEntity;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankRepository;
import com.cardsync.domain.repository.HolidayRepository;
import com.cardsync.domain.repository.ProcessedFileErrorRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import com.cardsync.infrastructure.repository.spec.ProcessedFileSpecs;
import com.cardsync.core.file.config.FileProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileProcessingReportService {

  private final ProcessedFileSpecs processedFileSpecs;
  private final FileProcessingProperties fileProcessingProperties;
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

  private final BankRepository bankRepository;
  private final HolidayRepository holidayRepository;
  private final AcquirerRepository acquirerRepository;
  private final ProcessedFileRepository processedFileRepository;
  private final ProcessedFileErrorRepository processedFileErrorRepository;

  @Transactional(readOnly = true)
  public Page<ProcessedFileModel> list(Pageable pageable) {
    return processedFileRepository.findAll(pageable).map(this::toModel);
  }

  @Transactional(readOnly = true)
  public Page<ProcessedFileModel> search(Pageable pageable, ListQueryDto<ProcessedFileFilter> query) {
    Specification<ProcessedFileEntity> filterSpec = processedFileSpecs.fromQueryForTotals(query);
    Specification<ProcessedFileEntity> dataSpec = processedFileSpecs.fromQuery(query);

    long total = processedFileRepository.count(filterSpec);

    List<ProcessedFileModel> content = total == 0
      ? List.of()
      : processedFileRepository.findAll(dataSpec, pageable)
      .stream()
      .map(this::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public ProcessedFileTotalsModel totals(ListQueryDto<ProcessedFileFilter> query) {
    Specification<ProcessedFileEntity> base = processedFileSpecs.fromQueryForTotals(query);

    return new ProcessedFileTotalsModel(
      countWithStatus(base, FileStatusEnum.PROCESSED),
      countWithStatus(base, FileStatusEnum.PROCESSED_WITH_WARNINGS),
      countWithStatus(base, FileStatusEnum.ERROR),
      countWithStatus(base, FileStatusEnum.DUPLICATE),
      countWithStatus(base, FileStatusEnum.INVALID),
      countWithPositive(base, "pendingContractLines"),
      countWithPositive(base, "pendingBusinessContextLines")
    );
  }

  private long countWithStatus(Specification<ProcessedFileEntity> base, FileStatusEnum status) {
    Specification<ProcessedFileEntity> spec = base.and(
      (root, query, cb) -> cb.equal(root.get("status"), status)
    );
    return processedFileRepository.count(spec);
  }

  private long countWithPositive(Specification<ProcessedFileEntity> base, String field) {
    Specification<ProcessedFileEntity> spec = base.and(
      (root, query, cb) -> cb.greaterThan(root.<Integer>get(field), 0)
    );
    return processedFileRepository.count(spec);
  }

  @Transactional(readOnly = true)
  public ImportedFileCalendarModel importedFilesCalendar(YearMonth month) {
    YearMonth selectedMonth = month == null ? YearMonth.now(BUSINESS_ZONE) : month;
    LocalDate startDate = selectedMonth.atDay(1);
    LocalDate endDate = selectedMonth.atEndOfMonth();

    List<AcquirerEntity> acquirers = acquirerRepository.findAllByOrderByFantasyNameAsc();
    List<BankEntity> banks = bankRepository.findAllByOrderByNameAsc();
    Set<LocalDate> holidays = holidayRepository
      .findAllByHolidayDateBetweenOrderByHolidayDateAsc(startDate.minusDays(1), endDate)
      .stream()
      .filter(holiday -> holiday.getStatus() == StatusEnum.ACTIVE)
      .map(HolidayEntity::getHolidayDate)
      .collect(Collectors.toUnmodifiableSet());

    Map<LocalDate, List<ProcessedFileEntity>> entitiesByDay = new LinkedHashMap<>();
    startDate.datesUntil(endDate.plusDays(1)).forEach(date -> entitiesByDay.put(date, new ArrayList<>()));

    processedFileRepository.findCalendarFiles(startDate, endDate).forEach(file -> {
      if (file.getDateFile() != null) {
        entitiesByDay.computeIfAbsent(file.getDateFile(), ignored -> new ArrayList<>()).add(file);
      }
    });

    List<ImportedFileCalendarDayModel> days = entitiesByDay.entrySet().stream()
      .map(entry -> toCalendarDay(entry.getKey(), entry.getValue(), acquirers, banks, holidays))
      .toList();

    int daysWithFiles = (int) days.stream().filter(ImportedFileCalendarDayModel::hasFiles).count();
    int daysWithoutFiles = (int) days.stream()
      .filter(day -> !day.future() && !day.hasFiles())
      .count();
    int totalFiles = days.stream().mapToInt(ImportedFileCalendarDayModel::totalFiles).sum();

    return new ImportedFileCalendarModel(
      selectedMonth,
      startDate,
      endDate,
      daysWithFiles,
      daysWithoutFiles,
      totalFiles,
      days
    );
  }

  private ImportedFileCalendarDayModel toCalendarDay(
    LocalDate date,
    List<ProcessedFileEntity> processedFiles,
    List<AcquirerEntity> acquirers,
    List<BankEntity> banks,
    Set<LocalDate> holidays
  ) {
    List<ImportedFileCalendarItemModel> files = processedFiles.stream()
      .map(this::toCalendarItem)
      .toList();

    int erpFiles = countGroup(files, FileGroupEnum.ERP);
    int adqFiles = countGroup(files, FileGroupEnum.ADQ);
    int bankFiles = countGroup(files, FileGroupEnum.BANK);

    ImportedFileGroupStatusModel erpStatus = buildErpStatus(erpFiles);
    ImportedFileGroupStatusModel adqStatus = buildAcquirerStatus(date, processedFiles, acquirers);
    ImportedFileGroupStatusModel bankStatus = buildBankStatus(date, processedFiles, banks, holidays);

    return new ImportedFileCalendarDayModel(
      date,
      !files.isEmpty(),
      date.isAfter(LocalDate.now(BUSINESS_ZONE)),
      files.size(),
      erpFiles,
      adqFiles,
      bankFiles,
      new ImportedFileDayGroupStatusModel(erpStatus, adqStatus, bankStatus),
      List.copyOf(files)
    );
  }

  private ImportedFileGroupStatusModel buildErpStatus(int erpFiles) {
    int expected = fileProcessingProperties.getCalendar().isErpEnabled() ? 1 : 0;
    int received = erpFiles > 0 ? 1 : 0;
    return new ImportedFileGroupStatusModel(
      resolveStatus(received, expected),
      received,
      expected,
      List.of()
    );
  }

  private ImportedFileGroupStatusModel buildAcquirerStatus(
    LocalDate date,
    List<ProcessedFileEntity> files,
    List<AcquirerEntity> acquirers
  ) {
    List<ProcessedFileEntity> adqFiles = files.stream()
      .filter(file -> file.getGroup() == FileGroupEnum.ADQ)
      .toList();

    List<ImportedFileEntityStatusModel> entities = acquirers.stream()
      .map(acquirer -> {
        int filesReceived = (int) adqFiles.stream()
          .filter(file -> matchesAcquirer(file, acquirer))
          .count();
        boolean expectedOnDate = isAcquirerExpectedOnDate(acquirer, date);
        return new ImportedFileEntityStatusModel(
          firstNonBlank(acquirer.getFantasyName(), acquirer.getSocialReason(), acquirer.getFileIdentifier()),
          filesReceived,
          resolveEntityFileStatus(filesReceived, expectedOnDate),
          acquirer.getStatus() != null ? acquirer.getStatus().name() : StatusEnum.NULL.name(),
          resolveStatusDate(acquirer.getCreatedAt(), acquirer.getStatusDate())
        );
      })
      .toList();

    int expected = (int) acquirers.stream()
      .filter(acquirer -> isAcquirerExpectedOnDate(acquirer, date))
      .count();
    int received = (int) acquirers.stream()
      .filter(acquirer -> isAcquirerExpectedOnDate(acquirer, date))
      .filter(acquirer -> adqFiles.stream().anyMatch(file -> matchesAcquirer(file, acquirer)))
      .count();

    return new ImportedFileGroupStatusModel(resolveStatus(received, expected), received, expected, entities);
  }

  private ImportedFileGroupStatusModel buildBankStatus(
    LocalDate date,
    List<ProcessedFileEntity> files,
    List<BankEntity> banks,
    Set<LocalDate> holidays
  ) {
    List<ProcessedFileEntity> bankFiles = files.stream()
      .filter(file -> file.getGroup() == FileGroupEnum.BANK)
      .toList();

    boolean nonBusinessDay = isBankNonBusinessDay(date, holidays);

    List<ImportedFileEntityStatusModel> entities = banks.stream()
      .map(bank -> {
        int filesReceived = (int) bankFiles.stream()
          .filter(file -> matchesBank(file, bank))
          .count();
        boolean expectedOnDate = !nonBusinessDay && isBankExpectedOnDate(bank, date);
        return new ImportedFileEntityStatusModel(
          firstNonBlank(bank.getName(), bank.getCode()),
          filesReceived,
          resolveEntityFileStatus(filesReceived, expectedOnDate),
          bank.getStatus() != null ? bank.getStatus().name() : StatusEnum.NULL.name(),
          resolveStatusDate(bank.getCreatedAt(), bank.getStatusDate())
        );
      })
      .toList();

    int expected = nonBusinessDay
      ? 0
      : (int) banks.stream()
      .filter(bank -> isBankExpectedOnDate(bank, date))
      .count();

    int received = nonBusinessDay
      ? 0
      : (int) banks.stream()
      .filter(bank -> isBankExpectedOnDate(bank, date))
      .filter(bank -> bankFiles.stream().anyMatch(file -> matchesBank(file, bank)))
      .count();

    return new ImportedFileGroupStatusModel(resolveStatus(received, expected), received, expected, entities);
  }

  private boolean isBankNonBusinessDay(LocalDate date, Set<LocalDate> holidays) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek == DayOfWeek.SUNDAY
      || dayOfWeek == DayOfWeek.MONDAY
      || holidays.contains(date)
      || holidays.contains(date.minusDays(1));
  }

  private boolean isAcquirerExpectedOnDate(AcquirerEntity acquirer, LocalDate date) {
    if (acquirer.getStatus() == StatusEnum.ACTIVE) return true;
    return isBeforeStatusChange(date, acquirer.getStatusDate());
  }

  private boolean isBankExpectedOnDate(BankEntity bank, LocalDate date) {
    if (bank.getStatus() == StatusEnum.ACTIVE) return true;
    return isBeforeStatusChange(date, bank.getStatusDate());
  }

  private boolean isBeforeStatusChange(LocalDate date, java.time.OffsetDateTime statusDate) {
    if (statusDate == null) return false;
    LocalDate changeDate = statusDate.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    return date.isBefore(changeDate);
  }

  private String resolveEntityFileStatus(int filesReceived, boolean expectedOnDate) {
    return filesReceived > 0 || !expectedOnDate ? "complete" : "missing";
  }

  private java.time.OffsetDateTime resolveStatusDate(
    java.time.OffsetDateTime createdAt,
    java.time.OffsetDateTime updatedAt
  ) {
    return updatedAt != null ? updatedAt : createdAt;
  }

  private boolean matchesAcquirer(ProcessedFileEntity file, AcquirerEntity acquirer) {
    String searchable = normalizedSearchText(file);
    return containsIdentifier(searchable, acquirer.getFileIdentifier())
      || containsIdentifier(searchable, acquirer.getFantasyName())
      || containsIdentifier(searchable, acquirer.getSocialReason());
  }

  private boolean matchesBank(ProcessedFileEntity file, BankEntity bank) {
    String searchable = normalizedSearchText(file);
    return containsIdentifier(searchable, bank.getName())
      || containsIdentifier(searchable, bank.getCode())
      || containsIdentifier(searchable, bank.getIspb());
  }

  private String normalizedSearchText(ProcessedFileEntity file) {
    String origin = file.getOriginFile() != null ? file.getOriginFile().getCode() : "";
    return normalize(origin + " " + nullable(file.getTypeFile()) + " " + nullable(file.getFile()));
  }

  private boolean containsIdentifier(String searchable, String identifier) {
    String normalizedIdentifier = normalize(identifier);
    return !normalizedIdentifier.isBlank() && searchable.contains(normalizedIdentifier);
  }

  private String resolveStatus(int received, int expected) {
    if (expected == 0) return "complete";
    if (received == 0) return "missing";
    if (received == expected) return "complete";
    return "partial";
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "Não identificado";
  }

  private String nullable(String value) {
    return value == null ? "" : value;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9]+", " ")
      .trim();
  }

  private int countGroup(List<ImportedFileCalendarItemModel> files, FileGroupEnum group) {
    return (int) files.stream().filter(file -> file.group() == group).count();
  }

  private ImportedFileCalendarItemModel toCalendarItem(ProcessedFileEntity file) {
    String category = resolveCategory(file);
    return new ImportedFileCalendarItemModel(
      file.getId(),
      file.getFile(),
      file.getGroup(),
      category,
      resolveCategoryLabel(file, category),
      file.getTypeFile(),
      file.getOriginFile() != null ? file.getOriginFile().getCode() : null,
      file.getStatus(),
      file.getDateFile(),
      file.getDateImport()
    );
  }

  private String resolveCategory(ProcessedFileEntity file) {
    if (file.getGroup() == FileGroupEnum.ERP) return "ERP";

    if (file.getGroup() == FileGroupEnum.ADQ) {
      String origin = file.getOriginFile() != null ? file.getOriginFile().getCode() : "UNKNOWN";
      String subtype = resolveAcquirerSubtype(file);
      return "ADQ_" + normalizeCategory(origin) + (subtype.isBlank() ? "" : "_" + subtype);
    }

    if (file.getGroup() == FileGroupEnum.BANK) {
      return normalizeCategory(extractBankName(file.getTypeFile()));
    }

    return "OUTROS";
  }

  private String resolveCategoryLabel(ProcessedFileEntity file, String category) {
    if (file.getGroup() == FileGroupEnum.ERP) return "ERP";
    if (file.getGroup() == FileGroupEnum.ADQ) {
      String origin = file.getOriginFile() != null ? file.getOriginFile().getCode() : null;
      String subtype = resolveAcquirerSubtype(file);
      String name = firstNonBlank(origin, "Adquirente não identificada");
      return subtype.isBlank() ? name : name + " " + subtype;
    }
    if (file.getGroup() == FileGroupEnum.BANK) {
      String bank = extractBankName(file.getTypeFile());
      return bank.isBlank() ? "Banco não identificado" : bank;
    }
    return "Outros";
  }

  private String resolveAcquirerSubtype(ProcessedFileEntity file) {
    String searchable = normalize(nullable(file.getTypeFile()) + " " + nullable(file.getFile()));
    if (searchable.contains("EEVC")) return "EEVC";
    if (searchable.contains("EEVD")) return "EEVD";
    if (searchable.contains("EEFI")) return "EEFI";
    return "";
  }

  private String extractBankName(String typeFile) {
    if (typeFile == null || typeFile.isBlank()) return "";
    int separator = typeFile.indexOf(" - ");
    return separator >= 0 ? typeFile.substring(separator + 3).trim() : typeFile.trim();
  }

  private String normalizeCategory(String value) {
    if (value == null || value.isBlank()) return "UNKNOWN";
    return normalize(value).replace(' ', '_');
  }

  @Transactional(readOnly = true)
  public ProcessedFileModel find(UUID processedFileId) {
    return toModel(load(processedFileId));
  }

  @Transactional(readOnly = true)
  public ProcessedFileSummaryModel summary(UUID processedFileId) {
    ProcessedFileEntity file = load(processedFileId);
    return new ProcessedFileSummaryModel(
      file.getId(),
      file.getFile(),
      file.getStatus(),
      file.getStartedAt(),
      file.getFinishedAt(),
      file.getTotalLines(),
      file.getProcessedLines(),
      file.getIgnoredLines(),
      file.getWarningLines(),
      file.getErrorLines(),
      file.getPendingContractLines(),
      file.getPendingBusinessContextLines(),
      file.getStatusMessage(),
      file.getErrorMessage()
    );
  }

  @Transactional(readOnly = true)
  public List<ProcessedFileErrorModel> listErrors(UUID processedFileId) {
    load(processedFileId);
    return processedFileErrorRepository.findByProcessedFile_IdOrderByLineNumberAsc(processedFileId)
      .stream()
      .map(this::toErrorModel)
      .toList();
  }

  private ProcessedFileEntity load(UUID processedFileId) {
    return processedFileRepository.findById(processedFileId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Arquivo processado não encontrado: " + processedFileId));
  }

  private ProcessedFileModel toModel(ProcessedFileEntity file) {
    return new ProcessedFileModel(
      file.getId(),
      file.getFile(),
      file.getOriginFile() != null ? file.getOriginFile().getCode() : null,
      file.getGroup(),
      file.getStatus(),
      file.getDateFile(),
      file.getDateImport(),
      file.getStartedAt(),
      file.getFinishedAt(),
      file.getTotalLines(),
      file.getProcessedLines(),
      file.getIgnoredLines(),
      file.getWarningLines(),
      file.getErrorLines(),
      file.getPendingContractLines(),
      file.getPendingBusinessContextLines(),
      file.getStatusMessage(),
      file.getErrorMessage()
    );
  }

  private ProcessedFileErrorModel toErrorModel(ProcessedFileErrorEntity error) {
    return new ProcessedFileErrorModel(
      error.getId(),
      error.getLineNumber(),
      error.getErrorType(),
      error.getErrorCode(),
      error.getMessage(),
      error.getRawLine(),
      error.getCreatedAt()
    );
  }
}