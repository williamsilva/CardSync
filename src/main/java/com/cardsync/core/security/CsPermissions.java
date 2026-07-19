package com.cardsync.core.security;

public abstract class CsPermissions {

  public static final String ROLE_SUPPORT = "ROLE_SUPPORT";

  /* Permission for Companies */
  protected static final String PERM_COMPANIES_CHANGE = "PERM_COMPANIES_CHANGE";
  protected static final String PERM_COMPANIES_CREATE = "PERM_COMPANIES_CREATE";
  protected static final String PERM_COMPANIES_DELETE = "PERM_COMPANIES_DELETE";
  protected static final String PERM_COMPANIES_CONSULT = "PERM_COMPANIES_CONSULT";
  protected static final String PERM_COMPANIES_ACTIVE_OR_INACTIVE = "PERM_COMPANIES_ACTIVE_OR_INACTIVE";

  /* Permission for Banks */
  protected static final String PERM_BANK_CONSULT = "PERM_BANK_CONSULT";
  protected static final String PERM_BANK_ACTIVE_OR_INACTIVE = "PERM_BANK_ACTIVE_OR_INACTIVE";

  /* Permission for Acquirers */
  protected static final String PERM_ACQUIRER_CHANGE = "PERM_ACQUIRER_CHANGE";
  protected static final String PERM_ACQUIRER_CREATE = "PERM_ACQUIRER_CREATE";
  protected static final String PERM_ACQUIRER_DELETE = "PERM_ACQUIRER_DELETE";
  protected static final String PERM_ACQUIRER_CONSULT = "PERM_ACQUIRER_CONSULT";
  protected static final String PERM_ACQUIRER_MANAGE_RELATIONS = "PERM_ACQUIRER_MANAGE_RELATIONS";
  protected static final String PERM_ACQUIRER_ACTIVE_OR_INACTIVE = "PERM_ACQUIRER_ACTIVE_OR_INACTIVE";

  /* Permission for Establishments */
  protected static final String PERM_ESTABLISHMENT_CHANGE = "PERM_ESTABLISHMENT_CHANGE";
  protected static final String PERM_ESTABLISHMENT_CREATE = "PERM_ESTABLISHMENT_CREATE";
  protected static final String PERM_ESTABLISHMENT_DELETE = "PERM_ESTABLISHMENT_DELETE";
  protected static final String PERM_ESTABLISHMENT_CONSULT = "PERM_ESTABLISHMENT_CONSULT";
  protected static final String PERM_ESTABLISHMENT_ACTIVE_OR_INACTIVE = "PERM_ESTABLISHMENT_ACTIVE_OR_INACTIVE";

  /* Permission for Flags */
  protected static final String PERM_FLAGS_CHANGE = "PERM_FLAGS_CHANGE";
  protected static final String PERM_FLAGS_CREATE = "PERM_FLAGS_CREATE";
  protected static final String PERM_FLAGS_DELETE = "PERM_FLAGS_DELETE";
  protected static final String PERM_FLAGS_CONSULT = "PERM_FLAGS_CONSULT";
  protected static final String PERM_FLAGS_MANAGE_RELATIONS = "PERM_FLAGS_MANAGE_RELATIONS";
  protected static final String PERM_FLAGS_ACTIVE_OR_INACTIVE = "PERM_FLAGS_ACTIVE_OR_INACTIVE";

  /* Permission for Establishments */
  protected static final String PERM_CONTRACTS_CHANGE = "PERM_CONTRACTS_CHANGE";
  protected static final String PERM_CONTRACTS_CREATE = "PERM_CONTRACTS_CREATE";
  protected static final String PERM_CONTRACTS_DELETE = "PERM_CONTRACTS_DELETE";
  protected static final String PERM_CONTRACTS_CONSULT = "PERM_CONTRACTS_CONSULT";
  protected static final String PERM_CONTRACTS_ACTIVE_OR_INACTIVE = "PERM_CONTRACTS_ACTIVE_OR_INACTIVE";

  /* Permission for File Processing */
  protected static final String PERM_FILE_PROCESSING_READ = "PERM_FILE_PROCESSING_READ";
  protected static final String PERM_FILE_PROCESSING_PROCESS = "PERM_FILE_PROCESSING_PROCESS";
  protected static final String PERM_FILE_PROCESSING_REPROCESS = "PERM_FILE_PROCESSING_REPROCESS";

  /* Permission for Holidays */
  protected static final String PERM_HOLIDAY_CONSULT = "PERM_HOLIDAYS_CONSULT";
  protected static final String PERM_HOLIDAY_CREATE = "PERM_HOLIDAYS_CREATE";
  protected static final String PERM_HOLIDAY_CHANGE = "PERM_HOLIDAYS_CHANGE";
  protected static final String PERM_HOLIDAY_DELETE = "PERM_HOLIDAYS_DELETE";
  protected static final String PERM_HOLIDAY_ACTIVE_OR_INACTIVE = "PERM_HOLIDAYS_ACTIVE_OR_INACTIVE";

  /* Permission for No File Days */
  protected static final String PERM_NO_FILE_DAY_CONSULT = "PERM_NO_FILE_DAY_CONSULT";
  protected static final String PERM_NO_FILE_DAY_CREATE = "PERM_NO_FILE_DAY_CREATE";
  protected static final String PERM_NO_FILE_DAY_CHANGE = "PERM_NO_FILE_DAY_CHANGE";
  protected static final String PERM_NO_FILE_DAY_DELETE = "PERM_NO_FILE_DAY_DELETE";
  protected static final String PERM_NO_FILE_DAY_ACTIVE_OR_INACTIVE = "PERM_NO_FILE_DAY_ACTIVE_OR_INACTIVE";

  /* Permission for Anticipation */
  protected static final String PERM_ANTICIPATION_CONSULT = "PERM_ANTICIPATION_CONSULT";

  /* Permission for Audit */
  protected static final String PERM_AUDIT_MAIL_CONSULT = "PERM_AUDIT_MAIL_CONSULT";

  /* Permission for Banking Domicile */
  protected static final String PERM_BANKING_DOMICILE_CONSULT = "PERM_BANKING_DOMICILE_CONSULT";
  protected static final String PERM_BANKING_DOMICILE_CREATE = "PERM_BANKING_DOMICILE_CREATE";
  protected static final String PERM_BANKING_DOMICILE_CHANGE = "PERM_BANKING_DOMICILE_CHANGE";
  protected static final String PERM_BANKING_DOMICILE_ACTIVE_OR_INACTIVE = "PERM_BANKING_DOMICILE_ACTIVE_OR_INACTIVE";

  /* Permission for ERP Sales */
  protected static final String PERM_ERP_SALES_CONSULT = "PERM_ERP_SALES_CONSULT";

  /* Permission for ERP Installments */
  protected static final String PERM_ERP_INSTALLMENTS_CONSULT = "PERM_ERP_INSTALLMENTS_CONSULT";

  /* Permission for Acquirers Sales */
  protected static final String PERM_ACQUIRERS_SALES_CONSULT = "PERM_ACQUIRERS_SALES_CONSULT";

  /* Permission for Acquirers Installments */
  protected static final String PERM_ACQUIRERS_INSTALLMENTS_CONSULT = "PERM_ACQUIRERS_INSTALLMENTS_CONSULT";

  /* Permission for Sales Summary */
  protected static final String PERM_SALES_SUMMARY_CONSULT = "PERM_SALES_SUMMARY_CONSULT";

  /* Permission for Credit Order */
  protected static final String PERM_CREDIT_ORDER_CONSULT = "PERM_CREDIT_ORDER_CONSULT";

  /* Permission for Bank Statement */
  protected static final String PERM_BANK_STATEMENT_CONSULT = "PERM_BANK_STATEMENT_CONSULT";

  /* Permission for Adjustments */
  protected static final String PERM_ADJUSTMENT_CANCELLATION_CONSULT = "PERM_ADJUSTMENT_CANCELLATION_CONSULT";
  protected static final String PERM_ADJUSTMENT_CHARGEBACK_REQUESTS_CONSULT = "PERM_ADJUSTMENT_CHARGEBACK_REQUESTS_CONSULT";
  protected static final String PERM_ADJUSTMENT_TARIFFS_CONSULT = "PERM_ADJUSTMENT_TARIFFS_CONSULT";
  protected static final String PERM_CHARGEBACK_ANALYSIS_CONSULT = "PERM_CHARGEBACK_ANALYSIS_CONSULT";

  /* Permission for Contract Audit */
  protected static final String PERM_CONTRACT_AUDIT_CONSULT = "PERM_CONTRACT_AUDIT_CONSULT";

  /* Permission for Conciliation Waiting */
  protected static final String PERM_CONCILIATION_WAITING_CONSULT = "PERM_CONCILIATION_WAITING_CONSULT";
  protected static final String PERM_CONCILIATION_WAITING_PROCESS = "PERM_CONCILIATION_WAITING_PROCESS";

  /* Permission for Conciliation Dashboard */
  protected static final String PERM_CONCILIATION_DASHBOARD_CONSULT = "PERM_CONCILIATION_DASHBOARD_CONSULT";

  /* Permission for Manual Bank Reconciliation */
  protected static final String PERM_MANUAL_BANK_RECONCILIATION_PROCESS = "PERM_MANUAL_BANK_RECONCILIATION_PROCESS";

  /* Permission for Bank x Acquirer Conciliation */
  protected static final String PERM_BANK_ACQUIRER_CONCILIATION_PROCESS = "PERM_BANK_ACQUIRER_CONCILIATION_PROCESS";

  /* Permission for Financial Reconciliation Pipeline */
  protected static final String PERM_FINANCIAL_RECONCILIATION_PIPELINE_CONSULT = "PERM_FINANCIAL_RECONCILIATION_PIPELINE_CONSULT";
  protected static final String PERM_FINANCIAL_RECONCILIATION_PIPELINE_PROCESS = "PERM_FINANCIAL_RECONCILIATION_PIPELINE_PROCESS";

  /* Permission for Scheduler Settings */
  protected static final String PERM_SCHEDULER_SETTINGS_CONSULT = "PERM_SCHEDULER_SETTINGS_CONSULT";
  protected static final String PERM_SCHEDULER_SETTINGS_PROCESS = "PERM_SCHEDULER_SETTINGS_PROCESS";

  /* Permission for Email Settings */
  protected static final String PERM_EMAIL_SETTINGS_CONSULT = "PERM_EMAIL_SETTINGS_CONSULT";
  protected static final String PERM_EMAIL_SETTINGS_PROCESS = "PERM_EMAIL_SETTINGS_PROCESS";

  /* Permission for Reconciliation Settings */
  protected static final String PERM_RECONCILIATION_SETTINGS_CONSULT = "PERM_RECONCILIATION_SETTINGS_CONSULT";
  protected static final String PERM_RECONCILIATION_SETTINGS_PROCESS = "PERM_RECONCILIATION_SETTINGS_PROCESS";

  /* Permission for Management Dashboard */
  protected static final String PERM_MANAGEMENT_DASHBOARD_CONSULT = "PERM_MANAGEMENT_DASHBOARD_CONSULT";

  /* Permission for Dashboard Audit */
  protected static final String PERM_DASHBOARD_AUDIT_CONSULT = "PERM_DASHBOARD_AUDIT_CONSULT";
}
