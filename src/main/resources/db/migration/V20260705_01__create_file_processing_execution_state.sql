CREATE TABLE cs_file_processing_execution_state (
  id               BINARY(16)    NOT NULL,
  system_code      VARCHAR(20)   NOT NULL,
  last_started_at  DATETIME(6)   NULL,
  last_finished_at DATETIME(6)   NULL,
  last_success     BOOLEAN       NULL,
  last_trigger     VARCHAR(80)   NULL,
  last_message     TEXT          NULL,
  updated_at       DATETIME(6)   NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_fp_exec_state_system UNIQUE (system_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
