-- Permite vincular manualmente um lançamento bancário a ordens de crédito mesmo quando a soma
-- não bate exato — cobre o caso real de o lançamento misturar vendas anteriores à implantação do
-- sistema (sem CreditOrder correspondente) com vendas atuais, o que nunca fecha por definição. A
-- diferença aceita fica registrada (valor + motivo), não é silenciosa.
ALTER TABLE cs_releases_bank
  ADD COLUMN divergence_value DECIMAL(18,8),
  ADD COLUMN divergence_reason VARCHAR(500);
