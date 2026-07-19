package com.cardsync.core.security.web;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.resourceserver.ResourceServerJwtBeans;
import com.cardsync.core.security.web.headers.ConditionalHstsHeaderWriter;
import com.cardsync.core.security.web.headers.CspHeaderWriter;
import com.cardsync.core.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.LinkedHashSet;

/**
 * Segurança do Cardsync após o split com o NimbusAuth: não há mais Authorization Server
 * nem login local aqui - só o BFF (sessão/cookies/CSRF, oauth2Login contra o NimbusAuth)
 * e o Resource Server (/api/**, valida JWT emitido pelo NimbusAuth via JWKS remoto).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements EnvironmentAware {

  private Environment env;
  private final CardsyncSecurityProperties props;

  @Override
  public void setEnvironment(Environment environment) {
    this.env = environment;
  }

  private boolean isProd() {
    return env != null && env.matchesProfiles("prod");
  }

  private String buildPageCsp() {
    return String.join(
      " ",
      "default-src 'self';",
      "object-src 'none';",
      "base-uri 'self';",
      "frame-ancestors 'none';",
      "form-action 'self';",
      "upgrade-insecure-requests;",
      "script-src 'self' 'nonce-{nonce}';",
      "style-src 'self';",
      "img-src 'self' data:;",
      "connect-src 'self';"
    );
  }

  private String buildApiCsp() {
    return "default-src 'none'; frame-ancestors 'none';";
  }

  /**
   * JWKS remoto do NimbusAuth: os tokens são emitidos lá, não pelo Cardsync.
   * Usa withJwkSetUri (carregamento preguiçoso, só na primeira validação de token) em vez de
   * withIssuerLocation (faria discovery via rede aqui mesmo, no boot do Cardsync, exigindo que
   * o NimbusAuth já esteja no ar nesse instante).
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri(props.getIssuer() + "/oauth2/jwks").build();
  }

  @Bean
  public AuthenticationEntryPoint spa401EntryPoint(
    ObjectMapper objectMapper, Clock clock, org.springframework.context.MessageSource messages) {
    return new Spa401EntryPoint(objectMapper, clock, messages);
  }

  @Bean
  public AccessDeniedHandler spa403AccessDeniedHandler(
    ObjectMapper objectMapper, Clock clock, org.springframework.context.MessageSource messages) {
    return new Spa403AccessDeniedHandler(objectMapper, clock, messages);
  }

  @Bean
  public RequestCache requestCache() {
    return new HttpSessionRequestCache();
  }

  @Bean
  public AuthenticationSuccessHandler oauth2SpaSuccessHandler(RequestCache requestCache, SpaRedirectSupport spaRedirectSupport) {
    return new com.cardsync.bff.security.OAuth2SpaSuccessHandler(props, requestCache, spaRedirectSupport);
  }

  @Bean
  public AuthenticationEntryPoint bffAuthenticationEntryPoint(AuthenticationEntryPoint spa401EntryPoint) {
    return DelegatingAuthenticationEntryPoint.builder()
      .addEntryPointFor(spa401EntryPoint, new SpaRequestMatcher())
      .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/bff/login"))
      .build();
  }

  /**
   * PKCE no fluxo authorization_code — mesmo sendo um client confidential (client_secret_basic),
   * o BFF roda num processo separado da Authorization Server (Railway), então o code_verifier
   * ainda protege contra um authorization code interceptado em trânsito. Combina com
   * requireProofKey(true) no RegisteredClient do lado do NimbusAuth.
   */
  @Bean
  public OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
    ClientRegistrationRepository clientRegistrationRepository) {

    var resolver = new DefaultOAuth2AuthorizationRequestResolver(
      clientRegistrationRepository, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
  }

  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser> bffOidcUserService() {

    var delegate = new OidcUserService();

    return (req) -> {
      var oidc = delegate.loadUser(req);

      var out = new LinkedHashSet<GrantedAuthority>(oidc.getAuthorities());

      // groups/permissions são claims de autorização — só vêm no access_token (padrão OIDC),
      // então lemos do /userinfo (chamado pelo OidcUserService com o access_token como bearer),
      // não do id_token (que agora carrega só identidade).
      var userInfo = oidc.getUserInfo();

      var groups = userInfo != null ? userInfo.getClaimAsStringList("groups") : null;
      if (groups != null) {
        for (String g : groups) {
          out.add(new SimpleGrantedAuthority("ROLE_" + g));
        }
      }

      var perms = userInfo != null ? userInfo.getClaimAsStringList("permissions") : null;
      if (perms != null) {
        for (String p : perms) {
          out.add(new SimpleGrantedAuthority("PERM_" + p));
        }
      }

      return new DefaultOidcUser(
        out,
        oidc.getIdToken(),
        oidc.getUserInfo(),
        "username"
      );
    };
  }

  // ---------------------------
  // 1) API CHAIN (/api/**) STATELESS
  // ---------------------------
  @Bean
  @Order(10)
  public SecurityFilterChain apiChain(
    HttpSecurity http,
    JwtDecoder jwtDecoder,
    OAuth2TokenValidator<Jwt> jwtTokenValidator,
    ResourceServerJwtBeans.JwtAuthenticationConverterAdapter jwtAuthConverter
  ) throws Exception {

    http.securityMatcher("/api/**");

    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(Customizer.withDefaults());

    http.authorizeHttpRequests(auth -> auth
      .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
      .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
      // política de senha: proxy público para o NimbusAuth (ver PasswordPolicyProxyController)
      .requestMatchers(HttpMethod.GET, "/api/password/policy").permitAll()
      .requestMatchers(HttpMethod.POST, "/api/password/policy/check").permitAll()
      .anyRequest().authenticated()
    );

    http.addFilterBefore(new CorrelationIdFilter(), HeaderWriterFilter.class);

    applySecurityHeaders(http, false);

    if (props.getResourceServer().isEnabled()) {
      if (jwtDecoder instanceof NimbusJwtDecoder nimbus) {
        nimbus.setJwtValidator(jwtTokenValidator);
      }

      http.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt
          .decoder(jwtDecoder)
          .jwtAuthenticationConverter(jwtAuthConverter)
        )
      );
    }

    return http.build();
  }

  // ---------------------------
  // 2) BFF CHAIN (/bff/**) STATEFUL
  // ---------------------------
  @Bean
  @Order(20)
  public SecurityFilterChain bffChain(
    HttpSecurity http, AuthenticationSuccessHandler oauth2SpaSuccessHandler,
    AuthenticationEntryPoint bffAuthenticationEntryPoint, AuthenticationEntryPoint spa401EntryPoint,
    AccessDeniedHandler spa403AccessDeniedHandler, CookieProps cookieProps,
    OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver
  ) throws Exception {

    http.securityMatcher("/bff/**", "/oauth2/authorization/**", "/login/oauth2/**");

    http.requestCache(rc -> rc.disable());

    http.sessionManagement(sm -> sm
      .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      .sessionFixation(sf -> sf.migrateSession())
      .invalidSessionStrategy((req, res) -> {
        if (new SpaRequestMatcher().matches(req)) {
          spa401EntryPoint.commence(req, res, null);
        } else {
          // Cookie de sessão antigo/inválido (ex: sessão expirou ou a tabela de sessão foi
          // recriada) - precisa limpar o cookie antes de redirecionar, senão o navegador
          // reenvia o mesmo cookie inválido e cai num loop de redirecionamento em "/bff/login".
          // O nome do cookie é "SESSION" (default do Spring Session com store-type: jdbc),
          // não "JSESSIONID" (esse é o nome do cookie de sessão nativo do servlet container,
          // que não é usado aqui) - limpar o nome errado deixava o cookie real intacto.
          res.addHeader("Set-Cookie", CookieBuilder.clearCookie("SESSION", cookieProps, true));
          res.sendRedirect("/bff/login");
        }
      })
    );

    http.cors(Customizer.withDefaults());

    http.csrf(csrf -> csrf
      .csrfTokenRepository(csrfRepo())
      .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
      .ignoringRequestMatchers("/bff/login/prepare")
    );
    http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

    http.authorizeHttpRequests(auth -> auth
      .requestMatchers(HttpMethod.GET, "/bff/login").permitAll()
      .requestMatchers(HttpMethod.GET, "/bff/csrf").permitAll()
      .requestMatchers(HttpMethod.POST, "/bff/login/prepare").permitAll()
      .requestMatchers("/oauth2/authorization/**", "/login/oauth2/**").permitAll()
      .requestMatchers(HttpMethod.POST, "/bff/logout").authenticated()
      .requestMatchers(HttpMethod.GET, "/bff/me").authenticated()
      .anyRequest().authenticated()
    );

    http.exceptionHandling(ex -> ex
      .authenticationEntryPoint(bffAuthenticationEntryPoint)
      .accessDeniedHandler(spa403AccessDeniedHandler)
    );

    http.oauth2Login(o -> o
      .authorizationEndpoint(a -> a.authorizationRequestResolver(pkceAuthorizationRequestResolver))
      .userInfoEndpoint(u -> u.oidcUserService(bffOidcUserService()))
      .successHandler(oauth2SpaSuccessHandler)
    );

    http.logout(AbstractHttpConfigurer::disable);
    http.formLogin(AbstractHttpConfigurer::disable);

    return http.build();
  }

  private CookieCsrfTokenRepository csrfRepo() {
    CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repo.setCookieName("XSRF-TOKEN");
    repo.setHeaderName("X-XSRF-TOKEN");
    repo.setCookiePath("/");

    repo.setCookieCustomizer(cookie -> {
      String domain = props.getCookies().getDomain();
      if (domain != null && !domain.isBlank()) {
        cookie.domain(domain);
      }
      cookie.secure(props.getCookies().isSecure());
      cookie.sameSite(props.getCookies().getSameSite());
    });

    return repo;
  }

  private void applySecurityHeaders(HttpSecurity http, boolean isPage) throws Exception {

    HeaderWriter cspWriter =
      isPage ? new CspHeaderWriter(buildPageCsp())
        : new StaticHeadersWriter("Content-Security-Policy", buildApiCsp());

    HeaderWriter hstsWriter = new ConditionalHstsHeaderWriter(
      31536000,
      true,
      true,
      req -> {
        String host = req.getServerName();
        boolean isLocal = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);

        String xfProto = req.getHeader("X-Forwarded-Proto");
        boolean forwardedHttps = "https".equalsIgnoreCase(xfProto);

        return !isLocal && isProd() && (req.isSecure() || forwardedHttps);
      }
    );

    http.headers(headers -> headers
      .addHeaderWriter(cspWriter)
      .addHeaderWriter(hstsWriter)
      .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
      .contentTypeOptions(Customizer.withDefaults())
      .frameOptions(frame -> frame.deny())
      .addHeaderWriter(new StaticHeadersWriter(
        "Permissions-Policy",
        "geolocation=(), microphone=(), camera=(), payment=(), usb=(), interest-cohort=()"
      ))
    );
  }

}
