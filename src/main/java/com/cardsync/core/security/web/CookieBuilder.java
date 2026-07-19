package com.cardsync.core.security.web;

public final class CookieBuilder {
  private CookieBuilder() {}

  /**
   * @param includeDomain precisa bater com o escopo do cookie que está sendo limpo: o
   * XSRF-TOKEN é setado cross-subdomain (Domain=cardsync.com.br, pra o JS da SPA ler), mas o
   * SESSION é host-only (Spring Session não seta Domain nenhum, escopo só de api.cardsync.com.br).
   * Um Set-Cookie com Domain diferente do original não apaga o cookie - o navegador trata como
   * um cookie diferente, e o cookie de sessão inválido continua sendo enviado pra sempre.
   */
  public static String clearCookie(String name, CookieProps props, boolean httpOnly, boolean includeDomain) {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append("=; Path=/; Max-Age=0");
    if (httpOnly) sb.append("; HttpOnly");
    if (props.isSecure()) sb.append("; Secure");
    if (includeDomain && props.getDomain() != null && !props.getDomain().isBlank()) {
      sb.append("; Domain=").append(props.getDomain());
    }
    sb.append("; SameSite=").append(props.getSameSite());
    return sb.toString();
  }
}
