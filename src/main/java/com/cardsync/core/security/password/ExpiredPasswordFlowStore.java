package com.cardsync.core.security.password;

import jakarta.servlet.http.HttpSession;

public final class ExpiredPasswordFlowStore {
  private ExpiredPasswordFlowStore() {}

  public static final String KEY = "CS_PASSWORD_EXPIRED_FLOW";

  public static void put(HttpSession session, ExpiredPasswordFlow flow) {
    if (session == null || flow == null) return;
    session.setAttribute(KEY, flow);
  }

  /** One-shot: lê e remove */
  public static ExpiredPasswordFlow consume(HttpSession session) {
    if (session == null) return null;
    Object obj = session.getAttribute(KEY);
    session.removeAttribute(KEY);
    return (obj instanceof ExpiredPasswordFlow f) ? f : null;
  }
}