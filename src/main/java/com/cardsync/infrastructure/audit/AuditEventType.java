package com.cardsync.infrastructure.audit;

public enum AuditEventType {
  set_password_success,
  set_password_fail,

  reset_password_requested,
  reset_password_fail,

  change_password_success,
  change_password_fail,

  invite_resent
}
