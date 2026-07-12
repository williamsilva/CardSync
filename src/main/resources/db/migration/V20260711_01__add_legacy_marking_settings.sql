-- Data de implantação (go-live) e janela de marcação de legado.
-- go_live_date: data em que o CardSync entrou em operação. Antes era a propriedade
-- cardsync.app.implantation-date (CARDSYNC_IMPLANTATION_DATE); passa a ser configurável
-- na tela de configurações de conciliação. Seed com o antigo default da propriedade.
-- legacy_marking_months: meses após o go-live em que a marcação manual de lançamentos
-- bancários como legado permanece disponível. Passado o período, o botão é ocultado.
ALTER TABLE cs_reconciliation_settings
  ADD COLUMN go_live_date DATE NOT NULL DEFAULT '2024-07-01',
  ADD COLUMN legacy_marking_months INT NOT NULL DEFAULT 12;
