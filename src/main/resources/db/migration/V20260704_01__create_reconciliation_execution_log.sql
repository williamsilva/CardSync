CREATE TABLE cs_reconciliation_execution_log (
  id              BINARY(16)   NOT NULL,
  trigger_type    VARCHAR(50)  NOT NULL,
  started_at      DATETIME(6)  NOT NULL,
  finished_at     DATETIME(6)  NULL,
  overall_status  VARCHAR(20)  NOT NULL,
  total_analyzed  INT          NULL,
  total_reconciled INT         NULL,
  total_pending   INT          NULL,
  steps_json      TEXT         NULL,
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NULL,
  created_by_id   BINARY(16)   NULL,
  updated_by_id   BINARY(16)   NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_cs_rec_exec_log_created_by FOREIGN KEY (created_by_id) REFERENCES cs_users (id),
  CONSTRAINT fk_cs_rec_exec_log_updated_by FOREIGN KEY (updated_by_id) REFERENCES cs_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_cs_rec_exec_log_started_at ON cs_reconciliation_execution_log (started_at DESC);
