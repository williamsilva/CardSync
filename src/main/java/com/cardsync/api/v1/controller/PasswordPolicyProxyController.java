package com.cardsync.api.v1.controller;

import com.cardsync.bff.service.BffApiClient;
import com.cardsync.core.config.NimbusAuthClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy público (sem sessão/token) para a política de senha, que agora vive no NimbusAuth.
 * O SPA continua chamando /api/password/policy e /api/password/policy/check normalmente.
 */
@RestController
@RequiredArgsConstructor
public class PasswordPolicyProxyController {

  private final BffApiClient api;
  private final NimbusAuthClientProperties nimbusAuthProps;

  @RequestMapping("/api/password/policy")
  public ResponseEntity<byte[]> policy(HttpServletRequest req) {
    return api.forwardPublic(req, nimbusAuthProps.getBaseUrl(), "/api/password/policy", "/api/password/policy");
  }

  @RequestMapping("/api/password/policy/check")
  public ResponseEntity<byte[]> policyCheck(HttpServletRequest req) {
    return api.forwardPublic(req, nimbusAuthProps.getBaseUrl(), "/api/password/policy/check", "/api/password/policy/check");
  }
}
