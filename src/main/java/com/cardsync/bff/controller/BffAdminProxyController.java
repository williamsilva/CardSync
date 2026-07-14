package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffApiClient;
import com.cardsync.core.config.NimbusAuthClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy do BFF para as telas administrativas (Users/Groups/Permissions) e para a troca da
 * própria senha, que agora vivem no NimbusAuth. O SPA continua chamando exatamente as mesmas
 * URLs de sempre (/bff/v1/users, /bff/v1/groups, /bff/v1/permissions, /bff/v1/me/password/change);
 * aqui só trocamos o prefixo e anexamos o access token que fica guardado no servidor - o
 * token nunca chega ao browser.
 */
@RestController
@RequiredArgsConstructor
public class BffAdminProxyController {

  private final BffApiClient api;
  private final NimbusAuthClientProperties nimbusAuthProps;

  @RequestMapping("/bff/v1/users/**")
  public ResponseEntity<byte[]> proxyUsers(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return forward(auth, req, res, "/bff/v1/users", "/api/v1/users");
  }

  @RequestMapping("/bff/v1/groups/**")
  public ResponseEntity<byte[]> proxyGroups(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return forward(auth, req, res, "/bff/v1/groups", "/api/v1/groups");
  }

  @RequestMapping("/bff/v1/permissions/**")
  public ResponseEntity<byte[]> proxyPermissions(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return forward(auth, req, res, "/bff/v1/permissions", "/api/v1/permissions");
  }

  @RequestMapping("/bff/v1/me/password/change")
  public ResponseEntity<byte[]> proxyPasswordChange(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return forward(auth, req, res, "/bff/v1/me/password/change", "/api/v1/me/password/change");
  }

  private ResponseEntity<byte[]> forward(
    Authentication auth, HttpServletRequest req, HttpServletResponse res, String fromPrefix, String toPrefix
  ) {
    return api.forwardAuthenticated(auth, req, res, nimbusAuthProps.getBaseUrl(), fromPrefix, toPrefix);
  }
}
