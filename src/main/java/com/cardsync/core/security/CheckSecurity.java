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

  @interface Audit {

    @interface Mail {

      @Target(METHOD)
      @Retention(RUNTIME)
      @PreAuthorize("@csSecurity.canConsultAuditMail()")
      @interface CanConsultAuditMail{ }
    }
  }
}