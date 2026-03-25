package com.cardsync.web.controller;

import com.cardsync.core.security.password.PasswordService;
import com.cardsync.domain.model.enums.StatusUserEnum;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.domain.service.PasswordTokenService;
import com.cardsync.infrastructure.audit.AuditEventType;
import com.cardsync.infrastructure.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class PasswordFlowController {

  private final AuditService audit;
  private final UserRepository users;
  private final PasswordTokenService tokens;
  private final PasswordService passwordService;

  @GetMapping("/forget-password")
  public String forgetPage() {
    return "auth/forget-password";
  }

  @PostMapping("/forget-password")
  public String forgetSubmit(@RequestParam String username, HttpServletRequest req) {
    // baseline: resposta neutra (não revela se existe)
    String baseUrl = baseUrl(req);
    tokens.createResetToken(username, baseUrl);

    audit.log(
      AuditEventType.reset_password_requested, null,
      req, "{\"username\":\"" + safeJson(username) + "\"}");

    return "redirect:/success?m=forgetPasswordSent";
  }

  @GetMapping("/password/reset/expired/{userId}")
  public String resetExpiredPage(@PathVariable UUID userId, Model model) {
    model.addAttribute("userId", userId);
    return "auth/reset-expired";
  }

  @GetMapping("/password/reset/{userId}/{token}")
  public String resetPage(
    @PathVariable UUID userId,
    @PathVariable String token,
    Model model,
    HttpServletRequest req
  ) {
    var rt = tokens.validateReset(userId, token);

    boolean valid = rt != null;
    if (!valid) {
      audit.log(
        AuditEventType.reset_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"invalid_or_expired\"}");
      return "redirect:/password/reset/expired/" + userId;
    }

    model.addAttribute("valid", true);
    model.addAttribute("userId", userId);
    model.addAttribute("token", token);

    // Para política de senha (não exibir na UI, só usar no checklist)
    users.findById(userId).ifPresent(u -> model.addAttribute("username", u.getUserName()));

    return "auth/reset-password";
  }

  @PostMapping("/password/reset/{userId}/{token}")
  public String resetSubmit(
    @PathVariable UUID userId,
    @PathVariable String token,
    @RequestParam String newPassword,
    HttpServletRequest req,
    Model model
  ) {
    var rt = tokens.validateReset(userId, token);
    if (rt == null) {
      audit.log(
        AuditEventType.reset_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"invalid_or_expired\"}");

      return "redirect:/password/reset/expired/" + userId;
    }

    try {
      var user = users.findById(userId).orElseThrow();
      passwordService.changePassword(user, newPassword);
      user.setStatusEnum(StatusUserEnum.ACTIVE);
      users.save(user);

      tokens.markUsed(rt);

      audit.log(AuditEventType.set_password_success, null, req, "{\"userId\":\"" + userId + "\"}");

      return "redirect:/success";

    } catch (IllegalArgumentException e) {
      model.addAttribute("valid", true);
      model.addAttribute("userId", userId);
      model.addAttribute("token", token);
      users.findById(userId).ifPresent(u -> model.addAttribute("username", u.getUserName()));

      audit.log(
        AuditEventType.reset_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"" + safeJson(e.getMessage()) + "\"}");
      throw e;
    }
  }

  /**
   * Página de token expirado para convite de primeira senha.
   * Permite ao usuário solicitar novo link sem expor detalhes (e-mail vai para o userName).
   */
  @GetMapping("/password/set/expired/{userId}")
  public String inviteExpiredPage(@PathVariable UUID userId, Model model) {
    model.addAttribute("userId", userId);
    return "auth/invite-expired";
  }

  @PostMapping("/password/set/expired/{userId}/resend")
  public String inviteExpiredResend(@PathVariable UUID userId, HttpServletRequest req) {
    String baseUrl = baseUrl(req);
    // Sempre responde sucesso (evita enumeração), mas aqui userId vem do link.
    try {
      tokens.createInviteToken(userId, baseUrl);
    } catch (Exception ignored) {
      // resposta neutra
    }

    audit.log(AuditEventType.invite_resent, null, req, "{\"userId\":\"" + userId + "\"}");

    return "redirect:/success?m=inviteResent";
  }

  @GetMapping("/password/set/{userId}/{token}")
  public String setPage(@PathVariable UUID userId, @PathVariable String token, Model model, HttpServletRequest req) {
    var it = tokens.validateInvite(userId, token);

    boolean valid = it != null;
    if (!valid) {
      audit.log(
        AuditEventType.set_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"invalid_or_expired\"}");
      return "redirect:/password/set/expired/" + userId;
    }

    model.addAttribute("valid", true);
    model.addAttribute("userId", userId);
    model.addAttribute("token", token);

    // Para política de senha (não exibir na UI, só usar no checklist)
    users.findById(userId).ifPresent(u -> model.addAttribute("username", u.getUserName()));

    return "auth/set-password";
  }

  @PostMapping("/password/set/{userId}/{token}")
  public String setSubmit(
    @PathVariable UUID userId,
    @PathVariable String token,
    @RequestParam String newPassword,
    HttpServletRequest req,
    Model model
  ) {
    var it = tokens.validateInvite(userId, token);
    if (it == null) {
      audit.log(
        AuditEventType.set_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"invalid_or_expired\"}");

      return "redirect:/password/set/expired/" + userId;
    }

    try {
      var user = users.findById(userId).orElseThrow();
      passwordService.changePassword(user, newPassword);
      user.setStatusEnum(StatusUserEnum.ACTIVE);
      users.save(user);

      tokens.markUsed(it);

      audit.log(AuditEventType.set_password_success, null, req, "{\"userId\":\"" + userId + "\"}");

      return "redirect:/success";

    } catch (IllegalArgumentException e) {
      model.addAttribute("valid", true);
      model.addAttribute("userId", userId);
      model.addAttribute("token", token);
      users.findById(userId).ifPresent(u -> model.addAttribute("username", u.getUserName()));

      audit.log(
        AuditEventType.set_password_fail, null,
        req, "{\"userId\":\"" + userId + "\",\"reason\":\"" + safeJson(e.getMessage()) + "\"}");
      throw e;
    }
  }

  private static String baseUrl(HttpServletRequest req) {
    String scheme = req.getHeader("X-Forwarded-Proto");
    if (scheme == null || scheme.isBlank()) scheme = req.getScheme();

    String host = req.getHeader("X-Forwarded-Host");
    if (host == null || host.isBlank()) host = req.getServerName();

    String port = req.getHeader("X-Forwarded-Port");
    int serverPort = req.getServerPort();

    boolean hostHasPort = host.contains(":");

    String ctx = req.getContextPath();
    if (ctx == null) ctx = "";

    StringBuilder sb = new StringBuilder();
    sb.append(scheme).append("://").append(host);

    if (!hostHasPort) {
      String p = (port != null && !port.isBlank()) ? port : String.valueOf(serverPort);
      boolean defaultPort = ("http".equalsIgnoreCase(scheme) && "80".equals(p))
        || ("https".equalsIgnoreCase(scheme) && "443".equals(p));
      if (!defaultPort) {
        sb.append(":").append(p);
      }
    }

    if (!ctx.isBlank()) sb.append(ctx);
    return sb.toString();
  }

  private static String safeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}