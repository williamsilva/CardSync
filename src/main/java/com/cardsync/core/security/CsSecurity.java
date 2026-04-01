package com.cardsync.core.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CsSecurity extends CsDefaultSecurityMethod {

  public boolean hasAllReadScope() {
    return true;
  }

  public boolean hasAllWriteScope() {
    return true;
  }

  public boolean canConsultUsers() {
    return hasAllReadScope() && hasAuthority(PERM_USERS_CONSULT);
  }

  public boolean canCreateUsers() {
    return hasAllWriteScope() && hasAuthority(PERM_USERS_CREATE);
  }

  public boolean canChangeUsers(UUID userId) {
    return hasAllWriteScope()
      && (isAuthenticatedUserEqual(userId) || hasAuthority(PERM_USERS_CHANGE));
  }

  public boolean canChangePasswordUsers() {
    return hasAllWriteScope() && hasAuthority(PERM_USERS_CHANGE_PASSWORD);
  }

  public boolean canDeleteUsers() {
    return hasAllWriteScope() && hasAuthority(PERM_USERS_DELETE);
  }

  public boolean canActiveOrInactiveUser(UUID userId) {
    return hasAllWriteScope()
      && (!isAuthenticatedUserEqual(userId) && hasAuthority(PERM_USERS_ACTIVE_OR_INACTIVE));
  }

  public boolean canCanResendInvite() {
    return hasAllWriteScope() && hasAuthority(PERM_USERS_RESEND_INVITE);
  }

  public boolean canUpdatePermissions() {
    return hasAllWriteScope() && hasAuthority(PERM_GROUPS_MANAGEMENT_PERMISSION);
  }

  public boolean canUpdateUsers() {
    return hasAllWriteScope() && hasAuthority(PERM_GROUPS_MANAGEMENT_USER);
  }

  public boolean canConsultAuditMail() {
    return hasAllReadScope() && hasAuthority(PERM_AUDIT_MAIL_CONSULT);
  }

  /* Groups */
  public boolean canConsultGroups() {
    return hasAllReadScope() && hasAuthority(PERM_GROUPS_CONSULT);
  }

  public boolean canCreateGroups() {
    return hasAllWriteScope() && hasAuthority(PERM_GROUPS_CREATE);
  }

  public boolean canChangeGroups() {
    return hasAllWriteScope() && hasAuthority(PERM_GROUPS_CHANGE);
  }

  public boolean canDeleteGroups() {
    return hasAllWriteScope() && hasAuthority(PERM_GROUPS_DELETE);
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
    return hasAllWriteScope() && hasAuthority(PERM_USERS_ACTIVE_OR_INACTIVE);
  }
}
