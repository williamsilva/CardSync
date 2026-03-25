package com.cardsync.infrastructure.mail;

import com.cardsync.domain.service.EmailSenderService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
public class EmailTemplateProcessor {

  private final TemplateEngine templateEngine;

  public String processTemplateThymeleaf(EmailSenderService.Message message) {
    Locale locale = resolveLocale(message);

    Context context = new Context(locale);

    if (message.getData() != null) {
      message.getData().forEach(context::setVariable);
    }

    return templateEngine.process(resolveTemplateName(message.getTemplate()), context);
  }

  public String processTemplate(EmailSenderService.Message message) {
    throw new UnsupportedOperationException("Freemarker processor not implemented");
  }

  private Locale resolveLocale(EmailSenderService.Message message) {
    if (message.getLocale() != null) {
      return message.getLocale();
    }

    Locale locale = LocaleContextHolder.getLocale();
    if (locale != null) {
      return locale;
    }

    return new Locale("pt", "BR");
  }

  private String resolveTemplateName(String template) {
    if (template == null || template.isBlank()) {
      throw new IllegalArgumentException("Template de e-mail não informado");
    }

    String normalized = template.trim();
    if (normalized.endsWith(".html")) {
      normalized = normalized.substring(0, normalized.length() - 5);
    }

    return normalized;
  }
}