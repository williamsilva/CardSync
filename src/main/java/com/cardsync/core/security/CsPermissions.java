package com.cardsync.core.security;

public abstract class CsPermissions {

  public static final String ROLE_SUPPORT = "ROLE_SUPPORT";

  /* Permissions for Users */
  protected static final String PERM_USERS_CHANGE = "PERM_USERS_CHANGE";
  protected static final String PERM_USERS_CREATE = "PERM_USERS_CREATE";
  protected static final String PERM_USERS_DELETE = "PERM_USERS_DELETE";
  protected static final String PERM_USERS_CONSULT = "PERM_USERS_CONSULT";
  protected static final String PERM_USERS_RESEND_INVITE = "PERM_USERS_RESEND_INVITE";
  protected static final String PERM_USERS_CHANGE_PASSWORD = "PERM_USERS_CHANGE_PASSWORD";
  protected static final String PERM_USERS_ACTIVE_OR_INACTIVE = "PERM_USERS_ACTIVE_OR_INACTIVE";

  /* Permission for Groups */
  protected static final String PERM_GROUPS_CHANGE = "PERM_GROUPS_CHANGE";
  protected static final String PERM_GROUPS_CREATE = "PERM_GROUPS_CREATE";
  protected static final String PERM_GROUPS_DELETE = "PERM_GROUPS_DELETE";
  protected static final String PERM_GROUPS_CONSULT = "PERM_GROUPS_CONSULT";
  protected static final String PERM_GROUPS_MANAGEMENT_USER = "PERM_GROUPS_MANAGEMENT_USER";
  protected static final String PERM_GROUPS_MANAGEMENT_PERMISSION = "PERM_GROUPS_MANAGEMENT_PERMISSION";

  /* Permission for Companies */
  protected static final String PERM_COMPANIES_CHANGE = "PERM_COMPANIES_CHANGE";
  protected static final String PERM_COMPANIES_CREATE = "PERM_COMPANIES_CREATE";
  protected static final String PERM_COMPANIES_DELETE = "PERM_COMPANIES_DELETE";
  protected static final String PERM_COMPANIES_CONSULT = "PERM_COMPANIES_CONSULT";
  protected static final String PERM_COMPANIES_ACTIVE_OR_INACTIVE = "PERM_COMPANIES_ACTIVE_OR_INACTIVE";

  /* Permission for Acquirers */
  protected static final String PERM_ACQUIRER_CHANGE = "PERM_ACQUIRER_CHANGE";
  protected static final String PERM_ACQUIRER_CREATE = "PERM_ACQUIRER_CREATE";
  protected static final String PERM_ACQUIRER_DELETE = "PERM_ACQUIRER_DELETE";
  protected static final String PERM_ACQUIRER_CONSULT = "PERM_ACQUIRER_CONSULT";
  protected static final String PERM_ACQUIRER_ACTIVE_OR_INACTIVE = "PERM_ACQUIRER_ACTIVE_OR_INACTIVE";

  /* Permission for Establishments */
  protected static final String PERM_ESTABLISHMENT_CHANGE = "PERM_ESTABLISHMENT_CHANGE";
  protected static final String PERM_ESTABLISHMENT_CREATE = "PERM_ESTABLISHMENT_CREATE";
  protected static final String PERM_ESTABLISHMENT_DELETE = "PERM_ESTABLISHMENT_DELETE";
  protected static final String PERM_ESTABLISHMENT_CONSULT = "PERM_ESTABLISHMENT_CONSULT";
  protected static final String PERM_ESTABLISHMENT_ACTIVE_OR_INACTIVE = "PERM_ESTABLISHMENT_ACTIVE_OR_INACTIVE";

  /* Permission for Audit */
  protected static final String PERM_AUDIT_MAIL_CONSULT = "PERM_AUDIT_MAIL_CONSULT";
}
