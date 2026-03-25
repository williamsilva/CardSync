package com.cardsync.web.controller;

import com.cardsync.core.security.password.PasswordService;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.core.security.password.ExpiredPasswordFlow;
import com.cardsync.core.security.password.ExpiredPasswordFlowStore;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PasswordPageController {

  // TTL curto pra evitar reuso / deep link
  private static final long EXPIRED_FLOW_TTL_MS = 2 * 60 * 1000; // 2 min

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final PasswordService passwordService;

  @GetMapping("/password/expired")
  public String expired( HttpSession session,
                         Model model,
                         @CookieValue(name = "CS_LAST_USERNAME", required = false) String lastUsername) {

    ExpiredPasswordFlow flow = ExpiredPasswordFlowStore.consume(session);

    if (flow == null) {
      return "redirect:/login";
    }

    long now = System.currentTimeMillis();
    if (flow.isExpired(now, EXPIRED_FLOW_TTL_MS)) {
      return "redirect:/login";
    }

    // username não aparece na tela (hidden input)
    String username = (flow.username() != null && !flow.username().isBlank())
      ? flow.username()
      : (lastUsername != null && !lastUsername.isBlank() ? lastUsername : "");

    model.addAttribute("username", username);
    model.addAttribute("pageId", "expired-password");
    return "auth/expired-password";
  }

  @GetMapping("/password/change")
  public String change() {
    return "auth/change-password";
  }

  @PostMapping("/password/change")
  public String changeSubmit(
    @RequestParam String currentPassword,
    @RequestParam String newPassword,
    @RequestParam(required = false) String confirmPassword,
    Principal principal
  ) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      return "redirect:/login";
    }

    var user = users.findByUserNameIgnoreCase(principal.getName()).orElse(null);
    if (user == null) return "redirect:/password/change?error=1";

    if (!encoder.matches(currentPassword, user.getPasswordHash())) {
      return "redirect:/password/change?error=1";
    }

    try {
      passwordService.changePassword(user, newPassword, confirmPassword != null ? confirmPassword : newPassword);
      users.save(user);
      return "redirect:/password/change?ok=1";
    } catch (IllegalArgumentException e) {
      return "redirect:/password/change?error=1";
    }
  }

  @PostMapping("/password/expired")
  public String expiredSubmit(
    @RequestParam String username,
    @RequestParam String newPassword,
    @RequestParam(required = false) String confirmPassword,
    HttpServletRequest req
  ) {
    var user = users.findByUserNameIgnoreCase(username).orElse(null);
    if (user == null) return "redirect:/password/expired?error=1";

    try {
      passwordService.changePassword(user, newPassword, confirmPassword != null ? confirmPassword : newPassword);
      users.save(user);
      return "redirect:/password/expired?ok=1";
    } catch (IllegalArgumentException e) {
      return "redirect:/password/expired?error=1";
    }
  }
}
