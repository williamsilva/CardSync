package com.cardsync.core.security;

import org.springframework.stereotype.Component;

@Component
public class CsSecurity extends CsDefaultSecurityMethod {

  public boolean hasAllReadScope() {
    return true;
  }

  public boolean hasAllWriteScope() {
    return true;
  }

  public boolean canConsultAuditMail() {
    return hasAllReadScope() && hasAuthority(PERM_AUDIT_MAIL_CONSULT);
  }

  /* Companies */
  public boolean canConsultCompanies() {
    return hasAllReadScope() && hasAuthority(PERM_COMPANIES_CONSULT);
  }

  public boolean canCreateCompanies() {
    return hasAllWriteScope() && hasAuthority(PERM_COMPANIES_CREATE);
  }

  public boolean canChangeCompanies() {
    return hasAllWriteScope() && hasAuthority(PERM_COMPANIES_CHANGE);
  }

  public boolean canDeleteCompanies() {
    return hasAllWriteScope() && hasAuthority(PERM_COMPANIES_DELETE);
  }

  public boolean canActiveOrInactiveCompanies() {
    return hasAllWriteScope() && hasAuthority(PERM_COMPANIES_ACTIVE_OR_INACTIVE);
  }

  /* Banks */
  public boolean canConsultBanks() {
    return hasAllReadScope() && hasAuthority(PERM_BANK_CONSULT);
  }

  public boolean canActiveOrInactiveBanks() {
    return hasAllWriteScope() && hasAuthority(PERM_BANK_ACTIVE_OR_INACTIVE);
  }

  /* Acquirers */
  public boolean canConsultAcquirers() {
    return hasAllReadScope() && hasAuthority(PERM_ACQUIRER_CONSULT);
  }

  public boolean canCreateAcquirers() {
    return hasAllWriteScope() && hasAuthority(PERM_ACQUIRER_CREATE);
  }

  public boolean canChangeAcquirers() {
    return hasAllWriteScope() && hasAuthority(PERM_ACQUIRER_CHANGE);
  }

  public boolean canDeleteAcquirers() {
    return hasAllWriteScope() && hasAuthority(PERM_ACQUIRER_DELETE);
  }

  public boolean canActiveOrInactiveAcquirers() {
    return hasAllWriteScope() && hasAuthority(PERM_ACQUIRER_ACTIVE_OR_INACTIVE);
  }

  public boolean canManageRelationsAcquirers() {
    return hasAllWriteScope() && hasAuthority(PERM_ACQUIRER_MANAGE_RELATIONS);
  }

  /* Establishments */
  public boolean canConsultEstablishments() {
    return hasAllReadScope() && hasAuthority(PERM_ESTABLISHMENT_CONSULT);
  }

  public boolean canCreateEstablishments() {
    return hasAllWriteScope() && hasAuthority(PERM_ESTABLISHMENT_CREATE);
  }

  public boolean canChangeEstablishments() {
    return hasAllWriteScope() && hasAuthority(PERM_ESTABLISHMENT_CHANGE);
  }

  public boolean canDeleteEstablishments() {
    return hasAllWriteScope() && hasAuthority(PERM_ESTABLISHMENT_DELETE);
  }

  public boolean canActiveOrInactiveEstablishments() {
    return hasAllWriteScope() && hasAuthority(PERM_ESTABLISHMENT_ACTIVE_OR_INACTIVE);
  }

  /* Flags */
  public boolean canConsultFlags() {
    return hasAllReadScope() && hasAuthority(PERM_FLAGS_CONSULT);
  }

  public boolean canCreateFlags() {
    return hasAllWriteScope() && hasAuthority(PERM_FLAGS_CREATE);
  }

  public boolean canChangeFlags() {
    return hasAllWriteScope() && hasAuthority(PERM_FLAGS_CHANGE);
  }

  public boolean canDeleteFlags() {
    return hasAllWriteScope() && hasAuthority(PERM_FLAGS_DELETE);
  }

  public boolean canActiveOrInactiveFlags() {
    return hasAllWriteScope() && hasAuthority(PERM_FLAGS_ACTIVE_OR_INACTIVE);
  }

  public boolean canManageRelationsFlags() {
    return hasAllWriteScope() && hasAuthority(PERM_FLAGS_MANAGE_RELATIONS);
  }

  /* File Processing */
  public boolean canReadFileProcessing() {
    return hasAllReadScope() && hasAuthority(PERM_FILE_PROCESSING_READ);
  }

  public boolean canProcessFiles() {
    return hasAllWriteScope() && hasAuthority(PERM_FILE_PROCESSING_PROCESS);
  }

  public boolean canReprocessFiles() {
    return hasAllWriteScope() && hasAuthority(PERM_FILE_PROCESSING_REPROCESS);
  }

  /* Contracts */
  public boolean canConsultContracts() {
    return hasAllReadScope() && hasAuthority(PERM_CONTRACTS_CONSULT);
  }

  public boolean canCreateContracts() {
    return hasAllWriteScope() && hasAuthority(PERM_CONTRACTS_CREATE);
  }

  public boolean canChangeContracts() {
    return hasAllWriteScope() && hasAuthority(PERM_CONTRACTS_CHANGE);
  }

  public boolean canDeleteContracts() {
    return hasAllWriteScope() && hasAuthority(PERM_CONTRACTS_DELETE);
  }

  public boolean canActiveOrInactiveContracts() {
    return hasAllWriteScope() && hasAuthority(PERM_CONTRACTS_ACTIVE_OR_INACTIVE);
  }

  /* Holidays */
  public boolean canConsultHolidays() {
    return hasAllReadScope() && hasAuthority(PERM_HOLIDAY_CONSULT);
  }

  public boolean canCreateHolidays() {
    return hasAllWriteScope() && hasAuthority(PERM_HOLIDAY_CREATE);
  }

  public boolean canChangeHolidays() {
    return hasAllWriteScope() && hasAuthority(PERM_HOLIDAY_CHANGE);
  }

  public boolean canDeleteHolidays() {
    return hasAllWriteScope() && hasAuthority(PERM_HOLIDAY_DELETE);
  }

  public boolean canActiveOrInactiveHolidays() {
    return hasAllWriteScope() && hasAuthority(PERM_HOLIDAY_ACTIVE_OR_INACTIVE);
  }

  /* No File Days */
  public boolean canConsultNoFileDays() {
    return hasAllReadScope() && hasAuthority(PERM_NO_FILE_DAY_CONSULT);
  }

  public boolean canCreateNoFileDays() {
    return hasAllWriteScope() && hasAuthority(PERM_NO_FILE_DAY_CREATE);
  }

  public boolean canChangeNoFileDays() {
    return hasAllWriteScope() && hasAuthority(PERM_NO_FILE_DAY_CHANGE);
  }

  public boolean canDeleteNoFileDays() {
    return hasAllWriteScope() && hasAuthority(PERM_NO_FILE_DAY_DELETE);
  }

  public boolean canActiveOrInactiveNoFileDays() {
    return hasAllWriteScope() && hasAuthority(PERM_NO_FILE_DAY_ACTIVE_OR_INACTIVE);
  }

  /* Banking Domicile */
  public boolean canConsultBankingDomiciles() {
    return hasAllReadScope() && hasAuthority(PERM_BANKING_DOMICILE_CONSULT);
  }

  public boolean canCreateBankingDomiciles() {
    return hasAllWriteScope() && hasAuthority(PERM_BANKING_DOMICILE_CREATE);
  }

  public boolean canChangeBankingDomiciles() {
    return hasAllWriteScope() && hasAuthority(PERM_BANKING_DOMICILE_CHANGE);
  }

  public boolean canActiveOrInactiveBankingDomiciles() {
    return hasAllWriteScope() && hasAuthority(PERM_BANKING_DOMICILE_ACTIVE_OR_INACTIVE);
  }

  /* ERP Sales */
  public boolean canConsultErpSales() {
    return hasAllReadScope() && hasAuthority(PERM_ERP_SALES_CONSULT);
  }

  /* ERP Installments */
  public boolean canConsultErpInstallments() {
    return hasAllReadScope() && hasAuthority(PERM_ERP_INSTALLMENTS_CONSULT);
  }

  /* Acquirers Sales */
  public boolean canConsultAcquirersSales() {
    return hasAllReadScope() && hasAuthority(PERM_ACQUIRERS_SALES_CONSULT);
  }

  /* Acquirers Installments */
  public boolean canConsultAcquirersInstallments() {
    return hasAllReadScope() && hasAuthority(PERM_ACQUIRERS_INSTALLMENTS_CONSULT);
  }

  /* Anticipation */
  public boolean canConsultAnticipation() {
    return hasAllReadScope() && hasAuthority(PERM_ANTICIPATION_CONSULT);
  }

  /* Sales Summary */
  public boolean canConsultSalesSummary() {
    return hasAllReadScope() && hasAuthority(PERM_SALES_SUMMARY_CONSULT);
  }

  /* Credit Order */
  public boolean canConsultCreditOrder() {
    return hasAllReadScope() && hasAuthority(PERM_CREDIT_ORDER_CONSULT);
  }

  /* Bank Statement */
  public boolean canConsultBankStatement() {
    return hasAllReadScope() && hasAuthority(PERM_BANK_STATEMENT_CONSULT);
  }

  /* Adjustments */
  public boolean canConsultAdjustmentCancellation() {
    return hasAllReadScope() && hasAuthority(PERM_ADJUSTMENT_CANCELLATION_CONSULT);
  }

  public boolean canConsultAdjustmentChargeBackRequests() {
    return hasAllReadScope() && hasAuthority(PERM_ADJUSTMENT_CHARGEBACK_REQUESTS_CONSULT);
  }

  public boolean canConsultAdjustmentTariffs() {
    return hasAllReadScope() && hasAuthority(PERM_ADJUSTMENT_TARIFFS_CONSULT);
  }

  public boolean canConsultChargebackAnalysis() {
    return hasAllReadScope() && hasAuthority(PERM_CHARGEBACK_ANALYSIS_CONSULT);
  }

  /* Contract Audit */
  public boolean canConsultContractAudit() {
    return hasAllReadScope() && hasAuthority(PERM_CONTRACT_AUDIT_CONSULT);
  }

  /* Conciliation Waiting */
  public boolean canConsultConciliationWaiting() {
    return hasAllReadScope() && hasAuthority(PERM_CONCILIATION_WAITING_CONSULT);
  }

  public boolean canProcessConciliationWaiting() {
    return hasAllWriteScope() && hasAuthority(PERM_CONCILIATION_WAITING_PROCESS);
  }

  /* Conciliation Dashboard */
  public boolean canConsultConciliationDashboard() {
    return hasAllReadScope() && hasAuthority(PERM_CONCILIATION_DASHBOARD_CONSULT);
  }

  /* Manual Bank Reconciliation */
  public boolean canProcessManualBankReconciliation() {
    return hasAllWriteScope() && hasAuthority(PERM_MANUAL_BANK_RECONCILIATION_PROCESS);
  }

  /* Bank x Acquirer Conciliation */
  public boolean canProcessBankAcquirerConciliation() {
    return hasAllWriteScope() && hasAuthority(PERM_BANK_ACQUIRER_CONCILIATION_PROCESS);
  }

  /* Financial Reconciliation Pipeline */
  public boolean canConsultFinancialReconciliationPipeline() {
    return hasAllReadScope() && hasAuthority(PERM_FINANCIAL_RECONCILIATION_PIPELINE_CONSULT);
  }

  public boolean canProcessFinancialReconciliationPipeline() {
    return hasAllWriteScope() && hasAuthority(PERM_FINANCIAL_RECONCILIATION_PIPELINE_PROCESS);
  }

  /* Scheduler Settings */
  public boolean canConsultSchedulerSettings() {
    return hasAllReadScope() && hasAuthority(PERM_SCHEDULER_SETTINGS_CONSULT);
  }

  public boolean canProcessSchedulerSettings() {
    return hasAllWriteScope() && hasAuthority(PERM_SCHEDULER_SETTINGS_PROCESS);
  }

  /* Email Settings */
  public boolean canConsultEmailSettings() {
    return hasAllReadScope() && hasAuthority(PERM_EMAIL_SETTINGS_CONSULT);
  }

  public boolean canProcessEmailSettings() {
    return hasAllWriteScope() && hasAuthority(PERM_EMAIL_SETTINGS_PROCESS);
  }

  /* Reconciliation Settings */
  public boolean canConsultReconciliationSettings() {
    return hasAllReadScope() && hasAuthority(PERM_RECONCILIATION_SETTINGS_CONSULT);
  }

  public boolean canProcessReconciliationSettings() {
    return hasAllWriteScope() && hasAuthority(PERM_RECONCILIATION_SETTINGS_PROCESS);
  }

  /* Management Dashboard */
  public boolean canConsultManagementDashboard() {
    return hasAllReadScope() && hasAuthority(PERM_MANAGEMENT_DASHBOARD_CONSULT);
  }

  /* Dashboard Audit */
  public boolean canConsultDashboardAudit() {
    return hasAllReadScope() && hasAuthority(PERM_DASHBOARD_AUDIT_CONSULT);
  }
}
