package com.cardsync.core.config;

import com.cardsync.core.security.web.CurrentAuditorAware;
import com.cardsync.domain.model.UserEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(
  auditorAwareRef = "auditorAware",
  dateTimeProviderRef = "auditingDateTimeProvider"
)
public class JpaAuditingConfig {

  @Bean("auditorAware")
  public AuditorAware<UserEntity> auditorAware(CurrentAuditorAware aware) {
    return aware;
  }

  @Bean("auditingDateTimeProvider")
  public org.springframework.data.auditing.DateTimeProvider auditingDateTimeProvider(java.time.Clock clock) {
    return () -> java.util.Optional.of(
      java.time.OffsetDateTime.now(clock).withOffsetSameInstant(java.time.ZoneOffset.UTC)
    );
  }
}
