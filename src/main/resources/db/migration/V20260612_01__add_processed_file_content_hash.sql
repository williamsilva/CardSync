ALTER TABLE cs_processed_file ADD COLUMN content_hash VARCHAR(64) NULL AFTER file_name;

CREATE UNIQUE INDEX uq_cs_processed_file_content_hash ON cs_processed_file (content_hash);
