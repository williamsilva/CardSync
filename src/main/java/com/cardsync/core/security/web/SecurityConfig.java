package com.cardsync.core.security.web;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.password.LoginFailureHandler;
import com.cardsync.core.security.password.LoginSuccessHandler;
import com.cardsync.core.security.password.PasswordExpiryAuthenticationProvider;
import com.cardsync.core.security.password.PasswordExpiryService;
import com.cardsync.core.security.resourceserver.ResourceServerJwtBeans;
import com.cardsync.core.security.web.headers.ConditionalHstsHeaderWriter;
import com.cardsync.core.security.web.headers.CspHeaderWriter;
import com.cardsync.core.security.web.nonce.CspNonceFilter;
import com.cardsync.core.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
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
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.LinkedHashSet;

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
    // IMPORTANTE: o CspHeaderWriter vai substituir {nonce} pelo nonce real.
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

  @Bean
  public AuthenticationEntryPoint spa401EntryPoint(
    ObjectMapper objectMapper, Clock clock, MessageSource messages) {
    return new Spa401EntryPoint(objectMapper, clock, messages);
  }

  @Bean
  public AccessDeniedHandler spa403AccessDeniedHandler(
    ObjectMapper objectMapper, Clock clock, MessageSource messages) {
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
      // fallback (NÃO SPA): manda para /bff/login
      .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/bff/login"))
      .build();
  }

  @Bean
  public AuthenticationProvider authenticationProvider(
    UserDetailsService userDetailsService,
    PasswordExpiryService passwordExpiryService,
    PasswordEncoder passwordEncoder
  ) {
    DaoAuthenticationProvider dao = new DaoAuthenticationProvider(userDetailsService);
    dao.setPasswordEncoder(passwordEncoder);

    return new PasswordExpiryAuthenticationProvider(dao, passwordExpiryService);
  }

  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser > bffOidcUserService() {

    var delegate = new OidcUserService();

    return (req) -> {
      var oidc = delegate.loadUser(req);

      var out = new LinkedHashSet<GrantedAuthority>(oidc.getAuthorities()); // mantém OIDC_USER + SCOPE_*

      var idToken = oidc.getIdToken();

      var groups = idToken.getClaimAsStringList("groups");
      if (groups != null) {
        for (String g : groups) {
          out.add(new SimpleGrantedAuthority("ROLE_" + g));
        }
      }

      var perms = idToken.getClaimAsStringList("permissions");
      if (perms != null) {
        for (String p : perms) {
          out.add(new SimpleGrantedAuthority("PERM_" + p));
        }
      }

      // ✅ deixa auth.getName() virar o email
      return new DefaultOidcUser(
        out,
        oidc.getIdToken(),
        oidc.getUserInfo(),
        "username"
      );
    };
  }

  // ---------------------------
  // 0) AUTHORIZATION SERVER CHAIN
  // ---------------------------
  @Bean
  @Order(5)
  public SecurityFilterChain authServerChain(HttpSecurity http, AuthenticationEntryPoint spa401EntryPoint,
                                             AccessDeniedHandler spa403AccessDeniedHandler) throws Exception {

    OAuth2AuthorizationServerConfigurer authServer = new OAuth2AuthorizationServerConfigurer();
    authServer.oidc(Customizer.withDefaults());

    RequestMatcher endpointsMatcher = authServer.getEndpointsMatcher();

    http.securityMatcher(endpointsMatcher);

    http.authorizeHttpRequests(a -> a.anyRequest().authenticated());

    // Quando não autenticado, manda pra UI /login (WEB chain)
    var spaMatcher = new SpaRequestMatcher();
    var nonSpaMatcher = new NegatedRequestMatcher(spaMatcher);

    http.exceptionHandling(ex -> ex
      .defaultAuthenticationEntryPointFor(spa401EntryPoint, spaMatcher)
      .defaultAccessDeniedHandlerFor(spa403AccessDeniedHandler, spaMatcher)

      // fallback APENAS para requests que NÃO são SPA
      .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), nonSpaMatcher)
    );

    http.requestCache(RequestCacheConfigurer::disable);

    // CSRF não se aplica nos endpoints do Authorization Server
    http.csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher));

    http.cors(Customizer.withDefaults());

    // Spring Security 7: use with(...) em vez de apply(...)
    http.with(authServer, Customizer.withDefaults());

    http.addFilterBefore(new CspNonceFilter(), HeaderWriterFilter.class);
    http.addFilterBefore(new CorrelationIdFilter(), HeaderWriterFilter.class);

    applySecurityHeaders(http, true);

    return http.build();
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

      // password policy (fonte da verdade)
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
    AccessDeniedHandler spa403AccessDeniedHandler
  ) throws Exception {

    http.securityMatcher("/bff/**", "/oauth2/authorization/**", "/login/oauth2/**");

    // IMPORTANTÍSSIMO pro SPA: não salvar request, não tentar "voltar" em POST
    http.requestCache(rc -> rc.disable());

    http.sessionManagement(sm -> sm
      .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      .sessionFixation(sf -> sf.migrateSession())
      .invalidSessionStrategy((req, res) -> {
        // se for SPA => 401 JSON; senão => redirect /bff/login
        if (new SpaRequestMatcher().matches(req)) {
          spa401EntryPoint.commence(req, res, null);
        } else {
          res.sendRedirect("/login");
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

    // ✅ Aqui: UMA fonte de verdade
    http.exceptionHandling(ex -> ex
      .authenticationEntryPoint(bffAuthenticationEntryPoint)
      .accessDeniedHandler(spa403AccessDeniedHandler)
    );

    http.oauth2Login(o -> o
      .userInfoEndpoint(u -> u.oidcUserService(bffOidcUserService()))
      .successHandler(oauth2SpaSuccessHandler)
    );

    http.logout(AbstractHttpConfigurer::disable);
    http.formLogin(AbstractHttpConfigurer::disable);

    return http.build();
  }
  // ---------------------------
  // 3) WEB CHAIN (THYMELEAF) STATEFUL
  // ---------------------------
  @Bean
  @Order(30)
  public SecurityFilterChain webChain(
    HttpSecurity http,
    LoginSuccessHandler loginSuccessHandler,
    LoginFailureHandler loginFailureHandler,
    AuthenticationProvider authenticationProvider
  ) throws Exception {

    // Tudo que NÃO é /api/**, /bff/** e também não é callback/oauth2-client
    http.securityMatcher(request -> {
      String path = request.getRequestURI();
      return !path.startsWith("/api/")
        && !path.startsWith("/bff/")
        && !path.startsWith("/oauth2/")
        && !path.startsWith("/login/oauth2/");
    });

    http.sessionManagement(sm -> sm
      .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      .sessionFixation(sf -> sf.migrateSession())
      .maximumSessions(1)
      .maxSessionsPreventsLogin(false)
    );

    http.cors(Customizer.withDefaults());
    http.authenticationProvider(authenticationProvider);

    http.csrf(csrf -> csrf
      .csrfTokenRepository(csrfRepo())
      .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
      .ignoringRequestMatchers("/login/password/status"));

    http.authorizeHttpRequests(auth -> auth
      .requestMatchers(
        "/favicon.ico",
        "/login",
        "/error",
        "/success",
        "/forget-password",
        "/password/reset/**",
        "/password/set/**",
        "/password/expired",
        "/.well-known/**",
        "/assets/**", "/css/**", "/js/**", "/images/**", "/webjars/**",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info"
      ).permitAll()
      .requestMatchers(HttpMethod.POST, "/login/password/status").permitAll()
      .anyRequest().authenticated()
    );

    // ✅ AQUI é onde o /login realmente é processado.
    http.formLogin(fl -> fl
      .loginPage("/login")
      .loginProcessingUrl("/login")
      .successHandler(loginSuccessHandler)
      .failureHandler(loginFailureHandler)
      .permitAll()
    );

    // ⚠️ Importantíssimo: evitar página default OAuth2
    http.oauth2Login(AbstractHttpConfigurer::disable);

    http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
    http.addFilterBefore(new CspNonceFilter(), HeaderWriterFilter.class);
    http.addFilterBefore(new CorrelationIdFilter(), HeaderWriterFilter.class);

    applySecurityHeaders(http, true);

    return http.build();
  }

  // ---------------------------
  // CSRF repo (double submit)
  // ---------------------------
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
