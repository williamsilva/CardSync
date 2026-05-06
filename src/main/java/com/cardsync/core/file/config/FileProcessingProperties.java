package com.cardsync.core.file.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@Configuration
@ToString
@ConfigurationProperties(prefix = "file-processing")
public class FileProcessingProperties {

  private String basePath;
  private String baseAcquirer;
  private String baseBank;

  private Systems systems = new Systems();
  private Scheduler scheduler = new Scheduler();
  private Erp erp = new Erp();
  private Reconciliation reconciliation = new Reconciliation();

  @Getter
  @Setter
  @ToString
  public static class Systems {

    private FilePaths erp = new FilePaths();

    /**
     * Compatibilidade com formato antigo:
     *
     * file-processing.systems.rede
     *
     * O novo formato recomendado é:
     *
     * file-processing.systems.acquirer.rede
     */
    private FilePaths rede = new FilePaths();

    /**
     * Novo agrupador de adquirentes:
     *
     * file-processing.systems.acquirer.rede
     */
    private Acquirer acquirer = new Acquirer();

    /**
     * Agrupador bancário:
     *
     * file-processing.systems.bank.itau
     * file-processing.systems.bank.santander
     * file-processing.systems.bank.bradesco
     */
    private Bank bank = new Bank();

    /**
     * Compatibilidade com a configuração atual, onde reconciliation está
     * dentro de systems.
     */
    private Reconciliation reconciliation;

    public FilePaths byName(String system) {
      if (system == null) return null;

      return switch (system.trim().toLowerCase(Locale.ROOT)) {
        case "erp" -> erp;
        case "rede" -> redePath();
        default -> null;
      };
    }

    public FilePaths redePath() {
      FilePaths nestedRede = acquirer != null ? acquirer.getRede() : null;
      if (hasInput(nestedRede)) {
        return nestedRede;
      }

      return rede;
    }

    private boolean hasInput(FilePaths paths) {
      return paths != null
        && paths.getInput() != null
        && !paths.getInput().isBlank();
    }
  }

  @Getter
  @Setter
  @ToString
  public static class Acquirer {

    private FilePaths rede = new FilePaths();

    public Map<String, FilePaths> enabledAcquirers() {
      Map<String, FilePaths> result = new LinkedHashMap<>();
      if (hasInput(rede)) result.put("rede", rede);
      return result;
    }

    private boolean hasInput(FilePaths paths) {
      return paths != null
        && paths.getInput() != null
        && !paths.getInput().isBlank();
    }
  }

  @Getter
  @Setter
  @ToString
  public static class Bank {

    private FilePaths itau = new FilePaths();
    private FilePaths santander = new FilePaths();
    private FilePaths bradesco = new FilePaths();

    public Map<String, FilePaths> enabledBanks() {
      Map<String, FilePaths> result = new LinkedHashMap<>();
      if (hasInput(itau)) result.put("itau", itau);
      if (hasInput(santander)) result.put("santander", santander);
      if (hasInput(bradesco)) result.put("bradesco", bradesco);
      return result;
    }

    private boolean hasInput(FilePaths paths) {
      return paths != null
        && paths.getInput() != null
        && !paths.getInput().isBlank();
    }
  }

  @Getter
  @Setter
  @ToString
  public static class FilePaths {

    private String log;
    private String input;
    private String error;
    private String invalid;
    private String processed;
    private String duplicate;
  }

  @Getter
  @Setter
  @ToString
  public static class Erp {

    /**
     * Fallback para layouts ERP/MultiVendas que não trazem CNPJ/PV em cada linha.
     * Configure com o PV real já cadastrado no CardSync.
     */
    private Integer defaultPvNumber;
    private String defaultCompanyCnpj;
    private String defaultCompanyName;
    private String defaultEstablishmentName;
    private String defaultCommercialName = "ERP";
    private Integer defaultPvGroupNumber;
  }

  @Getter
  @Setter
  @ToString
  public static class Scheduler {

    /**
     * Liga/desliga geral do agendamento.
     * Os endpoints manuais continuam funcionando mesmo quando o scheduler estiver desabilitado.
     */
    private boolean enabled = false;

    /** Liga/desliga o agendamento do ERP. */
    private boolean erpEnabled = true;

    /** Liga/desliga o agendamento da Rede/adquirentes. */
    private boolean redeEnabled = true;

    /** Liga/desliga o agendamento bancário. */
    private boolean bankEnabled = true;

    /** Cron de 6 campos do Spring. Default: a cada 5 minutos. */
    private String erpCron = "0 0/5 * * * *";

    /** Cron de 6 campos do Spring. Default: a cada 10 minutos. */
    private String redeCron = "0 0/10 * * * *";

    /** Cron de 6 campos do Spring. Default: a cada 5 minutos. */
    private String bankCron = "0 0/5 * * * *";

    /** Apenas para logs/status, indicando se o scheduler deve registrar ciclos sem arquivos. */
    private boolean logIdleCycles = true;
  }

  @Getter
  @Setter
  @ToString
  public static class Reconciliation {

    /**
     * Tolerância máxima, em dias, entre a data prevista/origem e a data do lançamento bancário.
     */
    private int dateToleranceDays = 7;

    /**
     * Tolerância monetária absoluta. String para evitar perda de precisão no binder.
     */
    private String valueTolerance = "0.05";

    /**
     * Limite para busca recursiva de combinação de parcelas/ordens.
     */
    private int recursiveLimit = 30;

    /**
     * Proteção contra subset-sum muito grande. Valor em centavos; default R$ 500.000,00.
     */
    private long safeCapCents = 50_000_000L;

    public BigDecimal valueToleranceAsBigDecimal() {
      if (valueTolerance == null || valueTolerance.isBlank()) {
        return new BigDecimal("0.05");
      }
      return new BigDecimal(valueTolerance.trim());
    }
  }

  public FilePaths getPathsOrThrow(String system) {
    FilePaths paths = systems == null ? null : systems.byName(system);

    if (paths == null || paths.getInput() == null || paths.getInput().isBlank()) {
      throw new IllegalStateException("Configuração não encontrada para file-processing.systems." + system + ".input");
    }

    applyFilePathDefaults(system, paths, "erp".equalsIgnoreCase(system));

    return paths;
  }

  public Map<String, FilePaths> getAcquirerPaths() {
    if (systems == null || systems.getAcquirer() == null) {
      return Map.of();
    }

    Map<String, FilePaths> result = systems.getAcquirer().enabledAcquirers();
    result.forEach((acquirer, paths) -> applyFilePathDefaults("acquirer." + acquirer, paths, false));
    return result;
  }

  public Map<String, FilePaths> getBankPaths() {
    if (systems == null || systems.getBank() == null) {
      return Map.of();
    }

    Map<String, FilePaths> result = systems.getBank().enabledBanks();
    result.forEach((bank, paths) -> applyFilePathDefaults("bank." + bank, paths, false));
    return result;
  }

  @PostConstruct
  void applyDefaults() {
    if (systems == null) {
      systems = new Systems();
    }

    if (systems.getReconciliation() != null) {
      reconciliation = systems.getReconciliation();
    }

    applyFilePathDefaults("erp", systems.getErp(), true);

    FilePaths redePaths = systems.redePath();
    applyFilePathDefaults("rede", redePaths, false);

    getAcquirerPaths().forEach((acquirer, paths) ->
      applyFilePathDefaults("acquirer." + acquirer, paths, false)
    );

    getBankPaths().forEach((bank, paths) ->
      applyFilePathDefaults("bank." + bank, paths, false)
    );
  }

  private void applyFilePathDefaults(String system, FilePaths paths, boolean erpLog) {
    if (paths == null || paths.getInput() == null || paths.getInput().isBlank()) {
      return;
    }

    String input = normalizePath(paths.getInput());
    paths.setInput(input);

    String systemRoot = parentOfInput(input);

    if (paths.getError() == null || paths.getError().isBlank()) {
      paths.setError(systemRoot + "/error");
    }

    if (paths.getProcessed() == null || paths.getProcessed().isBlank()) {
      paths.setProcessed(systemRoot + "/processed");
    }

    if (paths.getInvalid() == null || paths.getInvalid().isBlank()) {
      paths.setInvalid(input + "/invalid_file");
    }

    if (paths.getDuplicate() == null || paths.getDuplicate().isBlank()) {
      paths.setDuplicate(systemRoot + "/duplicate");
    }

    if (erpLog && (paths.getLog() == null || paths.getLog().isBlank())) {
      paths.setLog(input + "/log");
    }
  }

  private String parentOfInput(String input) {
    String normalized = normalizePath(input);
    if (normalized == null || normalized.isBlank()) {
      return normalized;
    }

    if (normalized.endsWith("/input")) {
      return normalized.substring(0, normalized.length() - "/input".length());
    }

    return normalized;
  }

  private String normalizePath(String value) {
    return value == null ? null : value.replace('\\', '/');
  }
}