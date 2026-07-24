-- Teto de centavos para o subset-sum por programação dinâmica (Etapa 7 — Banco x Ordem de
-- Crédito/Parcela). Antes fixo em application.yml (RECONCILIATION_SUBSET_DP_MAX_CENTS, default
-- 5.000.000 = R$ 50.000,00); movido para cá para ser ajustável sem redeploy. Default elevado para
-- 50.000.000 (R$ 500.000,00) — mesmo teto já aceito por safe-cap-cents (que continua em
-- application.yml como limite externo) — para não deixar pendentes releases de maior valor que já
-- eram considerados seguros.
ALTER TABLE cs_reconciliation_settings
  ADD COLUMN subset_dp_max_cents BIGINT NOT NULL DEFAULT 50000000;
