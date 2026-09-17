-- Tabela "email_log" da lib compartilhada (com.nimbussystems.commons.notification.mail.
-- EmailLogEntity) - NÃO é usada de verdade aqui (a auditoria de e-mail do CardSync continua
-- 100% local, ver cs_email_log/EmailLogService/EmailLogController - decisão explícita da Fase 4,
-- pra não afetar a tela de auditoria já existente). Mesmo achado/mesmo workaround já aplicado no
-- NimbusAuthServer (ver V20260915_03 de lá).
--
-- Existe só porque @EntityScan precisa cobrir com.nimbussystems.commons.notification.mail
-- inteiro pra achar EmailSettingsEntity (ver CommonsIntegrationConfig) - EmailLogEntity mora no
-- MESMO pacote e é descoberta junto, e o Hibernate em modo "validate" (padrão com Flyway) exige
-- que toda entidade JPA registrada tenha uma tabela compatível no banco, mesmo que nunca seja
-- lida/escrita. Sem alternativa mais cirúrgica encontrada nesta versão do Spring Boot (4.0.2) pra
-- excluir uma entidade específica de dentro de um pacote escaneado.
--
-- Fica sempre vazia. Colunas espelham EmailLogEntity da lib (NimbusCommonsServer).

CREATE TABLE email_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(60) NOT NULL,
  recipients TEXT NOT NULL,
  subject VARCHAR(300) NOT NULL,
  template VARCHAR(200) NOT NULL,
  body TEXT,
  status VARCHAR(10) NOT NULL,
  error_message VARCHAR(1000),
  requested_by_id VARCHAR(100),
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
