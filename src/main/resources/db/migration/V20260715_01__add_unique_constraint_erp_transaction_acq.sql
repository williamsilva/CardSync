-- Impede duas vendas ERP vinculadas à mesma venda da adquirente (transaction_acq_id).
-- Sem isso, duas chamadas concorrentes a createErpFromAcquirer (duplo clique, retry, ação
-- manual concorrente com o lote automático) para o mesmo acquirerTransactionId podiam ambas
-- passar pelo check "já existe ERP vinculado?" antes de qualquer uma commitar, criando duas
-- TransactionErpEntity distintas para a mesma venda ADQ (venda duplicada nos totais do ERP).
-- Índice parcial (WHERE transaction_acq_id IS NOT NULL): a maioria das vendas ERP não tem
-- vínculo com a adquirente ainda (transaction_acq_id NULL), e UNIQUE do Postgres já trata
-- múltiplos NULLs como distintos entre si — o índice parcial só evita indexar essas linhas.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cs_transaction_erp_transaction_acq
  ON cs_transaction_erp (transaction_acq_id)
  WHERE transaction_acq_id IS NOT NULL;
