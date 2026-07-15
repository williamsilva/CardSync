CREATE TABLE cs_scheduler_settings (
  id                                   UUID   NOT NULL,
  enabled                              BOOLEAN       NOT NULL DEFAULT FALSE,
  complete_pipeline_enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
  complete_pipeline_cron               VARCHAR(100) NOT NULL DEFAULT '0 0/30 * * * *',
  complete_pipeline_stop_on_step_error BOOLEAN       NOT NULL DEFAULT TRUE,
  log_idle_cycles                      BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at                           TIMESTAMP(6)  NOT NULL,
  updated_at                           TIMESTAMP(6)  NULL,
  created_by_id                        UUID   NULL,
  updated_by_id                        UUID   NULL,
  PRIMARY KEY (id)
);
