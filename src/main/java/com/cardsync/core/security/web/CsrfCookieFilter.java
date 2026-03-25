package com.cardsync.core.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    // Ao tocar no token, o CookieCsrfTokenRepository gera o cookie se ainda não existir
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }

    filterChain.doFilter(request, response);
  }
}
