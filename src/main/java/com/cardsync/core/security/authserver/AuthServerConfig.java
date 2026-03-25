package com.cardsync.core.security.authserver;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@RequiredArgsConstructor
public class AuthServerConfig {

  private final ResourceLoader resourceLoader;
  private final CardsyncSecurityProperties props;

  @Bean
  public RegisteredClientRepository registeredClientRepository(DataSource dataSource) {
    return new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource));
  }

  @Bean
  public JsonMapper authorizationServerJsonMapper() {
    BasicPolymorphicTypeValidator.Builder ptv =
      BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.cardsync")
        .allowIfSubType("org.springframework.security")
        .allowIfSubType("org.springframework.security.oauth2");

    return JsonMapper.builder()
      .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), ptv))
      .build();
  }

  @Bean
  public JdbcOAuth2AuthorizationService authorizationService(
    DataSource dataSource,
    RegisteredClientRepository registeredClientRepository,
    JsonMapper authorizationServerJsonMapper
  ) {
    var service = new JdbcOAuth2AuthorizationService(new JdbcTemplate(dataSource), registeredClientRepository);

    RowMapper<OAuth2Authorization> rowMapper =
      new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
        registeredClientRepository, authorizationServerJsonMapper
      );

    Function<OAuth2Authorization, List<SqlParameterValue>> paramsMapper =
      new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(
        authorizationServerJsonMapper
      );

    service.setAuthorizationRowMapper(rowMapper);
    service.setAuthorizationParametersMapper(paramsMapper);

    return service;
  }

  @Bean
  public OAuth2AuthorizationConsentService authorizationConsentService(DataSource dataSource,
    RegisteredClientRepository registeredClientRepository
  ) {
    return new JdbcOAuth2AuthorizationConsentService(new JdbcTemplate(dataSource), registeredClientRepository);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder()
      .issuer(props.getIssuer())
      .build();
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource() throws Exception {
    if (props.getAuthserver().isDevGenerateKey()) {
      RSAKey rsaKey = generateRsa();
      JWKSet jwkSet = new JWKSet(rsaKey);
      return (selector, context) -> selector.select(jwkSet);
    }

    RSAKey rsaKey = loadFromKeystore(props.getAuthserver().getKeystore());
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (selector, context) -> selector.select(jwkSet);
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  private static RSAKey generateRsa() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();
    PublicKey publicKey = kp.getPublic();
    PrivateKey privateKey = kp.getPrivate();

    return new RSAKey.Builder((RSAPublicKey) publicKey)
      .privateKey(privateKey)
      .keyID(UUID.randomUUID().toString())
      .build();
  }

  private RSAKey loadFromKeystore(AuthServerProperties.Keystore ksProps) throws Exception {
    Resource res = resourceLoader.getResource(ksProps.getLocation());
    KeyStore ks = KeyStore.getInstance("JKS");
    try (var in = res.getInputStream()) {
      ks.load(in, ksProps.getPassword().toCharArray());
    }

    var key = ks.getKey(ksProps.getKeyAlias(), ksProps.getKeyPassword().toCharArray());
    if (!(key instanceof PrivateKey privateKey)) {
      throw new IllegalStateException("Keystore key não é PrivateKey para alias=" + ksProps.getKeyAlias());
    }

    var cert = ks.getCertificate(ksProps.getKeyAlias());
    if (cert == null) {
      throw new IllegalStateException("Certificado não encontrado no keystore para alias=" + ksProps.getKeyAlias());
    }

    PublicKey publicKey = cert.getPublicKey();
    if (!(publicKey instanceof RSAPublicKey rsaPublic)) {
      throw new IllegalStateException("PublicKey do keystore não é RSA para alias=" + ksProps.getKeyAlias());
    }

    return new RSAKey.Builder(rsaPublic)
      .privateKey(privateKey)
      .keyID(ksProps.getKeyAlias())
      .build();
  }
}