package com.cardsync.core.security.resourceserver;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@RequiredArgsConstructor
public class ResourceServerJwtBeans {

  private final CardsyncSecurityProperties props;

  @Bean
  public JwtAuthenticationConverterAdapter jwtAuthenticationConverterAdapter() {
    return new JwtAuthenticationConverterAdapter(new GroupsPermissionsJwtAuthConverter());
  }

  @Bean
  public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
    // iss estrito + validações padrão + aud obrigatório
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(props.getIssuer());
    OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(props.getResourceServer().getAudience());
    return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
  }

  /**
   * Adapter simples para plugar nosso Converter<Jwt, AbstractAuthenticationToken>
   * no ponto esperado pelo Spring Security.
   */
  public static class JwtAuthenticationConverterAdapter extends JwtAuthenticationConverter {
    public JwtAuthenticationConverterAdapter(GroupsPermissionsJwtAuthConverter converter) {
      setJwtGrantedAuthoritiesConverter(jwt -> converter.convert(jwt).getAuthorities());
      setPrincipalClaimName("username"); // preferir username; fallback feito no converter
    }
  }
}
