-- Spring Authorization Server schema (MySQL 8.x)

CREATE TABLE oauth2_registered_client (
  id varchar(100) NOT NULL,
  client_id varchar(100) NOT NULL,
  client_id_issued_at timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  client_secret varchar(200) DEFAULT NULL,
  client_secret_expires_at timestamp NULL DEFAULT NULL,
  client_name varchar(200) NOT NULL,
  client_authentication_methods varchar(1000) NOT NULL,
  authorization_grant_types varchar(1000) NOT NULL,
  redirect_uris varchar(1000) DEFAULT NULL,
  post_logout_redirect_uris varchar(1000) DEFAULT NULL,
  scopes varchar(1000) NOT NULL,
  client_settings varchar(2000) NOT NULL,
  token_settings varchar(2000) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_oauth2_registered_client_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oauth2_authorization (
  id varchar(100) NOT NULL,
  registered_client_id varchar(100) NOT NULL,
  principal_name varchar(200) NOT NULL,
  authorization_grant_type varchar(100) NOT NULL,
  authorized_scopes varchar(1000) DEFAULT NULL,
  attributes longtext DEFAULT NULL,
  state varchar(500) DEFAULT NULL,

  authorization_code_value longtext DEFAULT NULL,
  authorization_code_issued_at timestamp NULL DEFAULT NULL,
  authorization_code_expires_at timestamp NULL DEFAULT NULL,
  authorization_code_metadata longtext DEFAULT NULL,

  access_token_value longtext DEFAULT NULL,
  access_token_issued_at timestamp NULL DEFAULT NULL,
  access_token_expires_at timestamp NULL DEFAULT NULL,
  access_token_metadata longtext DEFAULT NULL,
  access_token_type varchar(100) DEFAULT NULL,
  access_token_scopes varchar(1000) DEFAULT NULL,

  oidc_id_token_value longtext DEFAULT NULL,
  oidc_id_token_issued_at timestamp NULL DEFAULT NULL,
  oidc_id_token_expires_at timestamp NULL DEFAULT NULL,
  oidc_id_token_metadata longtext DEFAULT NULL,

  refresh_token_value longtext DEFAULT NULL,
  refresh_token_issued_at timestamp NULL DEFAULT NULL,
  refresh_token_expires_at timestamp NULL DEFAULT NULL,
  refresh_token_metadata longtext DEFAULT NULL,

  user_code_value longtext DEFAULT NULL,
  user_code_issued_at timestamp NULL DEFAULT NULL,
  user_code_expires_at timestamp NULL DEFAULT NULL,
  user_code_metadata longtext DEFAULT NULL,

  device_code_value longtext DEFAULT NULL,
  device_code_issued_at timestamp NULL DEFAULT NULL,
  device_code_expires_at timestamp NULL DEFAULT NULL,
  device_code_metadata longtext DEFAULT NULL,

  PRIMARY KEY (id),
  KEY idx_oauth2_authorization_registered_client_id (registered_client_id),
  KEY idx_oauth2_authorization_principal_name (principal_name),
  KEY idx_oauth2_authorization_state (state),
  KEY idx_oauth2_authorization_authorization_code (authorization_code_value(100)),
  KEY idx_oauth2_authorization_access_token (access_token_value(100)),
  KEY idx_oauth2_authorization_refresh_token (refresh_token_value(100)),
  KEY idx_oauth2_authorization_oidc_id_token (oidc_id_token_value(100)),
  KEY idx_oauth2_authorization_user_code (user_code_value(100)),
  KEY idx_oauth2_authorization_device_code (device_code_value(100)),
  CONSTRAINT fk_oauth2_authorization_registered_client
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oauth2_authorization_consent (
  registered_client_id varchar(100) NOT NULL,
  principal_name varchar(200) NOT NULL,
  authorities varchar(1000) NOT NULL,
  PRIMARY KEY (registered_client_id, principal_name),
  CONSTRAINT fk_oauth2_authorization_consent_registered_client
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
