package com.cardsync.core.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Trava distribuída para o agendamento da esteira.

 * As travas em memória ({@code AtomicBoolean}) impedem execução concorrente apenas
 * dentro de UMA instância. Como a aplicação roda em múltiplas instâncias, o ShedLock
 * usa uma tabela no banco (criada pela migration {@code V20260606_01__Add_shedlock_table})
 * para garantir que apenas uma instância do cluster execute a esteira por vez.

 * O {@code defaultLockAtMostFor} é uma rede de segurança: se a instância que detém o
 * lock cair sem liberá-lo, o lock expira após esse tempo e outra instância pode assumir.
 * Cada job pode sobrescrever esse limite na anotação {@code @SchedulerLock}.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
      JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(new JdbcTemplate(dataSource))
        .usingDbTime() // usa o horário do banco para evitar divergência de relógio entre instâncias
        .build()
    );
  }
}