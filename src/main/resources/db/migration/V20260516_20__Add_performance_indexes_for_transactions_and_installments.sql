-- =====================================================================
-- Performance indexes - transactions, installments and reconciliation
-- MySQL 8
--
-- Safe migration: creates indexes only when they do not exist.
-- This avoids failures when an index was already created in previous
-- consolidated migrations.
-- =====================================================================

DROP PROCEDURE IF EXISTS cs_create_index_if_missing;

DELIMITER $$

CREATE PROCEDURE cs_create_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_columns TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = p_table_name
           AND index_name = p_index_name
    ) THEN
        SET @sql = CONCAT(
            'CREATE INDEX `',
            REPLACE(p_index_name, '`', '``'),
            '` ON `',
            REPLACE(p_table_name, '`', '``'),
            '` (',
            p_index_columns,
            ')'
        );

        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- Acquirer transactions
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_sale_date_id',
    '`sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_company_sale_date',
    '`company_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_acquirer_sale_date',
    '`acquirer_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_establishment_sale_date',
    '`establishment_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_flag_sale_date',
    '`flag_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_modality_sale_date',
    '`modality`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_nsu',
    '`nsu`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_authorization',
    '`authorization`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_reconciliation_status_sale_date',
    '`status_transaction`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_sales_summary',
    '`sales_summary_id`, `id`');

CALL cs_create_index_if_missing('cs_transaction_acq', 'idx_acq_match_reconciliation',
    '`acquirer_id`, `nsu`, `authorization`, `gross_value`, `sale_date`, `flag_id`');

-- ---------------------------------------------------------------------
-- ERP transactions
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_sale_date_id',
    '`sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_company_sale_date',
    '`company_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_acquirer_sale_date',
    '`acquirer_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_establishment_sale_date',
    '`establishment_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_flag_sale_date',
    '`flag_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_modality_sale_date',
    '`modality`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_nsu',
    '`nsu`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_authorization',
    '`authorization`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_transaction_acq',
    '`transaction_acq_id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_status_sale_date',
    '`status_transaction`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_reconciliation_pending',
    '`status_transaction`, `transaction_acq_id`, `sale_date`, `id`');

CALL cs_create_index_if_missing('cs_transaction_erp', 'idx_erp_match_reconciliation',
    '`acquirer_id`, `nsu`, `authorization`, `gross_value`, `sale_date`, `flag_id`');

-- ---------------------------------------------------------------------
-- Acquirer installments
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_transaction',
    '`transaction_id`, `installment`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_expected_payment_date',
    '`expected_payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_payment_date',
    '`payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_status_expected_date',
    '`status_payment_bank`, `expected_payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_credit_order',
    '`credit_order_id`, `id`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_release_bank',
    '`release_bank_id`, `id`');

CALL cs_create_index_if_missing('cs_installment_acq', 'idx_inst_acq_transaction_expected_date',
    '`transaction_id`, `expected_payment_date`, `id`');

-- ---------------------------------------------------------------------
-- ERP installments
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_installment_erp', 'idx_inst_erp_transaction',
    '`transaction_id`, `installment`');

CALL cs_create_index_if_missing('cs_installment_erp', 'idx_inst_erp_expected_payment_date',
    '`expected_payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_erp', 'idx_inst_erp_status_expected_date',
    '`status_payment_bank`, `expected_payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_erp', 'idx_inst_erp_transaction_expected_date',
    '`transaction_id`, `expected_payment_date`, `id`');

CALL cs_create_index_if_missing('cs_installment_erp', 'idx_inst_erp_reconciliation_bank_file',
    '`reconciliation_bank_file_id`, `id`');

-- ---------------------------------------------------------------------
-- Sales summary
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_sales_summary', 'idx_sales_summary_rv_number', '`rv_number`');

CALL cs_create_index_if_missing('cs_sales_summary', 'idx_sales_summary_context',
    '`company_id`, `acquirer_id`, `flag_id`, `banking_domicile_id`, `rv_date`');

CALL cs_create_index_if_missing('cs_sales_summary', 'idx_sales_summary_status_rv_date',
    '`status_payment_bank`, `rv_date`, `id`');

-- ---------------------------------------------------------------------
-- Credit orders
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_credit_order', 'idx_credit_order_release_date', '`release_date`, `id`');

CALL cs_create_index_if_missing('cs_credit_order', 'idx_credit_order_context',
    '`company_id`, `acquirer_id`, `banking_domicile_id`, `flag_id`, `release_date`');

CALL cs_create_index_if_missing('cs_credit_order', 'idx_credit_order_status_release_date',
    '`reconciliation_status`, `release_date`, `id`');

CALL cs_create_index_if_missing('cs_credit_order', 'idx_credit_order_sales_summary',
    '`sales_summary_id`, `release_date`, `id`');

-- ---------------------------------------------------------------------
-- Bank releases
-- ---------------------------------------------------------------------

CALL cs_create_index_if_missing('cs_releases_bank', 'idx_release_bank_release_date', '`release_date`, `id`');

CALL cs_create_index_if_missing('cs_releases_bank', 'idx_release_bank_context',
    '`company_id`, `acquirer_id`, `banking_domicile_id`, `establishment_id`, `flag_id`, `release_date`');

CALL cs_create_index_if_missing('cs_releases_bank', 'idx_release_bank_status_release_date',
    '`reconciliation_status`, `release_date`, `id`');

CALL cs_create_index_if_missing('cs_releases_bank', 'idx_release_bank_value_date',
    '`release_value`, `release_date`, `id`');

DROP PROCEDURE IF EXISTS cs_create_index_if_missing;