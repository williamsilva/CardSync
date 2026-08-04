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

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lê o arquivo CIELO04 (Liquidação/Pagamento) do Extrato Eletrônico Cielo v15.15.
 *
 * Compartilha o mesmo Registro E do CIELO03 (ver ProcessCielo03Service), mas aqui ele representa o
 * pagamento efetivado, não a captura da venda. O Registro D ("UR Agenda") carrega a info de
 * liquidação que o E não tem por si só — banco/agência/conta e data de pagamento — e se liga ao(s)
 * Registro(s) E correspondente(s) pela "Chave UR" + "Tipo de lançamento" (manual pág. 22-24). Não
 * existe entidade própria pra "UR Agenda" no domain model; o Registro D é lido só em memória, como
 * enriquecimento dos CreditOrderEntity montados a partir do Registro E.
 *
 * Fase 2: só tipo de lançamento "01"/"02"/"03" (venda débito/crédito/parcelada), mesmo limite do
 * CIELO03. rvNumber é derivado da "Chave UR" via FileParserUtils.deriveConciliationKey — a mesma
 * fórmula usada em ProcessCielo03Service.buildTransaction — para que o CreditOrderEntity aqui
 * criado case, por valor, com o InstallmentAcqEntity da venda original (ver
 * BankReconciliationService.propagateCreditOrdersToInstallments).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCielo04Service {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");
  private static final Set<String> SALE_LAUNCH_TYPES = Set.of("01", "02", "03");
  private static final int STATUS_PENDING = 1;

  private final FileLookupService lookupService;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final MoveFileService moveFileService;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final ProcessedFileRepository processedFileRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Cielo CIELO04: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, CIELO_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      Map<String, RegistroD> urByKey = collectUrAgenda(lines);

      List<CreditOrderEntity> orders = new ArrayList<>();
      Map<String, Integer> launchTypeCounts = new TreeMap<>();
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
          case "9", "D" -> recognized++;
          case "E" -> {
            recognized++;
            String launchType = trim(FileParserUtils.extractStringLine(line, "27-29", lineNumber));
            launchTypeCounts.merge(launchType == null ? "?" : launchType, 1, Integer::sum);
            if (!SALE_LAUNCH_TYPES.contains(launchType)) {
              ignored++;
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
                "CIELO04_UNSUPPORTED_LAUNCH_TYPE", "Tipo de lançamento Cielo ainda não suportado: " + launchType, line));
              continue;
            }

            String chaveUR = trim(FileParserUtils.extractStringLine(line, "29-129", lineNumber));
            RegistroD registroD = urByKey.get(urKey(chaveUR, launchType));
            if (registroD == null) {
              ignored++;
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
                "CIELO04_MISSING_UR_AGENDA", "Registro D (UR Agenda) não encontrado para a chave UR desta linha", line));
              continue;
            }

            orders.add(buildCreditOrder(line, lineNumber, processedFile, launchType, chaveUR, registroD));
          }
          default -> {
            ignored++;
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CIELO04_UNSUPPORTED_RECORD_TYPE", "Tipo de registro Cielo ainda não mapeado: " + recordType, line));
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
          + ", ordensCredito=" + orders.size()
          + ", tiposLancamento=" + launchTypeCounts);

      processedFileRepository.save(processedFile);
      creditOrderRepository.saveAll(orders);

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      log.info("✅ CIELO04 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CIELO04 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CIELO04 {}: {}", file.getFileName(), safeMessage(ex), ex);
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

  private Map<String, RegistroD> collectUrAgenda(List<String> lines) {
    Map<String, RegistroD> urByKey = new HashMap<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int lineNumber = i + 1;
      if (!"D".equals(FileParserUtils.extractStringLine(line, "0-1", lineNumber))) continue;

      String chaveUR = trim(FileParserUtils.extractStringLine(line, "151-251", lineNumber));
      String launchType = trim(FileParserUtils.extractStringLine(line, "149-151", lineNumber));
      RegistroD registroD = new RegistroD(
        FileParserUtils.extractStringLine(line, "113-117", lineNumber),
        FileParserUtils.extractIntegerLine(line, "117-122", lineNumber),
        FileParserUtils.extractIntegerLine(line, "122-143", lineNumber),
        FileParserUtils.extractDateLine(line, "267-275", lineNumber),
        FileParserUtils.extractIntegerLine(line, "56-59", lineNumber),
        FileParserUtils.extractIntegerLine(line, "69-71", lineNumber)
      );
      urByKey.put(urKey(chaveUR, launchType), registroD);
    }
    return urByKey;
  }

  private String urKey(String chaveUR, String launchType) {
    return (chaveUR == null ? "" : chaveUR) + "|" + (launchType == null ? "" : launchType);
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  CreditOrderEntity buildCreditOrder(String line, int lineNumber, ProcessedFileEntity processedFile, String launchType, String chaveUR, RegistroD registroD) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "1-11", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "11-14", lineNumber));
    CompanyEntity company = establishment != null ? establishment.getCompany() : null;

    BigDecimal grossValue = FileParserUtils.extractSignedMoneyLine(line, "260-274", lineNumber);
    BigDecimal liquidValue = FileParserUtils.extractSignedMoneyLine(line, "274-288", lineNumber);
    BigDecimal discountValue = FileParserUtils.extractSignedMoneyLine(line, "288-302", lineNumber).abs();

    CreditOrderEntity order = new CreditOrderEntity();
    order.setLineNumber(lineNumber);
    order.setRecordType(launchType);
    order.setLaunchType(launchType);
    order.setPvCentralizer(pvNumber);
    order.setRvNumber(FileParserUtils.deriveConciliationKey(chaveUR));
    Integer parcela = FileParserUtils.extractIntegerLine(line, "17-19", lineNumber);
    order.setInstallmentNumber(resolveCurrentInstallment(launchType, parcela));
    order.setInstallmentTotal(FileParserUtils.extractIntegerLine(line, "19-21", lineNumber));
    order.setReleaseValue(liquidValue);
    order.setGrossRvValue(grossValue);
    order.setDiscountRateValue(discountValue);
    OffsetDateTime saleDate = FileParserUtils.extractOffsetDateTimeLine(line, lineNumber, "565-573", "470-476");
    order.setReleaseDate(registroD.paymentDate());
    order.setRvDate(saleDate != null ? saleDate.toLocalDate() : null);
    order.setCreditOrderDate(processedFile.getDateFile());
    order.setTransactionType(registroD.settlementType());
    order.setCreditStatus(registroD.paymentStatus());
    order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    order.setSalesSummaryStatus(StatusReconciliationEnum.PENDING);
    order.setReconciliationStatus(STATUS_PENDING);
    order.setSalesSummary(safeSalesSummary(acquirer, pvNumber, order.getRvNumber()));
    order.setProcessedFile(processedFile);
    order.setAcquirer(acquirer);
    order.setFlag(safeFlag(acquirer, flagCode));
    order.setCompany(company);
    order.setBankingDomicile(safeDomicile(registroD.bankCode(), registroD.agency(), registroD.currentAccount(), company));
    return order;
  }

  /**
   * Número da parcela ATUAL (campo "Parcela") — mesma regra de ProcessCielo03Service, precisa
   * bater com o installment que a venda original gerou em InstallmentAcqEntity pra o
   * BankReconciliationService conseguir casar os dois por valor (acquirer + rvNumber + installment).
   */
  private Integer resolveCurrentInstallment(String launchType, Integer parcela) {
    if (!"03".equals(launchType)) return 1;
    return parcela == null || parcela <= 0 ? 1 : parcela;
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("CIELO");
    } catch (Exception ex) {
      log.debug("Adquirente Cielo não encontrada durante parsing CIELO04: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing CIELO04: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  /** Espelha ProcessRedeEeFiService.safeSalesSummary — resolve o resumo criado pela venda original no CIELO03. */
  private SalesSummaryEntity safeSalesSummary(AcquirerEntity acquirer, Integer pvNumber, Integer rvNumber) {
    if (acquirer == null || acquirer.getId() == null || pvNumber == null || rvNumber == null) return null;
    return salesSummaryRepository
      .findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirer.getId(), pvNumber, rvNumber)
      .orElse(null);
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code);
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Cielo '{}' durante parsing CIELO04: {}", code, ex.getMessage());
      return null;
    }
  }

  /**
   * O campo "Agência" da Cielo (5 dígitos, posição 118-122 do manual) traz um dígito extra ao
   * final que BankingDomicileResolver não sabe descartar (ele só separa dígito verificador da
   * CONTA, não da agência) — ex.: arquivo "86390" x cadastro agência=8639 sem dígito. Tenta a
   * agência bruta primeiro e, se não achar, a mesma sem o último caractere.
   */
  private BankingDomicileEntity safeDomicile(String bankCode, Integer agency, Integer currentAccount, CompanyEntity company) {
    try {
      BankingDomicileEntity found = bankingDomicileResolver.resolve(bankCode, agency, currentAccount, company).orElse(null);
      if (found != null || agency == null || agency < 10) return found;
      return bankingDomicileResolver.resolve(bankCode, agency / 10, currentAccount, company).orElse(null);
    } catch (Exception ex) {
      log.debug("Domicílio bancário não encontrado durante parsing CIELO04. banco={}, agência={}, conta={}: {}",
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
   * Registro "D" (UR Agenda) — lido só em memória, não persistido (não há entidade própria).
   * Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring.
   */
  record RegistroD(String bankCode, Integer agency, Integer currentAccount, LocalDate paymentDate,
                    Integer settlementType, Integer paymentStatus) {
  }
}
