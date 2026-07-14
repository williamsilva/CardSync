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
