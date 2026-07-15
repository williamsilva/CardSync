-- =====================================================================
-- Performance indexes - transactions, installments and reconciliation
-- Postgres: CREATE INDEX IF NOT EXISTS já é idempotente nativamente,
-- sem precisar da procedure condicional usada no MySQL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Acquirer transactions
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_acq_sale_date_id ON cs_transaction_acq (sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_company_sale_date ON cs_transaction_acq (company_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_acquirer_sale_date ON cs_transaction_acq (acquirer_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_establishment_sale_date ON cs_transaction_acq (establishment_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_flag_sale_date ON cs_transaction_acq (flag_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_modality_sale_date ON cs_transaction_acq (modality, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_nsu ON cs_transaction_acq (nsu);

CREATE INDEX IF NOT EXISTS idx_acq_authorization ON cs_transaction_acq ("authorization");

CREATE INDEX IF NOT EXISTS idx_acq_reconciliation_status_sale_date ON cs_transaction_acq (status_transaction, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_acq_sales_summary ON cs_transaction_acq (sales_summary_id, id);

CREATE INDEX IF NOT EXISTS idx_acq_match_reconciliation ON cs_transaction_acq (acquirer_id, nsu, "authorization", gross_value, sale_date, flag_id);

-- ---------------------------------------------------------------------
-- ERP transactions
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_erp_sale_date_id ON cs_transaction_erp (sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_company_sale_date ON cs_transaction_erp (company_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_acquirer_sale_date ON cs_transaction_erp (acquirer_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_establishment_sale_date ON cs_transaction_erp (establishment_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_flag_sale_date ON cs_transaction_erp (flag_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_modality_sale_date ON cs_transaction_erp (modality, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_nsu ON cs_transaction_erp (nsu);

CREATE INDEX IF NOT EXISTS idx_erp_authorization ON cs_transaction_erp ("authorization");

CREATE INDEX IF NOT EXISTS idx_erp_transaction_acq ON cs_transaction_erp (transaction_acq_id);

CREATE INDEX IF NOT EXISTS idx_erp_status_sale_date ON cs_transaction_erp (status_transaction, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_reconciliation_pending ON cs_transaction_erp (status_transaction, transaction_acq_id, sale_date, id);

CREATE INDEX IF NOT EXISTS idx_erp_match_reconciliation ON cs_transaction_erp (acquirer_id, nsu, "authorization", gross_value, sale_date, flag_id);

-- ---------------------------------------------------------------------
-- Acquirer installments
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_inst_acq_transaction ON cs_installment_acq (transaction_id, installment);

CREATE INDEX IF NOT EXISTS idx_inst_acq_expected_payment_date ON cs_installment_acq (expected_payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_acq_payment_date ON cs_installment_acq (payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_acq_status_expected_date ON cs_installment_acq (status_payment_bank, expected_payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_acq_credit_order ON cs_installment_acq (credit_order_id, id);

CREATE INDEX IF NOT EXISTS idx_inst_acq_release_bank ON cs_installment_acq (release_bank_id, id);

CREATE INDEX IF NOT EXISTS idx_inst_acq_transaction_expected_date ON cs_installment_acq (transaction_id, expected_payment_date, id);

-- ---------------------------------------------------------------------
-- ERP installments
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_inst_erp_transaction ON cs_installment_erp (transaction_id, installment);

CREATE INDEX IF NOT EXISTS idx_inst_erp_expected_payment_date ON cs_installment_erp (expected_payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_erp_status_expected_date ON cs_installment_erp (status_payment_bank, expected_payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_erp_transaction_expected_date ON cs_installment_erp (transaction_id, expected_payment_date, id);

CREATE INDEX IF NOT EXISTS idx_inst_erp_reconciliation_bank_file ON cs_installment_erp (reconciliation_bank_file_id, id);

-- ---------------------------------------------------------------------
-- Sales summary
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_sales_summary_rv_number ON cs_sales_summary (rv_number);

CREATE INDEX IF NOT EXISTS idx_sales_summary_context ON cs_sales_summary (company_id, acquirer_id, flag_id, banking_domicile_id, rv_date);

CREATE INDEX IF NOT EXISTS idx_sales_summary_status_rv_date ON cs_sales_summary (status_payment_bank, rv_date, id);

-- ---------------------------------------------------------------------
-- Credit orders
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_credit_order_release_date ON cs_credit_order (release_date, id);

CREATE INDEX IF NOT EXISTS idx_credit_order_context ON cs_credit_order (company_id, acquirer_id, banking_domicile_id, flag_id, release_date);

CREATE INDEX IF NOT EXISTS idx_credit_order_status_release_date ON cs_credit_order (reconciliation_status, release_date, id);

CREATE INDEX IF NOT EXISTS idx_credit_order_sales_summary ON cs_credit_order (sales_summary_id, release_date, id);

-- ---------------------------------------------------------------------
-- Bank releases
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_release_bank_release_date ON cs_releases_bank (release_date, id);

CREATE INDEX IF NOT EXISTS idx_release_bank_context ON cs_releases_bank (company_id, acquirer_id, banking_domicile_id, establishment_id, flag_id, release_date);

CREATE INDEX IF NOT EXISTS idx_release_bank_status_release_date ON cs_releases_bank (reconciliation_status, release_date, id);

CREATE INDEX IF NOT EXISTS idx_release_bank_value_date ON cs_releases_bank (release_value, release_date, id);
