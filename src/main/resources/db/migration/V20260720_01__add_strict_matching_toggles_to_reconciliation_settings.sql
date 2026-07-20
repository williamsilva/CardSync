-- Rigidez do matching Banco x Ordem de Crédito / Parcela (Etapa 7). Hoje bandeira,
-- estabelecimento e modalidade (débito/crédito) são opcionais no casamento — nulo/desconhecido
-- em qualquer lado age como coringa (ver ReconciliationMatchContext). Default FALSE nos três =
-- comportamento idêntico ao atual; ligar cada um passa a exigir o campo correspondente.
ALTER TABLE cs_reconciliation_settings
  ADD COLUMN flag_match_required          BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN establishment_match_required  BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN payment_kind_match_required   BOOLEAN NOT NULL DEFAULT FALSE;
