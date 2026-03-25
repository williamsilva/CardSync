package com.cardsync.core.security.password;

import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

  private final Clock clock;
  private final UserRepository users;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

    OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);

    var user = users.findByUserNameIgnoreCase(username)
      .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

    if (!user.isEnabled()) {
      throw new DisabledException("Usuário desativado");
    }

    if (user.isBlocked(now)) {
      throw new LockedException("Usuário bloqueado até " + user.getBlockedUntil());
    }

    Set<SimpleGrantedAuthority> auths = buildAuthorities(user);

    return new CardSyncUserDetails(
      user.getId(),
      user.getName(),
      user.getUserName(),
      user.getPasswordHash(),
      auths
    );
  }

  private Set<SimpleGrantedAuthority> buildAuthorities(UserEntity user) {
    Set<SimpleGrantedAuthority> auths = new HashSet<>();

    user.getGroups().forEach(g -> {
      auths.add(new SimpleGrantedAuthority("ROLE_" + g.getName()));
      g.getPermissions().forEach(p ->
        auths.add(new SimpleGrantedAuthority("PERM_" + p.getName()))
      );
    });

    return auths;
  }
}