package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lê o arquivo CIELO09 (Saldo em aberto) do Extrato Eletrônico Cielo v15.15.
 *
 * Snapshot mensal (disponibilizado no dia 1º) de URs ainda não pagas — Header ("0") + Registro D
 * ("UR Agenda", mesmo layout do CIELO04) + Trailer ("9"), sem Registro E (confirmado num arquivo
 * real moderno: só linhas "D" entre header e trailer). Diferente do CIELO04, aqui o Registro D É
 * o registro de conteúdo (não um enriquecimento pra E) — cada linha "D" vira um
 * {@link OpenBalanceEntity} direto.
 *
 * Tabela própria e isolada (ver OpenBalanceEntity) — a "Chave UR" de uma linha aqui vai reaparecer,
 * mais tarde, num CIELO04 real quando a UR for efetivamente liquidada; reusar CreditOrderEntity
 * arriscaria o BankReconciliationService casar um lançamento bancário real contra a ordem
 * "prevista" errada. O manual reforça isso: "Não deve ser utilizado para fins de conciliação
 * transacional".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCielo09Service {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");

  private final FileLookupService lookupService;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final MoveFileService moveFileService;
  private final OpenBalanceRepository openBalanceRepository;
  private final ProcessedFileRepository processedFileRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Cielo CIELO09: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, CIELO_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      List<OpenBalanceEntity> openBalances = new ArrayList<>();
      int recognized = 0;
      int ignored = 0;
      int warnings = 0;

      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        int lineNumber = i + 1;
        String recordType = FileParserUtils.extractStringLine(line, "0-1", lineNumber);
        if (recordType == null || recordType.isBlank()) {
          ignored++;
          continue;
        }

        switch (recordType) {
          case "0" -> {
            recognized++;
            processHeader(line, lineNumber, file, processedFile, lines.size());
          }
          case "9" -> recognized++;
          case "D" -> {
            recognized++;
            openBalances.add(buildOpenBalance(line, lineNumber, processedFile));
          }
          default -> {
            ignored++;
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CIELO09_UNSUPPORTED_RECORD_TYPE", "Tipo de registro Cielo ainda não mapeado: " + recordType, line));
          }
        }
      }

      if (processedFile.getOriginFile() == null) {
        throw new IllegalStateException("Header (registro 0) não encontrado: " + file.getFileName());
      }

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + lines.size()
          + ", reconhecidas=" + recognized
          + ", ignoradas=" + ignored
          + ", avisos=" + warnings
          + ", saldosEmAberto=" + openBalances.size());

      processedFileRepository.save(processedFile);
      openBalanceRepository.saveAll(openBalances);

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      log.info("✅ CIELO09 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CIELO09 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CIELO09 {}: {}", file.getFileName(), safeMessage(ex), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError(), processedFile == null ? null : processedFile.getDateFile());
      throw new IllegalStateException(ex);
    }
  }

  private void processHeader(String line, int lineNumber, Path file, ProcessedFileEntity processedFile, int totalLines) {
    processedFile.setOriginFile(lookupService.origin("CIELO"));
    processedFile.setGroup(FileGroupEnum.ADQ);
    processedFile.setStatus(FileStatusEnum.PROCESSING);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setStartedAt(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setDateFile(FileParserUtils.extractDateLine(line, "11-19", lineNumber));
    processedFile.setTypeFile("CIELO" + FileParserUtils.extractStringLine(line, "47-49", lineNumber));
    processedFile.setPvGroupNumber(FileParserUtils.extractIntegerLine(line, "1-11", lineNumber));
    processedFile.setTotalLines(totalLines);
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  OpenBalanceEntity buildOpenBalance(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "1-11", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "53-56", lineNumber));
    CompanyEntity company = establishment != null ? establishment.getCompany() : null;

    String bankCode = FileParserUtils.extractStringLine(line, "113-117", lineNumber);
    Integer agency = FileParserUtils.extractIntegerLine(line, "117-122", lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, "122-143", lineNumber);
    String chaveUR = trim(FileParserUtils.extractStringLine(line, "151-251", lineNumber));

    OpenBalanceEntity openBalance = new OpenBalanceEntity();
    openBalance.setLineNumber(lineNumber);
    openBalance.setPvNumber(pvNumber);
    openBalance.setRvNumber(FileParserUtils.deriveConciliationKey(chaveUR));
    openBalance.setNumberOfReleases(FileParserUtils.extractIntegerLine(line, "143-149", lineNumber));
    openBalance.setSettlementType(FileParserUtils.extractIntegerLine(line, "56-59", lineNumber));
    openBalance.setPaymentStatus(FileParserUtils.extractIntegerLine(line, "69-71", lineNumber));
    openBalance.setLaunchType(trim(FileParserUtils.extractStringLine(line, "149-151", lineNumber)));
    openBalance.setOpenBalanceIndicator(trim(FileParserUtils.extractStringLine(line, "318-319", lineNumber)));
    openBalance.setPaymentDate(FileParserUtils.extractDateLine(line, "267-275", lineNumber));
    openBalance.setOriginalDueDate(FileParserUtils.extractDateLine(line, "283-291", lineNumber));
    openBalance.setGrossValue(FileParserUtils.extractSignedMoneyLine(line, "71-85", lineNumber));
    openBalance.setLiquidValue(FileParserUtils.extractSignedMoneyLine(line, "99-113", lineNumber));
    openBalance.setAcquirer(acquirer);
    openBalance.setEstablishment(establishment);
    openBalance.setCompany(company);
    openBalance.setFlag(safeFlag(acquirer, flagCode));
    openBalance.setBankingDomicile(safeDomicile(bankCode, agency, currentAccount, company));
    openBalance.setProcessedFile(processedFile);
    return openBalance;
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("CIELO");
    } catch (Exception ex) {
      log.debug("Adquirente Cielo não encontrada durante parsing CIELO09: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing CIELO09: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code);
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Cielo '{}' durante parsing CIELO09: {}", code, ex.getMessage());
      return null;
    }
  }

  /** Mesmo fallback de dígito de agência do CIELO04/15 (ver ProcessCielo04Service.safeDomicile). */
  private BankingDomicileEntity safeDomicile(String bankCode, Integer agency, Integer currentAccount, CompanyEntity company) {
    try {
      BankingDomicileEntity found = bankingDomicileResolver.resolve(bankCode, agency, currentAccount, company).orElse(null);
      if (found != null || agency == null || agency < 10) return found;
      return bankingDomicileResolver.resolve(bankCode, agency / 10, currentAccount, company).orElse(null);
    } catch (Exception ex) {
      log.debug("Domicílio bancário não encontrado durante parsing CIELO09. banco={}, agência={}, conta={}: {}",
        bankCode, agency, currentAccount, ex.getMessage());
      return null;
    }
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
