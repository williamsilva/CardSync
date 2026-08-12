package com.cardsync.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Guarda de regressão pra estrutura HTML de mail/chargeback-mail.html (self-contained, não usa
 * o fragmento _layout - esse é dead code no CardsyncServer, nada o referencia). Sem Spring
 * context (Testcontainers não é necessário aqui, é só resolução de template) - mesma
 * configuração default do Spring Boot (classpath:/templates/, sufixo .html). Espelha
 * MailLayoutTemplateTest do NimbusAuth (mesma origem de template, mesmo ajuste de tema claro).
 */
class ChargebackMailTemplateTest {

  private final TemplateEngine templateEngine = buildEngine();

  private static TemplateEngine buildEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");

    TemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  @Test
  void chargebackMail_rendersLightThemeWithLogoUrlAndRows() {
    Context context = new Context();
    context.setVariable("fileName", "arquivo-teste.txt");
    context.setVariable("totalCount", 2);
    context.setVariable("logoUrl", "https://api.cardsync.com.br/assets/cardsync-logo.png");
    context.setVariable(
        "chargebacks",
        List.of(
            Map.of("tipo", "Débito Pendente", "pv", "123", "valor", "R$ 10,00", "data", "01/01/2026", "descricao", "-"),
            Map.of("tipo", "Ajuste", "pv", "456", "valor", "R$ 20,00", "data", "02/01/2026", "descricao", "-")
        )
    );

    String html = templateEngine.process("mail/chargeback-mail", context);

    assertThat(html).contains("<head>");
    assertThat(html).contains("name=\"color-scheme\" content=\"light dark\"");
    assertThat(html).contains("name=\"supported-color-schemes\" content=\"light dark\"");
    assertThat(html).contains("src=\"https://api.cardsync.com.br/assets/cardsync-logo.png\"");
    assertThat(html).contains("background-color:#eef2f8");
    assertThat(html).contains("background-color:#ffffff");
    assertThat(html).doesNotContain("#08111f");
    assertThat(html).doesNotContain("#0b1220");
    assertThat(html).contains("Débito Pendente");
    assertThat(html).contains("Ajuste");
  }

  @Test
  void chargebackMail_fallsBackToInitialsBadgeWhenLogoUrlMissing() {
    Context context = new Context();
    context.setVariable("fileName", "arquivo-teste.txt");
    context.setVariable("totalCount", 0);
    context.setVariable("chargebacks", List.of());

    String html = templateEngine.process("mail/chargeback-mail", context);

    assertThat(html).doesNotContain("<img");
    assertThat(html).containsPattern("(?s)>\\s*CS\\s*<");
  }
}
