package com.cardsync.web.controller;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.password.LoginUiState;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class LoginPageController {

  private static final String LAST_USERNAME_COOKIE = "CS_LAST_USERNAME";

  private final CardsyncSecurityProperties securityProps;

  @GetMapping("/login")
  public String login(HttpServletRequest request, Model model, Authentication auth) {

    // Usa SecurityContextHolder como fonte primária; auth (parâmetro Spring MVC) como fallback
    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current == null) current = auth;

    if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
      // Limpa qualquer estado de erro stale que possa ter sido migrado junto com a sessão
      var session = request.getSession(false);
      if (session != null) session.removeAttribute(LoginUiState.SESSION_KEY);

      // Redireciona direto para o SPA (evita o hop extra por "/")
      String spaUrl = securityProps.getWeb().getSpaBaseUrl();
      return "redirect:" + (spaUrl != null && !spaUrl.isBlank() ? spaUrl : "/");
    }

    var session = request.getSession(false);

    LoginUiState ui = LoginUiState.clear();
    String username = null;

    if (session != null) {
      Object s = session.getAttribute(LoginUiState.SESSION_KEY);
      if (s instanceof LoginUiState st) ui = st;

      Object u = session.getAttribute(LoginUiState.USERNAME_KEY);
      if (u instanceof String us) username = us;

      session.removeAttribute(LoginUiState.SESSION_KEY);
      session.removeAttribute(LoginUiState.USERNAME_KEY);
    }

    // 🍪 remember last username (fallback quando não veio via session)
    if (username == null || username.isBlank()) {
      Cookie[] cookies = request.getCookies();
      if (cookies != null) {
        for (Cookie c : cookies) {
          if (LAST_USERNAME_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
            username = URLDecoder.decode(c.getValue(), StandardCharsets.UTF_8);
            break;
          }
        }
      }
    }

    model.addAttribute("username", username);
    model.addAttribute("pageId", "login");

    model.addAttribute("locked", ui.locked());
    model.addAttribute("hasError", ui.hasError());
    model.addAttribute("nextThreshold", ui.nextThreshold());
    model.addAttribute("failedAttempts", ui.failedAttempts());
    model.addAttribute("expiredPassword", ui.expiredPassword());
    model.addAttribute("remainingAttempts", ui.remainingAttempts());
    model.addAttribute("nextLockDurationSeconds", ui.nextLockDurationSeconds());
    model.addAttribute("blockedUntilEpochMs", ui.blockedUntilEpochMs() != null ? ui.blockedUntilEpochMs() : 0L);

    // password warn/expire config (para JS/layout)
    int warnDays = securityProps.getPassword().getExpiration().getWarnDays();
    model.addAttribute("warnDays", warnDays);

    String defaultTarget = securityProps.getLogin().getDefaultTarget();
    model.addAttribute("defaultTarget", defaultTarget);

    return "auth/login";
  }

}
