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

  @interface Security {

    @interface Users {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultUsers()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateUsers()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeUsers(#id)")
      @interface CanChange {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangePasswordUsers()")
      @interface CanChangePassword {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canActiveOrInactiveUser(#id)")
      @interface CanActiveOrInactiveUser {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteUsers()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCanResendInvite()")
      @interface CanResendInvite {}
    }

    @interface Groups {
      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultGroups()")
      @interface CanConsult {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canCreateGroups()")
      @interface CanCreate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canChangeGroups()")
      @interface CanUpdate {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canDeleteGroups()")
      @interface CanDelete {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canUpdatePermissions()")
      @interface CanUpdatePermissions {}

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canUpdateUsers()")
      @interface CanUpdateUsers {}
    }

  }

  @interface Register {
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