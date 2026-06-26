-- Índice funcional para consultas que usam LOWER(authorization) na conciliação ERP x Adquirente.
-- Sem este índice, a query `WHERE LOWER(authorization) IN (...)` faz full scan de toda a tabela.
-- MySQL 8.0.13+ suporta índices funcionais (expressão).
CREATE INDEX idx_transaction_acq_auth_lower
    ON cs_transaction_acq ((LOWER(authorization)));
