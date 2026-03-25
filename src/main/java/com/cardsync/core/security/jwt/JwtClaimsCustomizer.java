package com.cardsync.core.security.jwt;

import com.cardsync.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

  private final UserRepository users;

  @Override
  public void customize(JwtEncodingContext context) {
    String tokenType = context.getTokenType().getValue();

    // Emite também no id_token para o BFF enxergar no login
    if (!"access_token".equals(tokenType) && !"id_token".equals(tokenType)) return;

    Authentication principal = context.getPrincipal();
    String username = principal.getName();

    var user = users.findByUserNameIgnoreCase(username).orElse(null);

    String userId = user != null ? user.getId().toString() : null;
    String name = user != null ? user.getName() : username;

    List<String> groups = new ArrayList<>();
    List<String> permissions = new ArrayList<>();

    for (GrantedAuthority ga : principal.getAuthorities()) {
      String a = ga.getAuthority();
      if (a == null) continue;

      if (a.startsWith("ROLE_")) groups.add(a.substring("ROLE_".length()));
      if (a.startsWith("PERM_")) permissions.add(a.substring("PERM_".length()));
    }

    var claims = context.getClaims();
    claims.claim("userId", userId);
    claims.claim("username", username);
    claims.claim("name", name);
    claims.claim("groups", groups);
    claims.claim("permissions", permissions);
  }
}
