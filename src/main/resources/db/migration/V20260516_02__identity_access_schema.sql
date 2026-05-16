-- Revision table (DefaultRevisionEntity)
CREATE TABLE revinfo (
  REV INT NOT NULL AUTO_INCREMENT,
  REVTSTMP BIGINT NULL,
  PRIMARY KEY (REV)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cs_users (
  id BINARY(16) NOT NULL,

  -- EntityBase (auditoria)
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  -- UserEntity
  status INT NOT NULL DEFAULT 1,
  name VARCHAR(120) NOT NULL,
  user_name VARCHAR(120) NOT NULL,
  document VARCHAR(15) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  blocked_until TIMESTAMP(6) NULL,
  last_login_at TIMESTAMP(6) NULL,
  password_changed_at TIMESTAMP(6) NULL,
  password_expires_at TIMESTAMP(6) NULL,

  PRIMARY KEY (id),
  CONSTRAINT uk_cs_users_document UNIQUE (document),
  CONSTRAINT uk_cs_users_username UNIQUE (user_name),

  CONSTRAINT fk_cs_users_created_by FOREIGN KEY (created_by_id)
    REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE,

  CONSTRAINT fk_cs_users_updated_by FOREIGN KEY (updated_by_id)
    REFERENCES cs_users(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Índices
CREATE INDEX idx_cs_users_status ON cs_users (status);
CREATE INDEX idx_cs_users_created_by_id ON cs_users (created_by_id);
CREATE INDEX idx_cs_users_updated_by_id ON cs_users (updated_by_id);
CREATE INDEX ix_users_blocked_until ON cs_users(blocked_until);
CREATE INDEX ix_users_password_expires_at ON cs_users(password_expires_at);

-- cs_users_aud
CREATE TABLE cs_users_aud (
  id BINARY(16) NOT NULL,
  REV INT NOT NULL,
  REVTYPE TINYINT NULL,

  user_name VARCHAR(120) NULL,
  document VARCHAR(15) NULL,
  name VARCHAR(120) NULL,
  status INT NULL,
  failed_attempts INT NULL,
  blocked_until DATETIME(6) NULL,
  last_login_at DATETIME(6) NULL,
  password_hash VARCHAR(255) NULL,
  password_changed_at DATETIME(6) NULL,
  password_expires_at DATETIME(6) NULL,

  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  created_by VARCHAR(120) NULL,
  updated_by VARCHAR(120) NULL,

  PRIMARY KEY (id, REV),
  CONSTRAINT fk_cs_users_aud_rev FOREIGN KEY (REV) REFERENCES revinfo(REV)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_users_aud_rev ON cs_users_aud (rev);

-- cs_groups
CREATE TABLE cs_groups (
   id BINARY(16) NOT NULL,

   -- EntityBase (auditoria)
   created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
   updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
   created_by_id BINARY(16) NULL,
   updated_by_id BINARY(16) NULL,

   name VARCHAR(60) NOT NULL,
   description VARCHAR(100) NULL,

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_groups_aud
CREATE TABLE cs_groups_aud (
  id BINARY(16) NOT NULL,
  rev INT NOT NULL,
  revtype TINYINT NULL,

  name VARCHAR(60) NULL,
  description VARCHAR(100) NULL,

  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  PRIMARY KEY (id, rev),
  CONSTRAINT fk_cs_groups_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_groups_aud_rev ON cs_groups_aud (rev);

-- cs_permissions
CREATE TABLE cs_permissions (
   id BINARY(16) NOT NULL,

   -- EntityBase (auditoria)
   created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
   updated_at TIMESTAMP(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
   created_by_id BINARY(16) NULL,
   updated_by_id BINARY(16) NULL,

   name VARCHAR(120) NOT NULL,
   description VARCHAR(120) NOT NULL,

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_permissions_aud
CREATE TABLE cs_permissions_aud (
  id BINARY(16) NOT NULL,
  rev INT NOT NULL,
  revtype TINYINT NULL,

  name VARCHAR(60) NULL,
  description VARCHAR(100) NULL,

  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  created_by_id BINARY(16) NULL,
  updated_by_id BINARY(16) NULL,

  PRIMARY KEY (id, rev),
  CONSTRAINT fk_cs_permissions_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_cs_permissions_aud_rev ON cs_permissions_aud (rev);

-- cs_group_user_aud
CREATE TABLE cs_group_user_aud (
  id BINARY(16) NOT NULL,
  rev INT NOT NULL,
  revtype TINYINT NULL,

  name VARCHAR(255) NULL,
  description VARCHAR(255) NULL,

  PRIMARY KEY (id, rev),
  CONSTRAINT fk_cs_group_user_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_group_user_aud_rev ON cs_group_user_aud (rev);

-- cs_users_groups
CREATE TABLE cs_users_groups (
    user_id BINARY(16) NOT NULL,
    group_id BINARY(16) NOT NULL,

    PRIMARY KEY (user_id, group_id),

    CONSTRAINT fk_user_group_user
        FOREIGN KEY (user_id)
        REFERENCES cs_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_group_group
        FOREIGN KEY (group_id)
        REFERENCES cs_groups(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_users_groups_aud
CREATE TABLE cs_users_groups_aud (
  rev INT NOT NULL,
  revtype TINYINT NULL,

  user_id BINARY(16) NOT NULL,
  group_id BINARY(16) NOT NULL,

  PRIMARY KEY (rev, user_id, group_id),
  CONSTRAINT fk_cs_users_groups_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_users_groups_aud_rev ON cs_users_groups_aud (rev);

-- cs_groups_permissions
CREATE TABLE cs_groups_permissions (
    group_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,

    PRIMARY KEY (group_id, permission_id),

    CONSTRAINT fk_group_permission_group
        FOREIGN KEY (group_id) REFERENCES cs_groups(id) ON DELETE CASCADE,

    CONSTRAINT fk_group_permission_permission
        FOREIGN KEY (permission_id) REFERENCES cs_permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- cs_groups_permissions_aud
CREATE TABLE cs_groups_permissions_aud (
  rev INT NOT NULL,
  revtype TINYINT NULL,

  group_id BINARY(16) NOT NULL,
  permission_id BINARY(16) NOT NULL,

  PRIMARY KEY (rev, group_id, permission_id),
  CONSTRAINT fk_cs_groups_permissions_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_cs_groups_permissions_aud_rev ON cs_groups_permissions_aud (rev);

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
CREATE TABLE cs_password_history_aud (
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
  password_hash VARCHAR(255) NULL,

  PRIMARY KEY (id, rev),
  CONSTRAINT fk_cs_password_history_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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

-- cs_invite_token_aud
CREATE TABLE cs_invite_token_aud (
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

  CONSTRAINT pk_cs_invite_tokens_aud PRIMARY KEY (id, REV),
  CONSTRAINT fk_cs_invite_tokens_aud_rev FOREIGN KEY (REV) REFERENCES revinfo(rev)
) ENGINE=InnoDB;
CREATE INDEX ix_cs_invite_tokens_aud_rev ON cs_invite_token_aud(REV);
CREATE INDEX ix_cs_invite_tokens_aud_user_rev ON cs_invite_token_aud(user_id, REV);

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

-- cs_reset_token_aud
CREATE TABLE cs_reset_token_aud (
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

  CONSTRAINT pk_cs_reset_token_aud PRIMARY KEY (id, REV),
  CONSTRAINT fk_cs_reset_token_aud_rev FOREIGN KEY (REV) REFERENCES revinfo(rev)
) ENGINE=InnoDB;
CREATE INDEX ix_cs_reset_token_aud_rev ON cs_reset_token_aud(REV);
CREATE INDEX ix_cs_reset_token_aud_user_rev ON cs_reset_token_aud(user_id, REV);

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
