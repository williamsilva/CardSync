package com.cardsync.core.security;

import com.cardsync.core.security.authserver.AuthServerProperties;

import java.time.Duration;
import java.util.*;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "cardsync.security")
public class CardsyncSecurityProperties {

  @NotNull
  private String issuer;

  @NotNull
  private Password password;

  @NotNull
  private Set<String> protectedUsernames;

  private Web web = new Web();
  private Login login = new Login();
  private Lockout lockout = new Lockout();
  private ResourceServer resourceServer = new ResourceServer();
  private AuthServerProperties authserver = new AuthServerProperties();

  @Data
  public static class ResourceServer {
    private boolean enabled = false;
    private String audience = "cardsync-api";
  }

  @Data
  public static class Web {
    /** Base URL do front-end (SPA) para redirecionamentos do lado servidor (ex: após login). */
    private String spaBaseUrl;
    private List<String> allowedOrigins;
  }

  @Data
  public static class Lockout {
    private boolean enabled;

    /**
     * Se true: quando usuário já está bloqueado e tentar de novo,
     * estende o bloqueio de acordo com a regra atual.
     * Se false: não estende.
     */
    private boolean extendWhenLocked;

    /**
     * Lista configurável de regras.
     * Ex:
     * - attempts: 5   duration: PT15M
     * - attempts: 10  duration: PT30M
     * - attempts: 15  duration: PT1H
     * - attempts: 20  duration: PT2H
     */
    private List<Rule> rules = new ArrayList<>();

    @Getter
    @Setter
    public static class Rule {
      private int attempts;
      private Duration duration;
    }

    public List<Rule> sortedRules() {
      return rules.stream()
        .filter(r -> r.getAttempts() > 0 && r.getDuration() != null && !r.getDuration().isNegative())
        .sorted(Comparator.comparingInt(Rule::getAttempts))
        .toList();
    }
  }

  @Data
  public static class Login {
    private String defaultTarget;
  }

  @Data
  public static class Password {

    @NotNull
    private int minLength;

    @NotNull
    private int historySize;

    /**
     * Se true: quando não houver nenhuma data de base (expiresAt, changedAt, createdAt),
     * considera como EXPIRADO (fail-safe).
     * Recomendado:
     *   DEV  -> false
     *   PROD -> true
     */
    @NotNull
    private boolean failIfNoExpirationData = false;

    /**
     * Regras dinâmicas: permite adicionar novas chaves no futuro.
     * Ex: digit=true, upper=true, symbol=true
     */
    private Map<String, Boolean> rules = new LinkedHashMap<>();

    /**
     * Parâmetros de regras opcionais.
     */
    private int sequenceLen = 4;
    private int maxSameInRow = 4;

    /**
     * Lista de senhas comuns (normalizada) para bloquear quando a regra "common" estiver habilitada.
     */
    private List<String> commonPasswords = new ArrayList<>();

    @NotNull
    private Expiration expiration;

    @Data
    public static class Expiration {
      /**
       * 0 ou negativo desabilita expiração.
       */
      @NotNull
      private int warnDays;

      @NotNull
      private int expireDays;

      @NotNull
      private boolean enabled;
    }

    @Data
    public static class Tokens {
      /**
       * Base pública para montar links em e-mails (ex: <a href="https://api.cardsync.com.br">...</a>).
       * Se vazio, o serviço tenta usar o baseUrl calculado do request (quando disponível).
       */
      private String publicBaseUrl = "";

      /**
       * Validade do link de convite / primeira senha. Default: 1 dia.
       */
      private Duration inviteTtl = Duration.ofDays(1);

      /**
       * Validade do link de reset de senha. Default: 15 minutos.
       */
      private Duration resetTtl = Duration.ofMinutes(15);
      private ResetRateLimit rateLimit = new ResetRateLimit();

      @Data
      public static class ResetRateLimit {
        private int maxRequests = 3;
        private boolean enabled = true;
        private Duration window = Duration.ofMinutes(15);
      }
    }

    @NotNull
    private Tokens tokens = new Tokens();

  }

}
