-- Armazena os pvNumbers encontrados em cada arquivo processado da adquirente.
-- Utilizado para enriquecer o calendário de arquivos com os estabelecimentos
-- cadastrados e ativos que aparecem em cada arquivo.
CREATE TABLE cs_processed_file_pv (
  processed_file_id BINARY(16) NOT NULL,
  pv_number         INT        NOT NULL,
  PRIMARY KEY (processed_file_id, pv_number),
  CONSTRAINT fk_cs_processed_file_pv_file
    FOREIGN KEY (processed_file_id) REFERENCES cs_processed_file (id)
    ON DELETE CASCADE
);

CREATE INDEX idx_cs_processed_file_pv_file ON cs_processed_file_pv (processed_file_id);
