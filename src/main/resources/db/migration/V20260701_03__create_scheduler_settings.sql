CREATE TABLE cs_scheduler_settings (
  id                                   BINARY(16)   NOT NULL,
  enabled                              BIT(1)       NOT NULL DEFAULT 0,
  complete_pipeline_enabled            BIT(1)       NOT NULL DEFAULT 1,
  complete_pipeline_cron               VARCHAR(100) NOT NULL DEFAULT '0 0/30 * * * *',
  complete_pipeline_stop_on_step_error BIT(1)       NOT NULL DEFAULT 1,
  log_idle_cycles                      BIT(1)       NOT NULL DEFAULT 0,
  created_at                           DATETIME(6)  NOT NULL,
  updated_at                           DATETIME(6)  NULL,
  created_by_id                        BINARY(16)   NULL,
  updated_by_id                        BINARY(16)   NULL,
  PRIMARY KEY (id)
);
