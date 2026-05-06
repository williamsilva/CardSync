package com.cardsync.core.file.bank;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.service.FileLookupService;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Cnab240FileProcessor {

  private static final int STATUS_PENDING = 1;

  /**
   * Limite defensivo compatível com colunas DECIMAL(18,8), mantendo folga para evitar
   * estouro quando um range de layout estiver desalinhado para um banco/segmento.
   */
  private static final BigDecimal SAFE_MONEY_LIMIT = new BigDecimal("999999999.99");

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final BankRepository bankRepository;
  private final CompanyRepository companyRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final ProcessedFileRepository processedFileRepository;
  private final BankStatementClassifierService bankStatementClassifierService;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, Cnab240BankLayout layout) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando leitura CNAB240 {}: {}", layout.getDisplayName(), file.getFileName());
      List<String> lines = Files.readAllLines(file);
      processedFile = createProcessedFile(file, lines.size(), layout);
      processedFile.markProcessing();

      CnabProcessingWarningCollector warningCollector = new CnabProcessingWarningCollector();
      List<ReleasesBankEntity> releases = new ArrayList<>();
      int recognized = 0;
      int ignored = 0;
      int warnings = 0;
      int detailSegmentsIgnored = 0;
      Set<String> unidentified = new TreeSet<>();
      Map<String, Integer> recordTypes = new TreeMap<>();
      Map<String, Integer> detailSegments = new TreeMap<>();

      for (int i = 0; i < lines.size(); i++) {
        int lineNumber = i + 1;
        String line = lines.get(i);
        String recordType = trim(FileParserUtils.extractStringLine(line, "7-8", lineNumber));
        if (recordType != null && !recordType.isBlank()) {
          recordTypes.merge(recordType, 1, Integer::sum);
        }

        if (recordType == null || recordType.isBlank()) {
          ignored++;
          warnings++;
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "CNAB240_EMPTY_RECORD_TYPE", "Linha CNAB240 sem tipo de registro.", line));
          continue;
        }

        switch (recordType) {
          case "0" -> {
            recognized++;
            applyHeaderFile(processedFile, line, lineNumber, layout);
          }
          case "1", "5", "9" -> recognized++;
          case "3" -> {
            recognized++;
            String segmentCode = trim(FileParserUtils.extractStringLine(line, "13-14", lineNumber));
            if (segmentCode != null && !segmentCode.isBlank()) {
              detailSegments.merge(segmentCode, 1, Integer::sum);
            }

            if (!layout.isSupportedDetailSegment(segmentCode)) {
              detailSegmentsIgnored++;
              continue;
            }

            ReleasesBankEntity release = buildRelease(line, lineNumber, processedFile, layout, warningCollector);
            releases.add(release);
            if (release.getCompany() == null || release.getBankingDomicile() == null
              || release.getAcquirer() == null || release.getEstablishment() == null) {
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.LOOKUP,
                "CNAB240_INCOMPLETE_BANK_CONTEXT",
                "Lançamento bancário importado com contexto comercial/bancário incompleto. "
                  + "empresa=" + (release.getCompany() != null)
                  + ", domicilio=" + (release.getBankingDomicile() != null)
                  + ", adquirente=" + (release.getAcquirer() != null)
                  + ", estabelecimento=" + (release.getEstablishment() != null)
                  + ", bandeira=" + (release.getFlag() != null),
                line));
            }
          }
          default -> {
            ignored++;
            warnings++;
            unidentified.add(recordType);
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CNAB240_UNSUPPORTED_RECORD_TYPE", "Tipo de registro CNAB240 ainda não mapeado: " + recordType, line));
          }
        }
      }

      int layoutWarnings = warningCollector.count();
      warnings += layoutWarnings;

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored + detailSegmentsIgnored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + processedFile.getTotalLines()
          + ", reconhecidas=" + recognized
          + ", lancamentos=" + releases.size()
          + ", segmentosDetalheIgnorados=" + detailSegmentsIgnored
          + ", ignoradas=" + ignored
          + ", avisos=" + warnings
          + ", avisosLayout=" + layoutWarnings
          + ", recordTypes=" + recordTypes
          + ", segmentos=" + detailSegments);

      processedFileRepository.save(processedFile);
      releasesBankRepository.saveAll(releases);
      moveFileService.moveAfterCommit(file, paths.getProcessed());

      if (!unidentified.isEmpty()) {
        log.warn("⚠ CNAB240 {} possui tipos de registro não mapeados: {}", file.getFileName(), unidentified);
      }
      if (warningCollector.hasWarnings()) {
        log.warn("⚠ CNAB240 {} finalizado com {} aviso(s) de layout monetário ignorado(s): {}",
          file.getFileName(), warningCollector.count(), warningCollector.summary());
      }
      log.info("✅ CNAB240 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CNAB240 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CNAB240 {}: {}", file.getFileName(), ex.getMessage(), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError());
      throw new IllegalStateException(ex);
    }
  }

  private ProcessedFileEntity createProcessedFile(Path file, int totalLines, Cnab240BankLayout layout) {
    ProcessedFileEntity processedFile = new ProcessedFileEntity();
    processedFile.setOriginFile(lookupService.origin(layout.getOriginCode()));
    processedFile.setGroup(FileGroupEnum.BANK);
    processedFile.setStatus(FileStatusEnum.RECEIVED);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setTypeFile("CNAB240 - " + layout.getDisplayName());
    processedFile.setVersion("CNAB240");
    processedFile.setTotalLines(totalLines);
    return processedFile;
  }

  private void applyHeaderFile(ProcessedFileEntity processedFile, String line, int lineNumber, Cnab240BankLayout layout) {
    processedFile.setCommercialName(FileParserUtils.extractStringLine(line, "72-102", lineNumber));
    processedFile.setDateFile(FileParserUtils.extractDateLine(line, "143-151", lineNumber));
    String version = FileParserUtils.extractStringLine(line, "163-166", lineNumber);
    if (version != null && !version.isBlank()) {
      processedFile.setVersion("CNAB240 v" + version.trim() + " - " + layout.getDisplayName());
    }
  }

  private ReleasesBankEntity buildRelease(
    String line,
    int lineNumber,
    ProcessedFileEntity processedFile,
    Cnab240BankLayout layout,
    CnabProcessingWarningCollector warningCollector
  ) {
    String cnpj = onlyDigits(FileParserUtils.extractStringLine(line, "18-32", lineNumber));
    Integer agency = FileParserUtils.extractIntegerLine(line, layout.getAgencyRange(), lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, layout.getCurrentAccountRange(), lineNumber);
    Integer releaseCategory = FileParserUtils.extractIntegerLine(line, layout.getReleaseCategoryRange(), lineNumber);
    Integer codeBank = FileParserUtils.extractIntegerLine(line, layout.getHistoricalCodeRange(), lineNumber);
    String description = FileParserUtils.extractStringLine(line, layout.getDescriptionRange(), lineNumber);
    String document = FileParserUtils.extractStringLine(line, layout.getDocumentRange(), lineNumber);
    String complement = FileParserUtils.extractStringLine(line, layout.getComplementRange(), lineNumber);
    String fullText = joinText(description, document, complement);

    Optional<CompanyEntity> companyFromCnab = companyByCnpj(cnpj);
    BankStatementClassification classification = bankStatementClassifierService.classify(
      fullText,
      agency,
      currentAccount,
      companyFromCnab.orElse(null),
      layout,
      codeBank
    );

    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setLineNumber(lineNumber);
    release.setBank(resolveBank(layout));
    release.setCompany(classification.getCompany());
    release.setBankingDomicile(classification.getBankingDomicile());
    release.setAcquirer(classification.getAcquirer());
    release.setEstablishment(classification.getEstablishment());
    release.setFlag(classification.getFlag());
    release.setProcessedFile(processedFile);
    release.setReconciliationStatus(STATUS_PENDING);
    release.setNumberReconciliations(0);
    release.setHistoricalCodeBank(codeBank);
    release.setReleaseCategoryCode(releaseCategory);
    release.setReleaseCategory(releaseCategory);
    release.setModalityPaymentBank(classification.getModalityPaymentBank());
    release.setServiceLot(FileParserUtils.extractIntegerLine(line, "3-7", lineNumber));
    release.setRecordType(FileParserUtils.extractStringLine(line, "7-8", lineNumber));
    release.setSequentialNumber(FileParserUtils.extractIntegerLine(line, "8-13", lineNumber));
    release.setSegmentCode(FileParserUtils.extractStringLine(line, "13-14", lineNumber));
    release.setCompanyRegistrationType(FileParserUtils.extractIntegerLine(line, "17-18", lineNumber));
    release.setBankAgreementCode(FileParserUtils.extractStringLine(line, "32-52", lineNumber));
    release.setNatureRelease(FileParserUtils.extractStringLine(line, layout.getNatureRange(), lineNumber));
    release.setTypeComplementRelease(FileParserUtils.extractIntegerLine(line, layout.getComplementTypeRange(), lineNumber));
    release.setComplementRelease(complement);
    release.setCpmfExemptionIdentification(FileParserUtils.extractStringLine(line, layout.getCpmfRange(), lineNumber));
    release.setAccountingDate(FileParserUtils.extractDateLine(line, layout.getAccountingDateRange(), lineNumber));
    release.setReleaseDate(FileParserUtils.extractDateLine(line, layout.getReleaseDateRange(), lineNumber));
    release.setReleaseValue(safeMoney(line, layout.getReleaseValueRange(), lineNumber, "release_value", "release", warningCollector));
    release.setReleaseType(FileParserUtils.extractStringLine(line, layout.getReleaseTypeRange(), lineNumber));
    release.setDescriptionHistoricalBank(description);
    release.setDocumentComplementNumber(document);
    return release;
  }

  private BigDecimal safeMoney(
    String line,
    String range,
    int lineNumber,
    String field,
    String context,
    CnabProcessingWarningCollector warningCollector
  ) {
    BigDecimal value = FileParserUtils.extractBigDecimalLine(line, range, lineNumber);
    if (value == null) return null;
    if (value.abs().compareTo(SAFE_MONEY_LIMIT) > 0) {
      warningCollector.monetaryOverflow(lineNumber, context, field, range, value);
      return null;
    }
    return value;
  }

  private BankEntity resolveBank(Cnab240BankLayout layout) {
    return bankRepository.findByCode(layout.getBankCode())
      .or(() -> bankRepository.findByCodeIgnoreCase(layout.getBankCode()))
      .orElseThrow(() -> new IllegalStateException("Banco não cadastrado para código CNAB: " + layout.getBankCode()));
  }

  private Optional<CompanyEntity> companyByCnpj(String cnpj) {
    if (cnpj == null || cnpj.isBlank()) return Optional.empty();
    return companyRepository.findByCnpj(cnpj);
  }

  private String joinText(String... values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (!result.isEmpty()) result.append(' ');
      result.append(value);
    }
    return result.toString();
  }

  private String onlyDigits(String value) {
    if (value == null) return null;
    return value.replaceAll("\\D", "");
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  private String safeMessage(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
