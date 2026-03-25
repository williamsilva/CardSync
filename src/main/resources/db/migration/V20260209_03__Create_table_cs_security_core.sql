-- cs_password_history
CREATE TABLE cs_password_history (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cs_password_history_user (user_id),
  CONSTRAINT fk_cs_password_history_user
    FOREIGN KEY (user_id) REFERENCES cs_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_password_history_aud
CREATE TABLE IF NOT EXISTS cs_password_history_aud (
  id BINARY(16) NOT NULL,

  -- envers
  rev INT NOT NULL,
  revtype TINYINT NULL,

  -- colunas originais
  created_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NULL,
  password_hash VARCHAR(120) NULL,

  PRIMARY KEY (id, rev),
  CONSTRAINT fk_cs_password_history_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;
CREATE INDEX ix_cs_password_history_aud_rev ON cs_password_history_aud (rev);

-- cs_invite_token
CREATE TABLE cs_invite_token (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uq_cs_invite_token_hash (token_hash),
  KEY idx_cs_invite_user (user_id),
  CONSTRAINT fk_cs_invite_user
    FOREIGN KEY (user_id) REFERENCES cs_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_invite_token_AUD
CREATE TABLE cs_invite_token_AUD (
  id BINARY(16) NOT NULL,

  -- envers
  rev INT NOT NULL,
  revtype TINYINT NULL,

  -- colunas originais
  created_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NULL,
  token_hash CHAR(64) NULL,
  expires_at TIMESTAMP(6) NULL,
  used_at TIMESTAMP(6) NULL,

  CONSTRAINT pk_cs_invite_tokens_AUD PRIMARY KEY (id, REV),
  CONSTRAINT fk_cs_invite_tokens_AUD_rev FOREIGN KEY (REV) REFERENCES revinfo(rev)
) ENGINE=InnoDB;
CREATE INDEX ix_cs_invite_tokens_AUD_rev ON cs_invite_token_AUD(REV);
CREATE INDEX ix_cs_invite_tokens_AUD_user_rev ON cs_invite_token_AUD(user_id, REV);

-- cs_reset_token
CREATE TABLE cs_reset_token (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uq_cs_reset_token_hash (token_hash),
  KEY idx_cs_reset_user (user_id),
  CONSTRAINT fk_cs_reset_user
    FOREIGN KEY (user_id) REFERENCES cs_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_reset_token_AUD
CREATE TABLE cs_reset_token_AUD (
  id BINARY(16) NOT NULL,

  -- envers
  rev INT NOT NULL,
  revtype TINYINT NULL,

  -- colunas originais
  created_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  user_id BINARY(16) NULL,
  token_hash CHAR(64) NULL,
  expires_at TIMESTAMP(6) NULL,
  used_at TIMESTAMP(6) NULL,

  CONSTRAINT pk_cs_reset_token_AUD PRIMARY KEY (id, REV),
  CONSTRAINT fk_cs_reset_token_AUD_rev FOREIGN KEY (REV) REFERENCES revinfo(rev)
) ENGINE=InnoDB;
CREATE INDEX ix_cs_reset_token_AUD_rev ON cs_reset_token_AUD(REV);
CREATE INDEX ix_cs_reset_token_AUD_user_rev ON cs_reset_token_AUD(user_id, REV);

CREATE TABLE cs_audit_event (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  event_type VARCHAR(80) NOT NULL,
  principal VARCHAR(120) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  correlation_id BINARY(16) NULL,
  payload_json JSON NULL,

  PRIMARY KEY (id),
  KEY idx_cs_audit_event_type (event_type),
  KEY idx_cs_audit_event_principal (principal),
  KEY idx_cs_audit_event_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
