-- =====================================================================
-- Índices faltando em colunas de FK (e alguns filtros de busca) — parte
-- da migração MySQL -> Postgres.
--
-- Motivo: InnoDB cria automaticamente um índice secundário para qualquer
-- coluna de FK que não seja a coluna líder de nenhum índice existente.
-- Postgres não faz isso — cada FK "de graça" no MySQL precisa de um
-- CREATE INDEX explícito aqui, ou os deletes/updates na tabela pai e os
-- joins por essa coluna caem em full scan.
--
-- Levantamento feito auditando as FKs de todas as tabelas cs_* contra os
-- índices já existentes (considerando só a coluna líder de cada índice,
-- que é a única que o planner consegue usar para busca por igualdade
-- nessa coluna isolada).
-- =====================================================================

-- cs_company / cs_acquirer: ContractSpecs busca por prefixo em fantasyName
-- (startsWithPath -> WHERE LOWER(fantasy_name) LIKE 'valor%'), mas os índices existentes
-- (idx_cs_company_fantasy_name / idx_cs_acquirer_fantasy_name) são na coluna crua, não em
-- LOWER() — nunca foram usados por essa busca, com ou sem locale pt-BR. Mantemos os
-- índices originais (úteis para igualdade/ORDER BY) e adicionamos a variante funcional
-- com text_pattern_ops (obrigatório porque o banco usa collation pt-BR, não "C"/POSIX).
CREATE INDEX IF NOT EXISTS idx_cs_company_fantasy_name_lower ON cs_company (LOWER(fantasy_name) text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_cs_acquirer_fantasy_name_lower ON cs_acquirer (LOWER(fantasy_name) text_pattern_ops);

-- cs_establishment
CREATE INDEX IF NOT EXISTS idx_cs_establishment_company_id ON cs_establishment (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_establishment_acquirer_id ON cs_establishment (acquirer_id);

-- cs_acquirer_establishment
CREATE INDEX IF NOT EXISTS idx_cs_acquirer_establishment_acquirer_id ON cs_acquirer_establishment (acquirer_id);

-- cs_acquirer_company
CREATE INDEX IF NOT EXISTS idx_cs_acquirer_company_company_id ON cs_acquirer_company (company_id);

-- cs_flag_acquirer
CREATE INDEX IF NOT EXISTS idx_cs_flag_acquirer_acquirer_id ON cs_flag_acquirer (acquirer_id);

-- cs_flag_company
CREATE INDEX IF NOT EXISTS idx_cs_flag_company_company_id ON cs_flag_company (company_id);

-- cs_banking_domicile
CREATE INDEX IF NOT EXISTS idx_cs_banking_domicile_bank_id ON cs_banking_domicile (bank_id);

-- cs_processed_file
CREATE INDEX IF NOT EXISTS idx_cs_processed_file_origin_file_id ON cs_processed_file (origin_file_id);

-- cs_sales_summary
CREATE INDEX IF NOT EXISTS idx_cs_sales_summary_flag_id ON cs_sales_summary (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_sales_summary_acquirer_id ON cs_sales_summary (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_sales_summary_processed_file_id ON cs_sales_summary (processed_file_id);

-- cs_installment_erp
CREATE INDEX IF NOT EXISTS idx_cs_installment_erp_reconciliation_payment_file_id ON cs_installment_erp (reconciliation_payment_file_id);

-- cs_installment_acq
CREATE INDEX IF NOT EXISTS idx_cs_installment_acq_reconciliation_bank_file_id ON cs_installment_acq (reconciliation_bank_file_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_acq_adjustment_id ON cs_installment_acq (adjustment_id);

-- cs_adjustment
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_rv_flag_adjustment_id ON cs_adjustment (rv_flag_adjustment_id);
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_rv_flag_origin_id ON cs_adjustment (rv_flag_origin_id);
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_acquirer_id ON cs_adjustment (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_transaction_id ON cs_adjustment (transaction_id);
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_sales_summary_id ON cs_adjustment (sales_summary_id);
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_establishment_id ON cs_adjustment (establishment_id);
-- Filtro de busca (tela de ajustes), não é FK. startsWith("authorization") gera
-- WHERE LOWER(authorization) LIKE 'valor%' — o índice precisa ser funcional em LOWER(),
-- não na coluna crua, e usar text_pattern_ops (collation do banco é pt-BR, não "C"/POSIX).
CREATE INDEX IF NOT EXISTS idx_cs_adjustment_authorization_lower ON cs_adjustment (LOWER("authorization") text_pattern_ops);

-- cs_rede_request_notice."authorization" — mesmo padrão de busca por prefixo
-- (ChargeBackRequestsAdvancedFields.authorization), sem nenhum índice até então.
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_authorization_lower ON cs_rede_request_notice (LOWER("authorization") text_pattern_ops);

-- cs_releases_bank
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_flag_id ON cs_releases_bank (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_bank_id ON cs_releases_bank (bank_id);
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_acquirer_id ON cs_releases_bank (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_establishment_id ON cs_releases_bank (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_processed_file_id ON cs_releases_bank (processed_file_id);
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_banking_domicile_id ON cs_releases_bank (banking_domicile_id);
-- Filtro de busca (tela de lançamentos bancários), não é FK.
CREATE INDEX IF NOT EXISTS idx_cs_releases_bank_modality_payment_bank ON cs_releases_bank (modality_payment_bank);

-- cs_credit_order
CREATE INDEX IF NOT EXISTS idx_cs_credit_order_flag_id ON cs_credit_order (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_order_acquirer_id ON cs_credit_order (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_order_processed_file_id ON cs_credit_order (processed_file_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_order_release_bank_id ON cs_credit_order (release_bank_id);
-- Filtro de busca (tela de ordens de crédito), não é FK.
CREATE INDEX IF NOT EXISTS idx_cs_credit_order_original_pv_number ON cs_credit_order (original_pv_number);

-- cs_anticipation
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_flag_id ON cs_anticipation (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_company_id ON cs_anticipation (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_acquirer_id ON cs_anticipation (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_processed_file_id ON cs_anticipation (processed_file_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_establishment_id ON cs_anticipation (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_sales_summary_id ON cs_anticipation (sales_summary_id);
CREATE INDEX IF NOT EXISTS idx_cs_anticipation_banking_domicile_id ON cs_anticipation (banking_domicile_id);

-- cs_credit_totalizer
CREATE INDEX IF NOT EXISTS idx_cs_credit_totalizer_acquirer_id ON cs_credit_totalizer (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_totalizer_company_id ON cs_credit_totalizer (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_totalizer_banking_domicile_id ON cs_credit_totalizer (banking_domicile_id);
CREATE INDEX IF NOT EXISTS idx_cs_credit_totalizer_processed_file_id ON cs_credit_totalizer (processed_file_id);

-- cs_settled_debt
CREATE INDEX IF NOT EXISTS idx_cs_settled_debt_flag_id ON cs_settled_debt (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_settled_debt_acquirer_id ON cs_settled_debt (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_settled_debt_processed_file_id ON cs_settled_debt (processed_file_id);

-- cs_pv_matrix_header
CREATE INDEX IF NOT EXISTS idx_cs_pv_matrix_header_acquirer_id ON cs_pv_matrix_header (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_pv_matrix_header_company_id ON cs_pv_matrix_header (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_pv_matrix_header_establishment_id ON cs_pv_matrix_header (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_pv_matrix_header_processed_file_id ON cs_pv_matrix_header (processed_file_id);

-- cs_serasa_consultation
CREATE INDEX IF NOT EXISTS idx_cs_serasa_consultation_acquirer_id ON cs_serasa_consultation (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_serasa_consultation_company_id ON cs_serasa_consultation (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_serasa_consultation_establishment_id ON cs_serasa_consultation (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_serasa_consultation_processed_file_id ON cs_serasa_consultation (processed_file_id);

-- cs_pending_debt
CREATE INDEX IF NOT EXISTS idx_cs_pending_debt_flag_id ON cs_pending_debt (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_pending_debt_acquirer_id ON cs_pending_debt (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_pending_debt_company_id ON cs_pending_debt (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_pending_debt_establishment_id ON cs_pending_debt (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_pending_debt_processed_file_id ON cs_pending_debt (processed_file_id);

-- cs_installment_unscheduling
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_flag_rv_origin_id ON cs_installment_unscheduling (flag_rv_origin_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_acquirer_id ON cs_installment_unscheduling (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_company_id ON cs_installment_unscheduling (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_establishment_id ON cs_installment_unscheduling (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_processed_file_id ON cs_installment_unscheduling (processed_file_id);
CREATE INDEX IF NOT EXISTS idx_cs_installment_unscheduling_flag_rv_adjusted_id ON cs_installment_unscheduling (flag_rv_adjusted_id);

-- cs_totalizer_matrix
CREATE INDEX IF NOT EXISTS idx_cs_totalizer_matrix_acquirer_id ON cs_totalizer_matrix (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_totalizer_matrix_company_id ON cs_totalizer_matrix (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_totalizer_matrix_establishment_id ON cs_totalizer_matrix (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_totalizer_matrix_processed_file_id ON cs_totalizer_matrix (processed_file_id);

-- cs_archive_trailer
CREATE INDEX IF NOT EXISTS idx_cs_archive_trailer_acquirer_id ON cs_archive_trailer (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_archive_trailer_company_id ON cs_archive_trailer (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_archive_trailer_establishment_id ON cs_archive_trailer (establishment_id);

-- cs_rede_request_notice
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_flag_id ON cs_rede_request_notice (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_acquirer_id ON cs_rede_request_notice (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_company_id ON cs_rede_request_notice (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_establishment_id ON cs_rede_request_notice (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_request_notice_sales_summary_id ON cs_rede_request_notice (sales_summary_id);

-- cs_rede_eevd_totalizer
CREATE INDEX IF NOT EXISTS idx_cs_rede_eevd_totalizer_acquirer_id ON cs_rede_eevd_totalizer (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_eevd_totalizer_company_id ON cs_rede_eevd_totalizer (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_eevd_totalizer_establishment_id ON cs_rede_eevd_totalizer (establishment_id);

-- cs_rede_negotiated_transaction
CREATE INDEX IF NOT EXISTS idx_cs_rede_negotiated_transaction_flag_id ON cs_rede_negotiated_transaction (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_negotiated_transaction_acquirer_id ON cs_rede_negotiated_transaction (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_negotiated_transaction_company_id ON cs_rede_negotiated_transaction (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_negotiated_transaction_establishment_id ON cs_rede_negotiated_transaction (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_negotiated_transaction_sales_summary_id ON cs_rede_negotiated_transaction (sales_summary_id);

-- cs_rede_ic_plus_transaction
CREATE INDEX IF NOT EXISTS idx_cs_rede_ic_plus_transaction_acquirer_id ON cs_rede_ic_plus_transaction (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_ic_plus_transaction_company_id ON cs_rede_ic_plus_transaction (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_ic_plus_transaction_establishment_id ON cs_rede_ic_plus_transaction (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_ic_plus_transaction_sales_summary_id ON cs_rede_ic_plus_transaction (sales_summary_id);

-- cs_rede_pix_cancellation
CREATE INDEX IF NOT EXISTS idx_cs_rede_pix_cancellation_acquirer_id ON cs_rede_pix_cancellation (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_pix_cancellation_company_id ON cs_rede_pix_cancellation (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_pix_cancellation_establishment_id ON cs_rede_pix_cancellation (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_pix_cancellation_processed_file_id ON cs_rede_pix_cancellation (processed_file_id);

-- cs_rede_suspended_payment
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_flag_id ON cs_rede_suspended_payment (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_acquirer_id ON cs_rede_suspended_payment (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_company_id ON cs_rede_suspended_payment (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_establishment_id ON cs_rede_suspended_payment (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_sales_summary_id ON cs_rede_suspended_payment (sales_summary_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_suspended_payment_processed_file_id ON cs_rede_suspended_payment (processed_file_id);

-- cs_rede_technical_reserve
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_flag_id ON cs_rede_technical_reserve (flag_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_acquirer_id ON cs_rede_technical_reserve (acquirer_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_company_id ON cs_rede_technical_reserve (company_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_establishment_id ON cs_rede_technical_reserve (establishment_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_sales_summary_id ON cs_rede_technical_reserve (sales_summary_id);
CREATE INDEX IF NOT EXISTS idx_cs_rede_technical_reserve_processed_file_id ON cs_rede_technical_reserve (processed_file_id);

-- Filtros de busca sem FK associada, confirmados sem cobertura nas telas de transações.
CREATE INDEX IF NOT EXISTS idx_cs_transaction_acq_capture ON cs_transaction_acq (capture);
CREATE INDEX IF NOT EXISTS idx_cs_transaction_erp_capture ON cs_transaction_erp (capture);
