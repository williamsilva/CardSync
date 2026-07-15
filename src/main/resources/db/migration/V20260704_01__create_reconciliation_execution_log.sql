CREATE TABLE cs_reconciliation_execution_log (
  id              UUID   NOT NULL,
  trigger_type    VARCHAR(50)  NOT NULL,
  started_at      TIMESTAMP(6)  NOT NULL,
  finished_at     TIMESTAMP(6)  NULL,
  overall_status  VARCHAR(20)  NOT NULL,
  total_analyzed  INT          NULL,
  total_reconciled INT         NULL,
  total_pending   INT          NULL,
  steps_json      TEXT         NULL,
  created_at      TIMESTAMP(6)  NOT NULL,
  updated_at      TIMESTAMP(6)  NULL,
  created_by_id   UUID   NULL,
  updated_by_id   UUID   NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_cs_rec_exec_log_started_at ON cs_reconciliation_execution_log (started_at DESC);
