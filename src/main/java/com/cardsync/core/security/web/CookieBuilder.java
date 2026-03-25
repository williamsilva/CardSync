package com.cardsync.core.security.web;

public final class CookieBuilder {
  private CookieBuilder() {}

  public static String clearCookie(String name, CookieProps props, boolean httpOnly) {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append("=; Path=/; Max-Age=0");
    if (httpOnly) sb.append("; HttpOnly");
    if (props.isSecure()) sb.append("; Secure");
    if (props.getDomain() != null && !props.getDomain().isBlank()) sb.append("; Domain=").append(props.getDomain());
    sb.append("; SameSite=").append(props.getSameSite());
    return sb.toString();
  }
}
