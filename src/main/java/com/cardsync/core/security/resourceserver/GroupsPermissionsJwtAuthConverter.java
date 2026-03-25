package com.cardsync.core.security.resourceserver;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Mapeia:
 * - groups -> ROLE_*
 * - permissions -> PERM_*
 *
 * ❌ Não usa scope/scp
 */
public class GroupsPermissionsJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

    authorities.addAll(readStringList(jwt, "groups").stream()
      .map(this::toRole)
      .toList()
    );

    authorities.addAll(readStringList(jwt, "permissions").stream()
      .map(this::toPerm)
      .toList()
    );

    String name = jwt.getClaimAsString("username");
    if (name == null || name.isBlank()) {
      name = jwt.getSubject();
    }

    return new JwtAuthenticationToken(jwt, authorities, name);
  }

  private SimpleGrantedAuthority toRole(String group) {
    String g = (group == null ? "" : group.trim());
    if (g.isBlank()) return null;
    if (g.startsWith("ROLE_")) return new SimpleGrantedAuthority(g);
    return new SimpleGrantedAuthority("ROLE_" + g);
  }

  private SimpleGrantedAuthority toPerm(String perm) {
    String p = (perm == null ? "" : perm.trim());
    if (p.isBlank()) return null;
    if (p.startsWith("PERM_")) return new SimpleGrantedAuthority(p);
    return new SimpleGrantedAuthority("PERM_" + p);
  }

  private List<String> readStringList(Jwt jwt, String claim) {
    Object raw = jwt.getClaims().get(claim);
    if (raw instanceof Collection<?> c) {
      List<String> out = new ArrayList<>();
      for (Object o : c) {
        if (o != null) out.add(o.toString());
      }
      return out;
    }
    return List.of();
  }
}