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

  /* Permission for Audit */
  protected static final String PERM_AUDIT_MAIL_CONSULT = "PERM_AUDIT_MAIL_CONSULT";
}
