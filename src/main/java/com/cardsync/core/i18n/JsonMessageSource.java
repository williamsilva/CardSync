package com.cardsync.core.i18n;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

public class JsonMessageSource extends AbstractMessageSource {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, Map<String, String>> cache = new HashMap<>();

  @Override
  protected MessageFormat resolveCode(String code, Locale locale) {
    String localeKey = normalizeLocale(locale);
    Map<String, String> messages = cache.computeIfAbsent(localeKey, this::loadMessages);

    String message = messages.get(code);
    if (message == null) {
      return null;
    }

    return new MessageFormat(message, locale);
  }

  @Override
  protected String resolveCodeWithoutArguments(String code, Locale locale) {
    String localeKey = normalizeLocale(locale);
    Map<String, String> messages = cache.computeIfAbsent(localeKey, this::loadMessages);
    return messages.get(code);
  }

  private Map<String, String> loadMessages(String localeKey) {
    for (String candidate : candidateFiles(localeKey)) {
      Map<String, String> loaded = tryLoad(candidate);
      if (!loaded.isEmpty()) {
        return loaded;
      }
    }
    return Map.of();
  }

  private Map<String, String> tryLoad(String localeKey) {
    String path = "i18n/messages_" + localeKey + ".json";
    ClassPathResource resource = new ClassPathResource(path);

    if (!resource.exists()) {
      return Map.of();
    }

    try (InputStream is = resource.getInputStream()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = objectMapper.readValue(is, Map.class);
      return flatten(raw, null);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private Map<String, String> flatten(Map<String, Object> source, String prefix) {
    Map<String, String> result = new HashMap<>();

    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = prefix == null ? entry.getKey() : prefix + "." + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedMap = (Map<String, Object>) nested;
        result.putAll(flatten(nestedMap, key));
      } else if (value != null) {
        result.put(key, String.valueOf(value));
      }
    }

    return result;
  }

  private String normalizeLocale(Locale locale) {
    if (locale == null) {
      return "pt_BR";
    }

    String language = locale.getLanguage();
    String country = locale.getCountry();

    if (language.isBlank()) {
      return "pt_BR";
    }

    if (country.isBlank()) {
      return language;
    }

    return language + "_" + country;
  }

  private String[] candidateFiles(String localeKey) {
    return switch (localeKey) {
      case "pt_BR" -> new String[] { "pt_BR", "pt", "en" };
      case "es_ES" -> new String[] { "es_ES", "es", "en" };
      case "en_US" -> new String[] { "en_US", "en" };
      default -> new String[] { localeKey, "en", "pt_BR" };
    };
  }
}