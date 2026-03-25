package com.cardsync.core.security.password;

import com.cardsync.core.security.lockout.LockoutService;
import com.cardsync.core.security.lockout.LockoutViewModel;
import com.cardsync.core.security.web.CookieProps;
import com.cardsync.core.security.web.CookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

  private final CookieProps cookieProps;
  private final LockoutService lockoutService;

  @Override
  public void onAuthenticationFailure(
    HttpServletRequest request,
    HttpServletResponse response,
    AuthenticationException exception
  ) throws IOException {

    String username = request.getParameter("username");

    LockoutViewModel lock;
    boolean locked = false;
    boolean expired = false;

    long until = 0L;
    Integer failed = 0;
    Integer remaining = 0;
    Integer nextThreshold = null;
    Long nextLockDurationSeconds = null;

    boolean isExpiredPassword = exception instanceof CredentialsExpiredException;

    if (username != null && !username.isBlank()) {

      request.getSession(true).setAttribute(LoginUiState.USERNAME_KEY, username);
      CookieWriter.setLastUsername(response, cookieProps, username);

      // ✅ Se a falha foi "senha expirada", NÃO registra failure no lockout
      if (isExpiredPassword) {
        expired = true;

        // ✅ Flow-gate: só permite abrir /password/expired se veio do login
        ExpiredPasswordFlowStore.put(
          request.getSession(true),
          new ExpiredPasswordFlow(username, System.currentTimeMillis())
        );

        locked = false;
        until = 0L;
        failed = 0;
        remaining = 0;
        nextThreshold = null;
        nextLockDurationSeconds = null;
      } else {
        // ✅ falha real (senha errada / usuário inexistente): conta lockout
        lock = lockoutService.onFailure(username);

        locked = lock.locked();
        until = lock.blockedUntilEpochMs();

        failed = lock.failedAttempts();
        remaining = lock.remainingAttempts();
        nextThreshold = lock.nextThreshold();
        nextLockDurationSeconds = lock.nextLockDurationSeconds();

        expired = false; // não checar expiração aqui (evita enumeração)
      }
    }

    var state = new LoginUiState(
      true,
      locked,
      expired,
      until,
      failed,
      remaining,
      nextThreshold,
      nextLockDurationSeconds
    );
    request.getSession(true).setAttribute(LoginUiState.SESSION_KEY, state);

    if (wantsJson(request)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      response.getWriter().write("""
        {
          "ok": false,
          "hasError": true,
          "locked": %s,
          "expiredPassword": %s,
          "blockedUntilEpochMs": %d,
          "failedAttempts": %d,
          "remainingAttempts": %d,
          "nextThreshold": %s,
          "nextLockDurationSeconds": %s
        }
      """.formatted(
        locked,
        expired,
        until,
        failed == null ? 0 : failed,
        remaining == null ? 0 : remaining,
        nextThreshold == null ? "null" : nextThreshold,
        nextLockDurationSeconds == null ? "null" : nextLockDurationSeconds
      ));
      return;
    }

    // ✅ HTML: se senha expirada, manda para a página de senha expirada (gated)
    if (isExpiredPassword) {
      response.sendRedirect(request.getContextPath() + "/password/expired");
      return;
    }

    response.sendRedirect(request.getContextPath() + "/login");
  }

  private boolean wantsJson(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    String xr = request.getHeader("X-Requested-With");
    return (accept != null && accept.contains("application/json"))
      || (xr != null && xr.equalsIgnoreCase("fetch"));
  }
}