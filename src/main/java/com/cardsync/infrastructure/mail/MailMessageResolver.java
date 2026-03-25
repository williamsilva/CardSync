package com.cardsync.infrastructure.mail;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailMessageResolver {

  private final MessageSource messageSource;

  public String get(String code, Locale locale, Object... args) {
    return messageSource.getMessage(code, args, locale);
  }
}