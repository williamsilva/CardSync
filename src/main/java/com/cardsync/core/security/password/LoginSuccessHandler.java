package com.cardsync.core.security.password;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.lockout.LockoutService;
import com.cardsync.core.security.web.CookieProps;
import com.cardsync.core.security.web.CookieWriter;
import com.cardsync.core.security.web.SpaRedirectSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final LockoutService lockoutService;
  private final CardsyncSecurityProperties props;
  private final CookieProps cookieProps;
  private final SpaRedirectSupport spaRedirectSupport;

  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

  @Override
  public void onAuthenticationSuccess(
    HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication
  ) throws IOException, ServletException {

    String username = authentication != null ? authentication.getName() : null;
    if (username != null && !username.isBlank()) {
      lockoutService.registerSuccess(username);
      CookieWriter.setLastUsername(response, cookieProps, username);
    }

    var session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(LoginUiState.SESSION_KEY);
      session.removeAttribute(LoginUiState.USERNAME_KEY);
    }

    String preparedReturnTo = spaRedirectSupport.normalizeForRedirect(
      spaRedirectSupport.consumeReturnTo(session)
    );

    SavedRequest saved = requestCache.getRequest(request, response);

    String defaultTarget = props.getLogin().getDefaultTarget();
    String redirectTo = preparedReturnTo != null
      ? preparedReturnTo
      : (saved != null ? saved.getRedirectUrl() : defaultTarget);

    if (wantsJson(request)) {
      if (saved != null) {
        requestCache.removeRequest(request, response);
      }

      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("""
        {\"ok\":true,\"redirectTo\":\"%s\"}
        """.formatted(escapeJson(redirectTo)));
      return;
    }

    if (saved != null) {
      requestCache.removeRequest(request, response);
    }

    redirectStrategy.sendRedirect(request, response, redirectTo);
  }

  private boolean wantsJson(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    String xr = request.getHeader("X-Requested-With");
    return (accept != null && accept.contains("application/json"))
      || (xr != null && xr.equalsIgnoreCase("fetch"));
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
