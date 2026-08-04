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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lê o arquivo CIELO15 (Negociação de Recebíveis/antecipação) do Extrato Eletrônico Cielo v15.15.
 *
 * Registros A (Resumo da negociação), B (Detalhe — uma ou mais URs dentro da negociação) e C
 * (Conta de Recebimento). Diferente do CIELO04 (Registro D/E, ligados por "Chave UR"), aqui não
 * existe chave explícita — a ligação A→B(s)→C é só pela ordem física das linhas no arquivo
 * (confirmado no arquivo de teste oficial da Cielo). Nenhum dos três tem entidade própria no
 * domain model; mapeia pra {@link AnticipationEntity} (já usada por
 * ProcessRedeEeFiService.buildAnticipation pro mesmo conceito no Rede), uma por linha "B".
 *
 * Sem dado real de produção pra validar (este cliente nunca negociou recebíveis com a Cielo —
 * todo histórico de CIELO15 é só Header+Trailer) — layout conferido contra o arquivo de teste
 * oficial da Cielo (ArquivoTeste_ExtratoEletronico/CIELO15D_...TXT).
 *
 * Sem InstallmentAcqEntity/SalesSummaryEntity (mesmo padrão do Rede pra antecipação) e sem vínculo
 * com CIELO03 (isso dependeria do tipo de lançamento "11" no CIELO03, ainda não implementado).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCielo15Service {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");

  private final FileLookupService lookupService;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final MoveFileService moveFileService;
  private final AnticipationRepository anticipationRepository;
  private final ProcessedFileRepository processedFileRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Cielo CIELO15: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, CIELO_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      List<AnticipationEntity> anticipations = new ArrayList<>();
      List<AnticipationEntity> pendingGroup = new ArrayList<>();
      RegistroA currentNegotiation = null;
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
          case "A" -> {
            recognized++;
            currentNegotiation = parseRegistroA(line, lineNumber);
          }
          case "B" -> {
            recognized++;
            if (currentNegotiation == null) {
              ignored++;
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
                "CIELO15_MISSING_NEGOTIATION", "Registro B sem Registro A (resumo da negociação) anterior", line));
              continue;
            }
            pendingGroup.add(buildAnticipation(line, lineNumber, processedFile, currentNegotiation));
          }
          case "C" -> {
            recognized++;
            applyBankingDomicile(line, lineNumber, pendingGroup);
            anticipations.addAll(pendingGroup);
            pendingGroup.clear();
            currentNegotiation = null;
          }
          default -> {
            ignored++;
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CIELO15_UNSUPPORTED_RECORD_TYPE", "Tipo de registro Cielo ainda não mapeado: " + recordType, line));
          }
        }
      }

      if (!pendingGroup.isEmpty()) {
        log.warn("⚠ CIELO15 {}: {} Registro(s) B sem Registro C (conta de recebimento) — negociação sem domicílio bancário.",
          file.getFileName(), pendingGroup.size());
        anticipations.addAll(pendingGroup);
        warnings++;
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
          + ", antecipacoes=" + anticipations.size());

      processedFileRepository.save(processedFile);
      anticipationRepository.saveAll(anticipations);

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      log.info("✅ CIELO15 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CIELO15 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CIELO15 {}: {}", file.getFileName(), safeMessage(ex), ex);
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

  private RegistroA parseRegistroA(String line, int lineNumber) {
    LocalDate paymentDate = FileParserUtils.extractDateLineYearFirst(line, "7-13", lineNumber);
    String negotiationNumber = trim(FileParserUtils.extractStringLine(line, "63-83", lineNumber));
    return new RegistroA(paymentDate, negotiationNumber);
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  AnticipationEntity buildAnticipation(String line, int lineNumber, ProcessedFileEntity processedFile, RegistroA registroA) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "116-126", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "27-30", lineNumber));

    AnticipationEntity anticipation = new AnticipationEntity();
    anticipation.setLineNumber(lineNumber);
    anticipation.setRecordType("B");
    anticipation.setPvNumber(pvNumber);
    anticipation.setCredit(trim(FileParserUtils.extractStringLine(line, "30-33", lineNumber)));
    anticipation.setOriginalDueDate(FileParserUtils.extractDateLineYearFirst(line, "7-13", lineNumber));
    anticipation.setGrossValue(FileParserUtils.extractSignedMoneyLine(line, "33-47", lineNumber));
    anticipation.setReleaseValue(FileParserUtils.extractSignedMoneyLine(line, "47-61", lineNumber));
    anticipation.setDiscountRateValue(FileParserUtils.extractSignedMoneyLine(line, "126-140", lineNumber).abs());
    anticipation.setReleaseDate(registroA.paymentDate());
    anticipation.setNumberRvCorresponding(FileParserUtils.deriveConciliationKey(registroA.negotiationNumber()));
    anticipation.setEstablishment(establishment);
    anticipation.setCompany(establishment != null ? establishment.getCompany() : null);
    anticipation.setAcquirer(acquirer);
    anticipation.setFlag(safeFlag(acquirer, flagCode));
    anticipation.setProcessedFile(processedFile);
    return anticipation;
  }

  private void applyBankingDomicile(String line, int lineNumber, List<AnticipationEntity> pendingGroup) {
    if (pendingGroup.isEmpty()) return;

    String bankCode = trim(FileParserUtils.extractStringLine(line, "1-5", lineNumber));
    Integer agency = FileParserUtils.extractIntegerLine(line, "5-10", lineNumber);
    Integer account = FileParserUtils.extractIntegerLine(line, "10-30", lineNumber);
    CompanyEntity company = pendingGroup.get(0).getCompany();

    BankingDomicileEntity domicile = safeDomicile(bankCode, agency, account, company);
    for (AnticipationEntity anticipation : pendingGroup) {
      anticipation.setBankingDomicile(domicile);
    }
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("CIELO");
    } catch (Exception ex) {
      log.debug("Adquirente Cielo não encontrada durante parsing CIELO15: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing CIELO15: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code);
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Cielo '{}' durante parsing CIELO15: {}", code, ex.getMessage());
      return null;
    }
  }

  /** Mesmo fallback de dígito de agência do CIELO04 (ver ProcessCielo04Service.safeDomicile) — a "Agência" do Registro C tem a mesma largura de 5 chars. */
  private BankingDomicileEntity safeDomicile(String bankCode, Integer agency, Integer currentAccount, CompanyEntity company) {
    try {
      BankingDomicileEntity found = bankingDomicileResolver.resolve(bankCode, agency, currentAccount, company).orElse(null);
      if (found != null || agency == null || agency < 10) return found;
      return bankingDomicileResolver.resolve(bankCode, agency / 10, currentAccount, company).orElse(null);
    } catch (Exception ex) {
      log.debug("Domicílio bancário não encontrado durante parsing CIELO15. banco={}, agência={}, conta={}: {}",
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

  /**
   * Registro "A" (Resumo da negociação) — lido só em memória, não persistido (não há entidade
   * própria). Visibilidade de pacote para permitir teste unitário direto sem contexto Spring.
   */
  record RegistroA(LocalDate paymentDate, String negotiationNumber) {
  }
}
