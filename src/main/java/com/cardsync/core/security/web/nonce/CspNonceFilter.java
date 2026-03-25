package com.cardsync.core.security.web.nonce;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.filter.OncePerRequestFilter;

public class CspNonceFilter extends OncePerRequestFilter {

  public static final String ATTR_NONCE = "cspNonce";

  private static final SecureRandom RNG = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    // 16 bytes já é ótimo para nonce
    byte[] bytes = new byte[16];
    RNG.nextBytes(bytes);
    String nonce = B64.encodeToString(bytes);

    request.setAttribute(ATTR_NONCE, nonce);
    filterChain.doFilter(request, response);
  }
}
