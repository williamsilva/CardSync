package com.cardsync.core.security.password;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Autentica usuário/senha normalmente e, se a senha estiver expirada,
 * lança CredentialsExpiredException SEM contar como falha (lockout).
 */
@RequiredArgsConstructor
public class PasswordExpiryAuthenticationProvider implements AuthenticationProvider {

  private final DaoAuthenticationProvider delegate;
  private final PasswordExpiryService passwordExpiryService;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    // 1) valida usuário/senha via DaoAuthenticationProvider
    Authentication result = delegate.authenticate(authentication);

    // 2) só chega aqui se a senha foi válida
    String username = authentication.getName();
    if (passwordExpiryService.isExpiredPassword(username)) {
      throw new CredentialsExpiredException("Senha expirada");
    }

    return result;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return delegate.supports(authentication);
  }
}