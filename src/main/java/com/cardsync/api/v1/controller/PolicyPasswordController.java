package com.cardsync.api.v1.controller;

import com.cardsync.core.security.password.PasswordExpiryService;
import com.cardsync.core.security.password.PasswordExpiryViewModel;
import com.cardsync.core.security.password.PasswordPolicyService;
import com.cardsync.core.security.password.dtos.PasswordCheckRequest;
import com.cardsync.core.security.password.dtos.PasswordRulesViewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/password")
public class PolicyPasswordController {

  private final PasswordPolicyService policyService;
  private final PasswordExpiryService expiryService;

  @GetMapping("/policy")
  public PasswordRulesViewModel policy() {
    return policyService.policyPending();
  }

  @PostMapping("/policy/check")
  public PasswordRulesViewModel check(@RequestBody PasswordCheckRequest req) {
    return policyService.check(req.password(), req.confirmPassword(), req.username());
  }

  /**
   * Status de expiração para o usuário autenticado (não vaza existência).
   */
  @GetMapping("/status")
  public PasswordExpiryViewModel status(Authentication auth) {
    String username = auth != null ? auth.getName() : null;
    if (username == null || username.isBlank()) {
      // se chegar aqui sem auth, é porque security liberou indevidamente
      return PasswordExpiryViewModel.expired(0L);
    }
    return expiryService.statusForUsername(username);
  }
}
