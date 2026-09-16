package com.cardsync.core.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lê/grava a coluna chargeback_recipients de "email_settings" via JDBC direto, sem entidade JPA
 *  própria pra essa tabela - a entidade JPA "de verdade" agora é a compartilhada
 *  (com.nimbussystems.commons.notification.mail.EmailSettingsEntity), que não tem esse campo
 *  (só o CardSync usa - lista de destinatários de alerta de chargeback, ver
 *  ChargebackEmailService). Ter DUAS entidades JPA mapeando a mesma tabela é evitável e mais
 *  arriscado (cache/staleness) do que um acesso JDBC pontual pra uma única coluna. */
@Repository
@RequiredArgsConstructor
public class ChargebackRecipientsRepository {

  private final JdbcTemplate jdbc;

  public String getRaw() {
    return jdbc.query(
        "SELECT chargeback_recipients FROM email_settings LIMIT 1",
        rs -> rs.next() ? rs.getString(1) : null);
  }

  public List<String> getAsList() {
    String raw = getRaw();
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  public void update(String raw) {
    jdbc.update("UPDATE email_settings SET chargeback_recipients = ?", raw);
  }
}
