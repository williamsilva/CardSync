package com.cardsync.core.security.password;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CardSyncUserDetails implements UserDetails {

  @Getter
  private final UUID id;
  @Getter
  private final String name;
  private final String username;
  private final String password;
  private final Set<? extends GrantedAuthority> authorities;

  public CardSyncUserDetails(
    UUID id,
    String name,
    String username,
    String password,
    Set<? extends GrantedAuthority> authorities
  ) {
    this.id = id;
    this.name = name;
    this.username = username;
    this.password = password;
    this.authorities = authorities;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return true; }
}