-- Fase 4 da consolidação de Segurança: CardSync migra pro EmailSettingsService/Entity
-- compartilhado (nimbus-commons-server), mesmo padrão de NimbusFlow/NimbusDesk/NimbusNovax/
-- NimbusAuth. Mesmo padrão de rename já usado antes (ver V20260808_01 do NimbusAuth,
-- cs_*->nb_*): ALTER TABLE ... RENAME TO, preserva dados/índices.
--
-- Diferente do NimbusAuth: chargeback_recipients NÃO é dropada aqui - está em uso real
-- (ChargebackEmailService.notifyChargebacksFound) e não tem equivalente na entidade da lib.
-- Fica como coluna não-mapeada pela entidade EmailSettingsEntity da lib, lida/gravada à parte via
-- ChargebackRecipientsRepository (JDBC direto na mesma tabela).

ALTER TABLE cs_email_settings RENAME TO email_settings;
