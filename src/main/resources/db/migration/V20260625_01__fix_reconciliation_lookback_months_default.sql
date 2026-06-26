-- Corrige o DEFAULT da coluna (era 6, inadequado para histórico completo).
-- Atualiza registros existentes que ainda estão com o valor padrão inicial de 6.
ALTER TABLE cs_reconciliation_settings
  MODIFY COLUMN reconciliation_lookback_months INT NOT NULL DEFAULT 120;

UPDATE cs_reconciliation_settings
  SET reconciliation_lookback_months = 120
  WHERE reconciliation_lookback_months = 6;
