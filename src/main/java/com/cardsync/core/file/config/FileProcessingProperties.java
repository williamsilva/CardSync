package com.cardsync.core.file.config;

import com.cardsync.core.reconciliation.BankReconciliationMode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

  private Erp erp = new Erp();
  private Systems systems = new Systems();
  private Calendar calendar = new Calendar();
  private Scheduler scheduler = new Scheduler();
  private Reconciliation reconciliation = new Reconciliation();

  @Getter
  @Setter
  @ToString
  public static class Calendar {
    /**
     * Define se o grupo ERP deve ser esperado diariamente na agenda de arquivos.
     */
    private boolean erpEnabled = true;
  }

  @Getter
  @Setter
  @ToString
  public static class Systems {

    private FilePaths erp = new FilePaths();

    /**
     * Compatibilidade com formato antigo:

     * file-processing.systems.rede

     * O novo formato recomendado é:

     * file-processing.systems.acquirer.rede
     */
    private FilePaths rede = new FilePaths();

    /**
     * Novo agrupador de adquirentes:

     * file-processing.systems.acquirer.rede
     */
    private Acquirer acquirer = new Acquirer();

    /**
     * Agrupador bancário:

     * file-processing.systems.bank.itau
     * file-processing.systems.bank.santander
     * file-processing.systems.bank.bradesco
     * file-processing.systems.bank.sicredi
     */
    private Bank bank = new Bank();

    /**
     * Compatibilidade com a configuração atual, onde reconciliation está
     * dentro de systems.
     */
    private Reconciliation reconciliation;

    /** Retorna os caminhos de um sistema pelo nome (erp, rede, cielo). */
    public FilePaths byName(String system) {
      if (system == null) return null;

      return switch (system.trim().toLowerCase(Locale.ROOT)) {
        case "erp" -> erp;
        case "rede" -> redePath();
        case "cielo" -> acquirer != null ? acquirer.getCielo() : null;
        default -> null;
      };
    }

    /**
     * Resolve o caminho Rede preferindo a configuração aninhada em {@code acquirer.rede},
     * com fallback para a chave legada {@code rede} no nível de systems.
     */
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
    private FilePaths cielo = new FilePaths();

    /** Retorna apenas os adquirentes que possuem {@code input} configurado. */
    public Map<String, FilePaths> enabledAcquirers() {
      Map<String, FilePaths> result = new LinkedHashMap<>();
      if (hasInput(rede)) result.put("rede", rede);
      if (hasInput(cielo)) result.put("cielo", cielo);
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
    private FilePaths sicredi = new FilePaths();

    /** Retorna apenas os bancos que possuem {@code input} configurado. */
    public Map<String, FilePaths> enabledBanks() {
      Map<String, FilePaths> result = new LinkedHashMap<>();
      if (hasInput(itau)) result.put("itau", itau);
      if (hasInput(santander)) result.put("santander", santander);
      if (hasInput(bradesco)) result.put("bradesco", bradesco);
      if (hasInput(sicredi)) result.put("sicredi", sicredi);
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

    /**
     * Garante que todas as pastas configuradas existam, criando-as (e os pais)
     * quando necessário. Deve ser chamado antes de listar/mover arquivos, para
     * que a ausência de uma pasta não derrube o processamento.
     */
    public void ensureDirectories() {
      createDirectory(input);
      createDirectory(error);
      createDirectory(invalid);
      createDirectory(processed);
      createDirectory(duplicate);
      createDirectory(log);
    }

    private void createDirectory(String path) {
      if (path == null || path.isBlank()) {
        return;
      }
      try {
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(path));
      } catch (java.io.IOException ex) {
        throw new IllegalStateException("Não foi possível criar a pasta de processamento: " + path, ex);
      }
    }
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

    /**
     * Indica se o arquivo ERP informa empresa na própria linha.
     * Quando false, a conciliação ERP x adquirente pode preencher/corrigir empresa
     * usando o contexto da venda da adquirente.
     */
    private boolean informsCompany = false;

    /**
     * Indica se o arquivo ERP informa estabelecimento/PV na própria linha.
     * Quando false, a conciliação ERP x adquirente pode preencher/corrigir estabelecimento
     * usando o contexto da venda da adquirente.
     */
    private boolean informsEstablishment = false;

    private String defaultCommercialName = "ERP";
    private Integer defaultPvGroupNumber;
  }

  @Getter
  @Setter
  @ToString
  public static class Scheduler {

    /**
     * Trava distribuída (ShedLock): tempo máximo que o lock da esteira é mantido caso a
     * instância que o adquiriu caia sem liberá-lo. Deve ser maior que a duração máxima
     * esperada de uma execução completa. Formato ISO-8601 de duração (ex.: PT30M).
     */
    private String lockAtMostFor = "PT30M";

    /**
     * Trava distribuída (ShedLock): tempo mínimo que o lock é mantido após o início,
     * mesmo que a esteira termine antes. Evita reexecução imediata por relógios levemente
     * dessincronizados entre instâncias. Formato ISO-8601 de duração (ex.: PT10S).
     */
    private String lockAtLeastFor = "PT10S";
  }

  @Getter
  @Setter
  @ToString
  public static class Reconciliation {

    /**
     * Janela máxima, em dias, entre a data de venda do ERP e a da adquirente na
     * conciliação de transações MANUAIS com NSU/autorização invertidos. Como essas
     * vendas são digitadas manualmente, a data pode divergir bastante; por isso a
     * janela é maior que a da conciliação principal. Default 60 dias.
     */
    private int manualSwapSaleDateToleranceDays = 60;

    /**
     * Limite para busca recursiva de combinação de parcelas/ordens.
     */
    private int recursiveLimit = 30;

    /**
     * Proteção contra subset-sum muito grande. Valor em centavos; default R$ 500.000,00.
     */
    private long safeCapCents = 50_000_000L;

    /**
     * Modo da conciliação Banco x adquirente (ver BankReconciliationMode). Default
     * CREDIT_ORDER_ONLY preserva o comportamento histórico (só ordens de crédito elegíveis);
     * os demais modos ativam a conciliação por parcelas (fallback ou exclusiva) via
     * BankReconciliationService.reconcilePending.
     */
    private BankReconciliationMode bankMode = BankReconciliationMode.CREDIT_ORDER_ONLY;

    /**
     * Quantidade máxima de ordens de crédito carregadas e processadas por lote.
     */
    private int bankBatchSize = 500;

  }

  /**
   * Resolve os caminhos de um sistema (erp, rede, ou qualquer adquirente/banco habilitado)
   * a partir de uma chave dinâmica vinda da API (upload manual, navegador de arquivos, etc.),
   * sem lançar exceção — retorna {@code null} se o sistema não existir/não estiver configurado.
   * Usado nos pontos que precisam validar e responder com uma mensagem de erro própria
   * (ver {@code FileUploadService}/{@code FileBrowserService}), em vez do
   * {@code IllegalStateException} genérico de {@link #getPathsOrThrow(String)}.
   */
  public FilePaths resolveSystemPaths(String system) {
    if (system == null || system.isBlank()) {
      return null;
    }

    String key = system.trim().toLowerCase(Locale.ROOT);

    if ("erp".equals(key) || "rede".equals(key)) {
      FilePaths paths = systems == null ? null : systems.byName(key);
      if (paths == null || paths.getInput() == null || paths.getInput().isBlank()) {
        return null;
      }
      applyFilePathDefaults(key, paths, "erp".equals(key));
      return paths;
    }

    FilePaths paths = getAcquirerPaths().get(key);
    if (paths == null) {
      paths = getBankPaths().get(key);
    }
    return paths;
  }

  /**
   * Retorna os caminhos de um sistema pelo nome, lançando exceção se {@code input} não estiver
   * configurado. Aplica os defaults de subpastas antes de retornar.
   */
  public FilePaths getPathsOrThrow(String system) {
    FilePaths paths = systems == null ? null : systems.byName(system);

    if (paths == null || paths.getInput() == null || paths.getInput().isBlank()) {
      throw new IllegalStateException("Configuração não encontrada para file-processing.systems." + system + ".input");
    }

    applyFilePathDefaults(system, paths, "erp".equalsIgnoreCase(system));

    return paths;
  }

  /**
   * Retorna o mapa de adquirentes habilitados (possuem {@code input} configurado),
   * com defaults de subpastas já aplicados.
   */
  public Map<String, FilePaths> getAcquirerPaths() {
    if (systems == null || systems.getAcquirer() == null) {
      return Map.of();
    }

    Map<String, FilePaths> result = systems.getAcquirer().enabledAcquirers();
    result.forEach((acquirer, paths) -> applyFilePathDefaults("acquirer." + acquirer, paths, false));
    return result;
  }

  /**
   * Retorna o mapa de bancos habilitados (possuem {@code input} configurado),
   * com defaults de subpastas já aplicados.
   */
  public Map<String, FilePaths> getBankPaths() {
    if (systems == null || systems.getBank() == null) {
      return Map.of();
    }

    Map<String, FilePaths> result = systems.getBank().enabledBanks();
    result.forEach((bank, paths) -> applyFilePathDefaults("bank." + bank, paths, false));
    return result;
  }

  /**
   * Executado após injeção de propriedades. Aplica os defaults de subpastas para todos
   * os sistemas e migra {@code systems.reconciliation} para o campo raiz quando presente.
   */
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

  /**
   * Preenche subpastas não configuradas com valores padrão derivados de {@code input}.
   * Estrutura gerada (todas irmãs de {@code input}):
   * <pre>
   *   &lt;root&gt;/input/          ← pasta varrida pelo processador
   *   &lt;root&gt;/error/          ← arquivos que falharam
   *   &lt;root&gt;/processed/      ← arquivos concluídos com sucesso ou warnings
   *   &lt;root&gt;/duplicate/      ← arquivos já importados anteriormente
   *   &lt;root&gt;/invalid_file/   ← arquivos que não passaram na validação de formato
   * </pre>
   * Para ERP, cria também {@code input/log/} para os arquivos de log gerados.
   */
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
      paths.setInvalid(systemRoot + "/invalid_file");
    }

    if (paths.getDuplicate() == null || paths.getDuplicate().isBlank()) {
      paths.setDuplicate(systemRoot + "/duplicate");
    }

    if (erpLog && (paths.getLog() == null || paths.getLog().isBlank())) {
      paths.setLog(input + "/log");
    }
  }

  /**
   * Retorna o diretório pai de {@code input}, assumindo que o caminho termina em {@code /input}.
   * Usado para derivar as subpastas irmãs (error, processed, etc.).
   * Se o caminho não terminar em {@code /input}, retorna o próprio caminho.
   */
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

  /** Normaliza separadores de caminho para {@code /}, independente do SO. */
  private String normalizePath(String value) {
    return value == null ? null : value.replace('\\', '/');
  }
}