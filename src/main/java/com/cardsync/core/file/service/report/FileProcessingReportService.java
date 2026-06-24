package com.cardsync.core.file.service.report;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarDayModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ImportedFileCalendarItemModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileEstablishmentModel;
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
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.AcquirerFileTypeEnum;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankRepository;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.HolidayRepository;
import com.cardsync.domain.repository.ProcessedFileErrorRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.infrastructure.repository.spec.ProcessedFileSpecs;
import com.cardsync.core.config.CardsyncAppProperties;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileProcessingReportService {

  private final ProcessedFileSpecs processedFileSpecs;
  private final FileProcessingProperties fileProcessingProperties;
  private final CardsyncAppProperties cardsyncAppProperties;
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> REDE_REQUIRED_FILE_TYPES = Set.of("EEVC", "EEVD", "EEFI");

  private final BankRepository bankRepository;
  private final HolidayRepository holidayRepository;
  private final AcquirerRepository acquirerRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final EstablishmentRepository establishmentRepository;
  private final ProcessedFileRepository processedFileRepository;
  private final BankingDomicileRepository bankingDomicileRepository;
  private final ProcessedFileErrorRepository processedFileErrorRepository;
  private final com.cardsync.domain.repository.NoFileDayRepository noFileDayRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final CreditOrderRepository creditOrderRepository;

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
    List<BankingDomicileEntity> bankingDomiciles =
      bankingDomicileRepository.findAllForImportedFilesCalendar();
    int calendarYear = selectedMonth.getYear();
    Set<LocalDate> holidays = holidayRepository
      .findActiveForCalendarRange(startDate.minusDays(1), endDate)
      .stream()
      .map(h -> Boolean.TRUE.equals(h.getRecurring())
        ? h.getHolidayDate().withYear(calendarYear)
        : h.getHolidayDate())
      .filter(d -> !d.isBefore(startDate.minusDays(1)) && !d.isAfter(endDate))
      .collect(Collectors.toUnmodifiableSet());

    // Dias marcados como "sem arquivo" (cs_no_file_day): por grupo (ERP/ADQ/BANK) e,
    // quando aplicável, por banco/adquirente específico. Usados para marcar como
    // "completo" exatamente o que não terá arquivo, em vez de cobrar o arquivo faltante.
    List<NoFileDayEntity> noFileDayEntries = noFileDayRepository
      .findAllByNoFileDateBetweenOrderByNoFileDateAsc(startDate.minusDays(1), endDate)
      .stream()
      .filter(day -> day.getStatus() == StatusEnum.ACTIVE)
      .toList();

    Map<LocalDate, List<ProcessedFileEntity>> entitiesByDay = new LinkedHashMap<>();
    startDate.datesUntil(endDate.plusDays(1)).forEach(date -> entitiesByDay.put(date, new ArrayList<>()));

    processedFileRepository.findCalendarFiles(startDate, endDate.plusDays(1)).forEach(file -> {
      if (file.getDateFile() != null) {
        LocalDate fileDate = (file.getGroup() == FileGroupEnum.ADQ || file.getGroup() == FileGroupEnum.BANK)
          ? file.getDateFile().minusDays(1)
          : file.getDateFile();
        if (!fileDate.isBefore(startDate) && !fileDate.isAfter(endDate)) {
          entitiesByDay.computeIfAbsent(fileDate, ignored -> new ArrayList<>()).add(file);
        }
      }
    });

    Map<UUID, Set<UUID>> domicileIdsByProcessedFile = loadDomicileIdsByProcessedFile(entitiesByDay);

    Map<UUID, List<ProcessedFileEstablishmentModel>> establishmentsByFileId =
      loadEstablishmentsByFileId(entitiesByDay, acquirers);

    Map<UUID, List<EstablishmentEntity>> allEstsByAcquirerId = new HashMap<>();
    if (!acquirers.isEmpty()) {
      List<UUID> acquirerIds = acquirers.stream().map(AcquirerEntity::getId).toList();
      establishmentRepository.findAllActiveByAcquirerIds(acquirerIds, StatusEnum.ACTIVE.getCode())
        .forEach(e -> allEstsByAcquirerId.computeIfAbsent(e.getAcquirer().getId(), k -> new ArrayList<>()).add(e));
    }

    List<ImportedFileCalendarDayModel> days = entitiesByDay.entrySet().stream()
      .map(entry -> toCalendarDay(
        entry.getKey(),
        entry.getValue(),
        acquirers,
        banks,
        bankingDomiciles,
        domicileIdsByProcessedFile,
        establishmentsByFileId,
        allEstsByAcquirerId,
        holidays,
        noFileDayEntries
      ))
      .toList();

    int daysWithFiles = (int) days.stream().filter(ImportedFileCalendarDayModel::hasFiles).count();
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    int daysWithoutFiles = (int) days.stream()
      .filter(day -> !day.future() && !day.hasFiles() && day.date().isBefore(today))
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
    List<BankingDomicileEntity> bankingDomiciles,
    Map<UUID, Set<UUID>> domicileIdsByProcessedFile,
    Map<UUID, List<ProcessedFileEstablishmentModel>> establishmentsByFileId,
    Map<UUID, List<EstablishmentEntity>> allEstsByAcquirerId,
    Set<LocalDate> holidays,
    List<com.cardsync.domain.model.NoFileDayEntity> noFileDayEntries
  ) {
    List<ImportedFileCalendarItemModel> files = processedFiles.stream()
      .map(f -> toCalendarItem(f, establishmentsByFileId.getOrDefault(f.getId(), List.of())))
      .toList();

    int erpFiles = countGroup(files, FileGroupEnum.ERP);
    int adqFiles = countGroup(files, FileGroupEnum.ADQ);
    int bankFiles = countGroup(files, FileGroupEnum.BANK);

    ImportedFileGroupStatusModel erpStatus = buildErpStatus(date, processedFiles, erpFiles, noFileDayEntries, holidays);
    ImportedFileGroupStatusModel adqStatus = buildAcquirerStatus(date, processedFiles, acquirers, noFileDayEntries, establishmentsByFileId, allEstsByAcquirerId, holidays);
    ImportedFileGroupStatusModel bankStatus = buildBankStatus(
      date, processedFiles, banks, bankingDomiciles, domicileIdsByProcessedFile, holidays, noFileDayEntries
    );

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

  private ImportedFileGroupStatusModel buildErpStatus(
    LocalDate date,
    List<ProcessedFileEntity> files,
    int erpFiles,
    List<com.cardsync.domain.model.NoFileDayEntity> noFileDayEntries,
    Set<LocalDate> holidays
  ) {
    boolean exempt = date.isBefore(cardsyncAppProperties.getImplantationDate())
      || isErpExemptOnDate(date, noFileDayEntries)
      || isNonBusinessDay(date, holidays);
    int expected = (!exempt && fileProcessingProperties.getCalendar().isErpEnabled()) ? 1 : 0;
    int received = erpFiles > 0 ? 1 : 0;

    List<String> presentFiles = files.stream()
      .filter(file -> file.getGroup() == FileGroupEnum.ERP)
      .map(this::processedFileDisplayName)
      .distinct()
      .sorted()
      .toList();

    List<String> missingFiles = expected > 0 && received == 0
      ? List.of("ERP")
      : List.of();

    ImportedFileEntityStatusModel erpEntity = new ImportedFileEntityStatusModel(
      "ERP",
      erpFiles,
      expected,
      resolveStatus(received, expected),
      fileProcessingProperties.getCalendar().isErpEnabled() ? StatusEnum.ACTIVE.name() : StatusEnum.INACTIVE.name(),
      null,
      missingFiles,
      presentFiles
    );

    return new ImportedFileGroupStatusModel(
      resolveStatus(received, expected),
      erpFiles,
      expected,
      List.of(erpEntity)
    );
  }

  private ImportedFileGroupStatusModel buildAcquirerStatus(
    LocalDate date,
    List<ProcessedFileEntity> files,
    List<AcquirerEntity> acquirers,
    List<com.cardsync.domain.model.NoFileDayEntity> noFileDayEntries,
    Map<UUID, List<ProcessedFileEstablishmentModel>> establishmentsByFileId,
    Map<UUID, List<EstablishmentEntity>> allEstsByAcquirerId,
    Set<LocalDate> holidays
  ) {
    List<ProcessedFileEntity> adqFiles = files.stream()
      .filter(file -> file.getGroup() == FileGroupEnum.ADQ)
      .toList();

    List<ImportedFileEntityStatusModel> entities = acquirers.stream()
      .map(acquirer -> {
        Set<String> expectedTypes = resolveExpectedAcquirerFileTypes(date, acquirer, noFileDayEntries, holidays);
        int expectedFiles = expectedTypes.size();
        int filesReceived = countReceivedAcquirerFiles(adqFiles, acquirer, expectedTypes);
        List<String> missingFiles;
        List<String> presentFiles;
        String status;

        if (isRedeAcquirer(acquirer)) {
          List<ProcessedFileEntity> acquirerFiles = adqFiles.stream()
            .filter(file -> matchesAcquirer(file, acquirer))
            .toList();

          Map<String, ProcessedFileEntity> fileBySubtype = new LinkedHashMap<>();
          acquirerFiles.stream()
            .filter(file -> REDE_REQUIRED_FILE_TYPES.contains(resolveAcquirerSubtype(file)))
            .forEach(file -> fileBySubtype.putIfAbsent(resolveAcquirerSubtype(file), file));

          Set<Integer> foundPvNumbers = fileBySubtype.values().stream()
            .flatMap(file -> establishmentsByFileId.getOrDefault(file.getId(), List.of()).stream())
            .map(ProcessedFileEstablishmentModel::pvNumber)
            .collect(Collectors.toSet());

          List<EstablishmentEntity> allActiveEsts = allEstsByAcquirerId
            .getOrDefault(acquirer.getId(), List.of())
            .stream()
            .filter(e -> isEstablishmentActiveOnDate(e, date))
            .sorted(Comparator.comparingInt(EstablishmentEntity::getPvNumber))
            .toList();

          List<String> present = new ArrayList<>();
          for (String subtype : fileBySubtype.keySet().stream().sorted().toList()) {
            ProcessedFileEntity subtypeFile = fileBySubtype.get(subtype);
            List<ProcessedFileEstablishmentModel> subtypeEsts =
              establishmentsByFileId.getOrDefault(subtypeFile.getId(), List.of());

            Set<Integer> displayPvs = subtypeEsts.isEmpty()
              ? foundPvNumbers
              : subtypeEsts.stream().map(ProcessedFileEstablishmentModel::pvNumber).collect(Collectors.toSet());

            String estsPart = allActiveEsts.stream()
              .filter(e -> displayPvs.contains(e.getPvNumber()))
              .map(e -> "PV " + e.getPvNumber() + (e.getCompany() != null ? " " + e.getCompany().getFantasyName() : ""))
              .collect(Collectors.joining(" | "));

            present.add(estsPart.isEmpty() ? subtype : subtype + " - " + estsPart);
          }
          presentFiles = present;

          List<String> missingEstEntries = allActiveEsts.stream()
            .filter(e -> !foundPvNumbers.contains(e.getPvNumber()))
            .map(e -> "PV " + e.getPvNumber() + (e.getCompany() != null ? " - " + e.getCompany().getFantasyName() : ""))
            .toList();

          List<String> missing = new ArrayList<>();
          Set<String> receivedSubtypes = fileBySubtype.keySet();
          expectedTypes.stream().filter(t -> !receivedSubtypes.contains(t)).sorted().forEach(missing::add);
          missing.addAll(missingEstEntries);
          missingFiles = missing;

          String fileStatus = resolveStatus(filesReceived, expectedFiles);
          status = "complete".equals(fileStatus) && !missingEstEntries.isEmpty() ? "partial" : fileStatus;

        } else {
          missingFiles = resolveMissingAcquirerFiles(adqFiles, acquirer, expectedTypes);
          presentFiles = resolvePresentAcquirerFiles(adqFiles, acquirer);
          status = resolveStatus(filesReceived, expectedFiles);
        }

        return new ImportedFileEntityStatusModel(
          firstNonBlank(acquirer.getFantasyName(), acquirer.getSocialReason(), acquirer.getFileIdentifier()),
          filesReceived,
          expectedFiles,
          status,
          acquirer.getStatus() != null ? acquirer.getStatus().name() : StatusEnum.NULL.name(),
          resolveStatusDate(acquirer.getCreatedAt(), acquirer.getStatusDate()),
          missingFiles,
          presentFiles
        );
      })
      .toList();

    int expected = acquirers.stream()
      .mapToInt(acquirer -> resolveExpectedAcquirerFileTypes(date, acquirer, noFileDayEntries, holidays).size())
      .sum();

    int received = acquirers.stream()
      .mapToInt(acquirer -> {
        Set<String> expectedTypes = resolveExpectedAcquirerFileTypes(date, acquirer, noFileDayEntries, holidays);
        return countReceivedAcquirerFiles(adqFiles, acquirer, expectedTypes);
      })
      .sum();

    return new ImportedFileGroupStatusModel(resolveStatus(received, expected), received, expected, entities);
  }

  private ImportedFileGroupStatusModel buildBankStatus(
    LocalDate date,
    List<ProcessedFileEntity> files,
    List<BankEntity> banks,
    List<BankingDomicileEntity> bankingDomiciles,
    Map<UUID, Set<UUID>> domicileIdsByProcessedFile,
    Set<LocalDate> holidays,
    List<com.cardsync.domain.model.NoFileDayEntity> noFileDayEntries
  ) {
    List<ProcessedFileEntity> bankFiles = files.stream()
      .filter(file -> file.getGroup() == FileGroupEnum.BANK)
      .toList();

    boolean nonBusinessDay = isNonBusinessDay(date, holidays);

    Map<UUID, List<BankingDomicileEntity>> domicilesByBank = bankingDomiciles.stream()
      .filter(domicile -> domicile.getBank() != null && domicile.getBank().getId() != null)
      .collect(Collectors.groupingBy(
        domicile -> domicile.getBank().getId(),
        LinkedHashMap::new,
        Collectors.toList()
      ));

    Set<UUID> receivedDomicileIds = resolveReceivedBankingDomicileIds(
      bankFiles,
      domicileIdsByProcessedFile,
      bankingDomiciles
    );

    List<ImportedFileEntityStatusModel> entities = banks.stream()
      .map(bank -> {
        boolean bankExpectedOnDate = !nonBusinessDay && isBankExpectedOnDate(bank, date);

        List<BankingDomicileEntity> expectedDomiciles = bankExpectedOnDate
          ? domicilesByBank.getOrDefault(bank.getId(), List.of()).stream()
          .filter(domicile -> isBankingDomicileFileRequiredOnDate(date, domicile, noFileDayEntries))
          .toList()
          : List.of();

        int expectedFiles = expectedDomiciles.size();
        int filesReceived = (int) expectedDomiciles.stream()
          .map(BankingDomicileEntity::getId)
          .filter(receivedDomicileIds::contains)
          .count();
        List<String> missingFiles = expectedDomiciles.stream()
          .filter(domicile -> !receivedDomicileIds.contains(domicile.getId()))
          .map(this::formatBankingDomicile)
          .sorted()
          .toList();
        List<String> presentFiles = expectedDomiciles.stream()
          .filter(domicile -> receivedDomicileIds.contains(domicile.getId()))
          .map(this::formatBankingDomicile)
          .distinct()
          .sorted()
          .toList();

        return new ImportedFileEntityStatusModel(
          firstNonBlank(bank.getName(), bank.getCode()),
          filesReceived,
          expectedFiles,
          resolveStatus(filesReceived, expectedFiles),
          bank.getStatus() != null ? bank.getStatus().name() : StatusEnum.NULL.name(),
          resolveStatusDate(bank.getCreatedAt(), bank.getStatusDate()),
          missingFiles,
          presentFiles
        );
      })
      .toList();

    int expected = nonBusinessDay
      ? 0
      : banks.stream()
      .filter(bank -> isBankExpectedOnDate(bank, date))
      .mapToInt(bank -> (int) domicilesByBank.getOrDefault(bank.getId(), List.of()).stream()
        .filter(domicile -> isBankingDomicileFileRequiredOnDate(date, domicile, noFileDayEntries))
        .count())
      .sum();

    // Quantidade de domicílios esperados que foram atendidos. Este número é usado
    // exclusivamente para determinar se o grupo BANK está missing, partial ou complete.
    int receivedDomiciles = nonBusinessDay
      ? 0
      : banks.stream()
      .filter(bank -> isBankExpectedOnDate(bank, date))
      .flatMap(bank -> domicilesByBank.getOrDefault(bank.getId(), List.of()).stream())
      .filter(domicile -> isBankingDomicileFileRequiredOnDate(date, domicile, noFileDayEntries))
      .map(BankingDomicileEntity::getId)
      .filter(receivedDomicileIds::contains)
      .distinct()
      .mapToInt(ignored -> 1)
      .sum();

    // No retorno do calendário, received deve representar arquivos efetivamente
    // processados no dia, da mesma forma que ocorre com ERP e ADQ. A cobertura dos
    // domicílios continua sendo usada somente no cálculo do status do grupo.
    int processedBankFiles = bankFiles.size();

    return new ImportedFileGroupStatusModel(
      resolveStatus(receivedDomiciles, expected),
      processedBankFiles,
      expected,
      entities
    );
  }

  /**
   * Resolve os domicílios atendidos por cada arquivo bancário em três níveis:
   * 1) vínculo direto salvo no ProcessedFile;
   * 2) vínculos existentes nos lançamentos bancários;
   * 3) fallback para arquivos antigos sem lançamento, usando banco + agência/conta
   *    presentes no nome do arquivo e, quando disponível, a empresa do header.
   */
  private Set<UUID> resolveReceivedBankingDomicileIds(
    List<ProcessedFileEntity> bankFiles,
    Map<UUID, Set<UUID>> domicileIdsByProcessedFile,
    List<BankingDomicileEntity> bankingDomiciles
  ) {
    Set<UUID> result = new HashSet<>();

    for (ProcessedFileEntity file : bankFiles) {
      if (file.getBankingDomicile() != null && file.getBankingDomicile().getId() != null) {
        result.add(file.getBankingDomicile().getId());
        continue;
      }

      Set<UUID> fromReleases = file.getId() == null
        ? null
        : domicileIdsByProcessedFile.get(file.getId());
      if (fromReleases != null && !fromReleases.isEmpty()) {
        result.addAll(fromReleases);
        continue;
      }

      inferBankingDomicileFromProcessedFile(file, bankingDomiciles)
        .map(BankingDomicileEntity::getId)
        .ifPresent(result::add);
    }

    return result;
  }

  private java.util.Optional<BankingDomicileEntity> inferBankingDomicileFromProcessedFile(
    ProcessedFileEntity file,
    List<BankingDomicileEntity> bankingDomiciles
  ) {
    if (file == null || file.getFile() == null) return java.util.Optional.empty();

    List<BankingDomicileEntity> candidates = bankingDomiciles.stream()
      .filter(domicile -> domicile.getId() != null)
      .filter(domicile -> domicile.getBank() != null)
      .filter(domicile -> matchesBank(file, domicile.getBank()))
      .filter(domicile -> fileNameContainsDomicile(file.getFile(), domicile))
      .toList();

    if (candidates.size() == 1) return java.util.Optional.of(candidates.getFirst());
    if (candidates.isEmpty()) return java.util.Optional.empty();

    String commercialName = normalize(file.getCommercialName());
    if (commercialName.isBlank()) return java.util.Optional.empty();

    List<BankingDomicileEntity> companyMatches = candidates.stream()
      .filter(domicile -> matchesCompanyName(commercialName, domicile.getCompany()))
      .toList();

    return companyMatches.size() == 1
      ? java.util.Optional.of(companyMatches.getFirst())
      : java.util.Optional.empty();
  }

  private boolean fileNameContainsDomicile(String fileName, BankingDomicileEntity domicile) {
    if (domicile.getAgency() == null || domicile.getCurrentAccount() == null) return false;

    Set<String> numericTokens = java.util.regex.Pattern.compile("\\d+")
      .matcher(fileName)
      .results()
      .map(java.util.regex.MatchResult::group)
      .map(this::stripLeadingZeros)
      .filter(token -> !token.isBlank())
      .collect(Collectors.toSet());

    return numericTokens.contains(String.valueOf(domicile.getAgency()))
      && numericTokens.contains(String.valueOf(domicile.getCurrentAccount()));
  }

  private String stripLeadingZeros(String value) {
    if (value == null || value.isBlank()) return "";
    String normalized = value.replaceFirst("^0+(?!$)", "");
    return normalized.isBlank() ? "0" : normalized;
  }

  private boolean matchesCompanyName(String normalizedCommercialName, CompanyEntity company) {
    if (company == null) return false;

    String fantasyName = normalize(company.getFantasyName());
    String socialReason = normalize(company.getSocialReason());

    return (!fantasyName.isBlank()
      && (normalizedCommercialName.contains(fantasyName) || fantasyName.contains(normalizedCommercialName)))
      || (!socialReason.isBlank()
      && (normalizedCommercialName.contains(socialReason) || socialReason.contains(normalizedCommercialName)));
  }

  /**
   * Um domicílio é esperado somente quando a data consultada estiver dentro do período
   * de vigência da conta, incluindo as datas de abertura e encerramento, estiver ativo
   * naquela data e estiver configurado para enviar arquivo bancário.
   */
  private boolean isBankingDomicileExpectedOnDate(BankingDomicileEntity domicile, LocalDate date) {
    if (domicile == null || date == null) return false;
    if (domicile.getStatus() != StatusEnum.ACTIVE) return false;
    if (domicile.getAccountOpeningDate() != null && date.isBefore(domicile.getAccountOpeningDate())) return false;
    if (domicile.getAccountClosingDate() != null && date.isAfter(domicile.getAccountClosingDate())) return false;
    return true;
  }

  private Map<UUID, Set<UUID>> loadDomicileIdsByProcessedFile(
    Map<LocalDate, List<ProcessedFileEntity>> entitiesByDay
  ) {
    List<UUID> bankProcessedFileIds = entitiesByDay.values().stream()
      .flatMap(List::stream)
      .filter(file -> file.getGroup() == FileGroupEnum.BANK)
      .map(ProcessedFileEntity::getId)
      .filter(java.util.Objects::nonNull)
      .distinct()
      .toList();

    if (bankProcessedFileIds.isEmpty()) return Map.of();

    Map<UUID, Set<UUID>> result = new HashMap<>();
    releasesBankRepository.findProcessedFileBankingDomiciles(bankProcessedFileIds)
      .forEach(row -> {
        UUID processedFileId = (UUID) row[0];
        UUID domicileId = (UUID) row[1];
        result.computeIfAbsent(processedFileId, ignored -> new HashSet<>()).add(domicileId);
      });
    return result;
  }

  private boolean isNonBusinessDay(LocalDate date, Set<LocalDate> holidays) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek == DayOfWeek.SATURDAY
      || dayOfWeek == DayOfWeek.SUNDAY
      || holidays.contains(date);
  }

  /** ERP isento no dia: existe registro NoFileDay do grupo ERP nessa data. */
  private boolean isErpExemptOnDate(LocalDate date, List<com.cardsync.domain.model.NoFileDayEntity> entries) {
    return entries.stream().anyMatch(e ->
      e.getFileGroup() == FileGroupEnum.ERP && date.equals(e.getNoFileDate()));
  }

  /**
   * Retorna os tipos de arquivo que a adquirente ainda deve enviar na data.
   * Cada NoFileDay de ADQ remove somente o tipo informado.
   */
  private Set<String> resolveExpectedAcquirerFileTypes(
    LocalDate date,
    AcquirerEntity acquirer,
    List<NoFileDayEntity> entries,
    Set<LocalDate> holidays
  ) {
    if (isNonBusinessDay(date, holidays)) return Set.of();
    if (!isAcquirerExpectedOnDate(acquirer, date)) return Set.of();

    Set<String> expectedTypes = new HashSet<>(isRedeAcquirer(acquirer)
      ? REDE_REQUIRED_FILE_TYPES
      : Set.of(AcquirerFileTypeEnum.GENERIC.name()));

    entries.stream()
      .filter(entry -> entry.getFileGroup() == FileGroupEnum.ADQ)
      .filter(entry -> date.equals(entry.getNoFileDate()))
      .filter(entry -> entry.getAcquirer() == null
        || (acquirer.getId() != null && acquirer.getId().equals(idOfAcquirer(entry))))
      .forEach(entry -> {
        AcquirerFileTypeEnum exemptType = entry.getAcquirerFileType();
        if (exemptType == null) {
          expectedTypes.clear();
        } else {
          expectedTypes.remove(exemptType.name());
        }
      });

    return Set.copyOf(expectedTypes);
  }

  /**
   * Indica se o domicílio bancário deve enviar arquivo na data.
   * A isenção pode ser geral ou específica para o domicílio e também vale para
   * o dia seguinte, preservando a regra dos arquivos bancários de liquidação.
   */
  private boolean isBankingDomicileFileRequiredOnDate(
    LocalDate date,
    BankingDomicileEntity domicile,
    List<NoFileDayEntity> entries
  ) {
    if (!isBankingDomicileExpectedOnDate(domicile, date)) return false;

    return entries.stream().noneMatch(entry ->
      entry.getFileGroup() == FileGroupEnum.BANK
        && (date.equals(entry.getNoFileDate()) || date.minusDays(1).equals(entry.getNoFileDate()))
        && (entry.getBankingDomicile() == null
        || (domicile.getId() != null && domicile.getId().equals(idOfBankingDomicile(entry))))
    );
  }

  private UUID idOfBankingDomicile(NoFileDayEntity entry) {
    return entry.getBankingDomicile() != null ? entry.getBankingDomicile().getId() : null;
  }

  private UUID idOfAcquirer(NoFileDayEntity entry) {
    return entry.getAcquirer() != null ? entry.getAcquirer().getId() : null;
  }

  private boolean isAcquirerExpectedOnDate(AcquirerEntity acquirer, LocalDate date) {
    if (acquirer.getStatus() != StatusEnum.ACTIVE) return false;
    if (acquirer.getOpeningDate() != null && date.isBefore(acquirer.getOpeningDate())) return false;
    if (acquirer.getClosingDate() != null && date.isAfter(acquirer.getClosingDate())) return false;
    return true;
  }

  private boolean isBankExpectedOnDate(BankEntity bank, LocalDate date) {
    return bank.getStatus() == StatusEnum.ACTIVE;
  }


  private java.time.OffsetDateTime resolveStatusDate(
    java.time.OffsetDateTime createdAt,
    java.time.OffsetDateTime updatedAt
  ) {
    return updatedAt != null ? updatedAt : createdAt;
  }

  /**
   * Para a REDE, conta somente os subtipos obrigatórios distintos. Dessa forma,
   * dois arquivos EEVC não compensam a ausência de EEVD ou EEFI.
   */
  private int countReceivedAcquirerFiles(
    List<ProcessedFileEntity> adqFiles,
    AcquirerEntity acquirer,
    Set<String> expectedTypes
  ) {
    List<ProcessedFileEntity> acquirerFiles = adqFiles.stream()
      .filter(file -> matchesAcquirer(file, acquirer))
      .toList();

    if (expectedTypes.isEmpty()) return 0;

    if (!isRedeAcquirer(acquirer)) {
      return expectedTypes.contains(AcquirerFileTypeEnum.GENERIC.name()) && !acquirerFiles.isEmpty() ? 1 : 0;
    }

    return (int) acquirerFiles.stream()
      .map(this::resolveAcquirerSubtype)
      .filter(expectedTypes::contains)
      .distinct()
      .count();
  }

  private List<String> resolvePresentAcquirerFiles(
    List<ProcessedFileEntity> adqFiles,
    AcquirerEntity acquirer
  ) {
    return adqFiles.stream()
      .filter(file -> matchesAcquirer(file, acquirer))
      .map(this::processedFileDisplayName)
      .distinct()
      .sorted()
      .toList();
  }

  private String processedFileDisplayName(ProcessedFileEntity file) {
    return firstNonBlank(file.getFile(), file.getTypeFile());
  }

  private List<String> resolveMissingAcquirerFiles(
    List<ProcessedFileEntity> adqFiles,
    AcquirerEntity acquirer,
    Set<String> expectedTypes
  ) {
    if (expectedTypes.isEmpty()) return List.of();

    if (!isRedeAcquirer(acquirer)) {
      return countReceivedAcquirerFiles(adqFiles, acquirer, expectedTypes) == 0
        ? List.of(firstNonBlank(acquirer.getFileIdentifier(), acquirer.getFantasyName(), acquirer.getSocialReason()))
        : List.of();
    }

    Set<String> receivedTypes = adqFiles.stream()
      .filter(file -> matchesAcquirer(file, acquirer))
      .map(this::resolveAcquirerSubtype)
      .filter(REDE_REQUIRED_FILE_TYPES::contains)
      .collect(Collectors.toSet());

    return expectedTypes.stream()
      .filter(requiredType -> !receivedTypes.contains(requiredType))
      .sorted()
      .toList();
  }

  private String formatBankingDomicile(BankingDomicileEntity domicile) {
    String agency = domicile.getAgency() != null ? domicile.getAgency().toString() : "?";
    if (domicile.getAgencyDigit() != null && !domicile.getAgencyDigit().isBlank()) {
      agency += "-" + domicile.getAgencyDigit().trim();
    }

    String account = domicile.getCurrentAccount() != null ? domicile.getCurrentAccount().toString() : "?";
    if (domicile.getAccountDigit() != null && !domicile.getAccountDigit().isBlank()) {
      account += "-" + domicile.getAccountDigit().trim();
    }

    String company = domicile.getCompany() != null
      ? firstNonBlank(domicile.getCompany().getFantasyName(), domicile.getCompany().getSocialReason())
      : "Empresa não identificada";

    return "Ag. " + agency + " Cc. " + account + " - " + company;
  }

  private boolean isRedeAcquirer(AcquirerEntity acquirer) {
    return isRedeIdentifier(acquirer.getFileIdentifier())
      || isRedeIdentifier(acquirer.getFantasyName())
      || isRedeIdentifier(acquirer.getSocialReason());
  }

  private boolean isRedeIdentifier(String value) {
    return "REDE".equals(normalize(value));
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

  private ImportedFileCalendarItemModel toCalendarItem(
    ProcessedFileEntity file,
    List<ProcessedFileEstablishmentModel> establishments
  ) {
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
      file.getDateImport(),
      establishments
    );
  }

  private Map<UUID, List<ProcessedFileEstablishmentModel>> loadEstablishmentsByFileId(
    Map<LocalDate, List<ProcessedFileEntity>> entitiesByDay,
    List<AcquirerEntity> acquirers
  ) {
    Map<UUID, LocalDate> fileDateById = new HashMap<>();
    Set<UUID> adqFileIds = new HashSet<>();
    entitiesByDay.forEach((date, files) ->
      files.stream()
        .filter(f -> f.getGroup() == FileGroupEnum.ADQ || f.getGroup() == FileGroupEnum.BANK)
        .forEach(f -> {
          fileDateById.put(f.getId(), date);
          if (f.getGroup() == FileGroupEnum.ADQ) adqFileIds.add(f.getId());
        })
    );

    if (fileDateById.isEmpty()) return Map.of();

    Map<UUID, Set<Integer>> pvNumbersByFileId = new HashMap<>();
    processedFileRepository.findPvNumbersByFileIds(fileDateById.keySet()).forEach(row -> {
      UUID fileId = (UUID) row[0];
      Integer pvNumber = (Integer) row[1];
      pvNumbersByFileId.computeIfAbsent(fileId, k -> new HashSet<>()).add(pvNumber);
    });

    // Fallback para arquivos ADQ processados antes da migration cs_processed_file_pv:
    // busca pvNumbers diretamente em SalesSummary (EEVC/EEVD) e CreditOrder (EEFI).
    Set<UUID> adqWithoutPv = adqFileIds.stream()
      .filter(id -> !pvNumbersByFileId.containsKey(id))
      .collect(Collectors.toSet());
    if (!adqWithoutPv.isEmpty()) {
      salesSummaryRepository.findPvNumbersByProcessedFileIds(adqWithoutPv).forEach(row -> {
        UUID fileId = (UUID) row[0];
        Integer pvNumber = (Integer) row[1];
        pvNumbersByFileId.computeIfAbsent(fileId, k -> new HashSet<>()).add(pvNumber);
      });
      creditOrderRepository.findPvCentralizerByProcessedFileIds(adqWithoutPv).forEach(row -> {
        UUID fileId = (UUID) row[0];
        Integer pvNumber = (Integer) row[1];
        pvNumbersByFileId.computeIfAbsent(fileId, k -> new HashSet<>()).add(pvNumber);
      });
    }

    if (pvNumbersByFileId.isEmpty()) return Map.of();

    Set<Integer> allPvNumbers = pvNumbersByFileId.values().stream()
      .flatMap(Set::stream)
      .collect(Collectors.toSet());
    List<UUID> acquirerIds = acquirers.stream().map(AcquirerEntity::getId).toList();

    // Carrega estabelecimentos ativos em lote; a validação de datas é feita por arquivo
    Map<Integer, EstablishmentEntity> byPvNumber = new HashMap<>();
    establishmentRepository.findActiveByAcquirerIdsAndPvNumbers(
      acquirerIds, allPvNumbers, StatusEnum.ACTIVE.getCode()
    ).forEach(e -> byPvNumber.put(e.getPvNumber(), e));

    Map<UUID, List<ProcessedFileEstablishmentModel>> result = new HashMap<>();
    pvNumbersByFileId.forEach((fileId, pvNums) -> {
      LocalDate fileDate = fileDateById.get(fileId);
      List<ProcessedFileEstablishmentModel> estabs = pvNums.stream()
        .map(byPvNumber::get)
        .filter(Objects::nonNull)
        .filter(e -> isEstablishmentActiveOnDate(e, fileDate))
        .map(e -> new ProcessedFileEstablishmentModel(
          e.getPvNumber(),
          e.getCompany() != null ? e.getCompany().getFantasyName() : null,
          e.getCompany() != null ? e.getCompany().getId() : null
        ))
        .toList();
      if (!estabs.isEmpty()) result.put(fileId, estabs);
    });
    return result;
  }

  private boolean isEstablishmentActiveOnDate(EstablishmentEntity e, LocalDate date) {
    if (date == null) return true;
    LocalDate opening = e.getOpeningDate();
    LocalDate closing = e.getClosingDate();
    if (opening != null && date.isBefore(opening)) return false;
    if (closing != null && date.isAfter(closing)) return false;
    return true;
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
    String typeFile = normalize(file.getTypeFile());
    String searchable = normalize(nullable(file.getTypeFile()) + " " + nullable(file.getFile()));

    if (typeFile.equals("EXTRATO DE MOVIMENTO DE VENDAS") || searchable.contains("EEVC")) {
      return "EEVC";
    }
    if (typeFile.equals("EXTRATO DE MOVIMENTACAO FINANCEIRA") || searchable.contains("EEFI")) {
      return "EEFI";
    }
    if (typeFile.equals("MOVIMENTACAO DIARIA CARTOES DE DEBITO") || searchable.contains("EEVD")) {
      return "EEVD";
    }
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