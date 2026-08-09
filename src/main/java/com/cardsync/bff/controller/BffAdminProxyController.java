package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffApiClient;
import com.cardsync.bff.service.BffUserProvisioningService;
import com.cardsync.core.config.NimbusAuthClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
  private final BffUserProvisioningService userProvisioning;

  /**
   * Mapeamento exato (sem "/**") - o Spring prioriza este sobre proxyUsers() abaixo pra POST em
   * "/bff/v1/users", que precisa da lógica de "usuário já existe em outro app" (ver
   * BffUserProvisioningService); todo o resto de /bff/v1/users/** (GET, PUT/{id}, search, etc.)
   * continua no proxy genérico.
   */
  @PostMapping("/bff/v1/users")
  public ResponseEntity<byte[]> createOrGrantUserAccess(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.createOrGrantAccess(auth, req, res);
  }

  /**
   * Mapeamento exato (path variable, sem "/**") - o Spring prioriza este sobre proxyUsers()
   * abaixo pra PUT em "/bff/v1/users/{id}", que precisa preservar grupos de outros apps Nimbus
   * (ver BffUserProvisioningService); subpaths mais profundos (activate/deactivate/etc.) e o
   * restante de /bff/v1/users/** continuam no proxy genérico.
   */
  @PutMapping("/bff/v1/users/{id}")
  public ResponseEntity<byte[]> updateUserPreservingOtherAppGroups(
      @PathVariable String id, Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.updatePreservingOtherAppGroups(auth, req, res, id);
  }

  /**
   * Mapeamentos exatos - o NimbusAuth não filtra usuário por app_key (só grupo tem app_key), então
   * a listagem crua traria usuários de qualquer app Nimbus (ex.: NimbusFlow) sem nenhum grupo do
   * Cardsync. Escopadas em BffUserProvisioningService, mesmo recorte de AdminUserService no
   * NimbusFlowServer.
   */
  @GetMapping("/bff/v1/users/options")
  public ResponseEntity<byte[]> userOptionsScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.listOptionsScopedToCardsync(auth, req, res);
  }

  @GetMapping("/bff/v1/users/options-filter")
  public ResponseEntity<byte[]> userOptionsFilterScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.listOptionsScopedToCardsync(auth, req, res);
  }

  @PostMapping("/bff/v1/users/search")
  public ResponseEntity<byte[]> searchUsersScopedToCardsync(
      Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.searchScopedToCardsync(auth, req, res);
  }

  @GetMapping("/bff/v1/users/{id}")
  public ResponseEntity<byte[]> getUserByIdScopedToCardsync(
      @PathVariable String id, Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    return userProvisioning.getByIdScopedToCardsync(auth, req, res, id);
  }

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
