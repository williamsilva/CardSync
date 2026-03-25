package com.cardsync.core.config;

import com.cardsync.core.i18n.JsonMessageSource;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

  @Bean
  public MessageSource messageSource() {
    JsonMessageSource ms = new JsonMessageSource();
    ms.setUseCodeAsDefaultMessage(true);
    return ms;
  }

  @Bean
  public LocaleResolver localeResolver() {
    CookieLocaleResolver r = new CookieLocaleResolver("CARDSYNC_LOCALE");
    r.setDefaultLocale(new Locale("pt", "BR"));
    return r;
  }

  @Bean
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor i = new LocaleChangeInterceptor();
    i.setParamName("lang");
    return i;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
  }
}