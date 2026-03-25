package com.cardsync.infrastructure.mail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailTemplateUtils {

  private final MessageSource messageSource;

  public String formatDuration(Duration duration, Locale locale) {
    long totalMinutes = duration.toMinutes();
    long days = totalMinutes / (60 * 24);
    long hours = (totalMinutes % (60 * 24)) / 60;
    long minutes = totalMinutes % 60;

    List<String> parts = new ArrayList<>();

    if (days > 0) {
      parts.add(messageSource.getMessage(
        days == 1 ? "mail.duration.day" : "mail.duration.days",
        new Object[]{days},
        locale
      ));
    }

    if (hours > 0) {
      parts.add(messageSource.getMessage(
        hours == 1 ? "mail.duration.hour" : "mail.duration.hours",
        new Object[]{hours},
        locale
      ));
    }

    if (minutes > 0 && days == 0) {
      parts.add(messageSource.getMessage(
        minutes == 1 ? "mail.duration.minute" : "mail.duration.minutes",
        new Object[]{minutes},
        locale
      ));
    }

    if (parts.isEmpty()) {
      return messageSource.getMessage("mail.duration.fewMinutes", null, locale);
    }

    if (parts.size() == 1) {
      return parts.getFirst();
    }

    String joinWord = messageSource.getMessage("mail.duration.and", null, locale);
    return String.join(" " + joinWord + " ", parts);
  }
}