-- =====================================================================
-- Tabela do ShedLock para trava distribuída do agendamento da esteira.
-- MySQL 8 / InnoDB
--
-- Contexto: a aplicação roda em múltiplas instâncias. As travas em memória
-- (AtomicBoolean) só impedem execução concorrente DENTRO de uma instância.
-- O ShedLock usa esta tabela para garantir que apenas UMA instância do
-- cluster execute a esteira agendada por vez.
--
-- Estrutura exigida pelo ShedLock (provider JdbcTemplate):
--   name       - identificador único do lock (PK)
--   lock_until - até quando o lock é considerado válido
--   locked_at  - quando o lock foi adquirido
--   locked_by  - qual instância adquiriu (host/identificador)
-- =====================================================================

CREATE TABLE IF NOT EXISTS shedlock (
  name       VARCHAR(64)  NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL,
  locked_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);