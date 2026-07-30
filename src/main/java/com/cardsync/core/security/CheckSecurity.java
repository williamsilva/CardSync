package com.cardsync.core.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

public @interface CheckSecurity {

  @Target(METHOD)
  @Retention(RUNTIME)
  @PreAuthorize("isAuthenticated()")
  @interface Authenticated {}

  @interface Register {
    @interface Banks {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultBanks()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveBanks()")
      @interface CanActiveOrInactive {}
    }

    @interface Companies {
        @Target(METHOD)
        @Retention(RUNTIME)
        @PreAuthorize("@csSecurity.canConsultCompanies()")
        @interface CanConsult {}

        @Target(METHOD)
        @Retention(RUNTIME)
        @PreAuthorize("@csSecurity.canCreateCompanies()")
        @interface CanCreate {}

        @Target(METHOD)
        @Retention(RUNTIME)
        @PreAuthorize("@csSecurity.canChangeCompanies()")
        @interface CanChange {}

        @Target(METHOD)
        @Retention(RUNTIME)
        @PreAuthorize("@csSecurity.canDeleteCompanies()")
        @interface CanDelete {}

        @Target(METHOD)
        @Retention(RUNTIME)
        @PreAuthorize("@csSecurity.canActiveOrInactiveCompanies()")
        @interface CanActiveOrInactive {}
      }

    @interface Acquirers {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAcquirers()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateAcquirers()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeAcquirers()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteAcquirers()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveAcquirers()")
      @interface CanActiveOrInactive {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canManageRelationsAcquirers()")
      @interface CanManageRelations {}
    }

    @interface Establishments {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultEstablishments()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateEstablishments()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeEstablishments()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteEstablishments()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveEstablishments()")
      @interface CanActiveOrInactive {}
    }

    @interface Flags {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultFlags()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateFlags()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeFlags()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteFlags()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveFlags()")
      @interface CanActiveOrInactive {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canManageRelationsFlags()")
      @interface CanManageRelations {}
    }

    @interface Contracts {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultContracts()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateContracts()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeContracts()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteContracts()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveContracts()")
      @interface CanActiveOrInactive {}
    }

    @interface Holidays {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultHolidays()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateHolidays()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeHolidays()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteHolidays()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveHolidays()")
      @interface CanActiveOrInactive {}
    }

    @interface NoFileDays {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultNoFileDays()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateNoFileDays()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeNoFileDays()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteNoFileDays()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveNoFileDays()")
      @interface CanActiveOrInactive {}
    }

    @interface BankingDomiciles {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultBankingDomiciles()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateBankingDomiciles()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeBankingDomiciles()")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveBankingDomiciles()")
      @interface CanActiveOrInactive {}
    }
  }

  @interface Documents {
    @interface ErpSales {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultErpSales()")
      @interface CanConsult {}
    }

    @interface ErpInstallments {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultErpInstallments()")
      @interface CanConsult {}
    }

    @interface AcquirersSales {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAcquirersSales()")
      @interface CanConsult {}
    }

    @interface AcquirersInstallments {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAcquirersInstallments()")
      @interface CanConsult {}
    }

    @interface Anticipation {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAnticipation()")
      @interface CanConsult {}
    }

    @interface SalesSummary {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultSalesSummary()")
      @interface CanConsult {}
    }

    @interface CreditOrder {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultCreditOrder()")
      @interface CanConsult {}
    }

    @interface BankStatement {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultBankStatement()")
      @interface CanConsult {}
    }

    @interface AdjustmentCancellation {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAdjustmentCancellation()")
      @interface CanConsult {}
    }

    @interface AdjustmentChargeBackRequests {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAdjustmentChargeBackRequests()")
      @interface CanConsult {}
    }

    @interface AdjustmentTariffs {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAdjustmentTariffs()")
      @interface CanConsult {}
    }

    @interface ChargebackAnalysis {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultChargebackAnalysis()")
      @interface CanConsult {}
    }

    @interface ContractAudit {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultContractAudit()")
      @interface CanConsult {}
    }
  }

  @interface Reconciliation {
    @interface ConciliationWaiting {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultConciliationWaiting()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessConciliationWaiting()")
      @interface CanProcess {}
    }

    @interface ManualBankReconciliation {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessManualBankReconciliation()")
      @interface CanProcess {}
    }

    @interface BankAcquirerConciliation {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessBankAcquirerConciliation()")
      @interface CanProcess {}
    }

    @interface FinancialReconciliationPipeline {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultFinancialReconciliationPipeline()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessFinancialReconciliationPipeline()")
      @interface CanProcess {}
    }
  }

  @interface Settings {
    @interface SchedulerSettings {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultSchedulerSettings()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessSchedulerSettings()")
      @interface CanProcess {}
    }

    @interface EmailSettings {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultEmailSettings()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessEmailSettings()")
      @interface CanProcess {}
    }

    @interface ReconciliationSettings {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultReconciliationSettings()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessReconciliationSettings()")
      @interface CanProcess {}
    }

    @interface Backup {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canProcessBackup()")
      @interface CanProcess {}
    }
  }

  @interface Management {
    @interface ManagementDashboard {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultManagementDashboard()")
      @interface CanConsult {}
    }

    @interface DashboardAudit {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultDashboardAudit()")
      @interface CanConsult {}
    }
  }

  @interface FileProcessing {

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@csSecurity.canReadFileProcessing()")
    @interface CanRead {}

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@csSecurity.canProcessFiles()")
    @interface CanProcess {}

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@csSecurity.canReprocessFiles()")
    @interface CanReprocess {}
  }

  @interface Audit {

    @interface Mail {

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAuditMail()")
      @interface CanConsultAuditMail{ }
    }
  }
}
