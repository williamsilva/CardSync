package com.cardsync.core.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * com.nimbussystems.commons.legacy (NimbusCommonsLegacy) vive fora da árvore com.cardsync, então
 * o component-scan default do Spring Boot (que só cobre o pacote da classe @SpringBootApplication
 * e sub-pacotes) não acha os @Configuration/@Component de lá (ex.: ClockConfig, ModelMapperConfig,
 * CsrfCookieFilter) sozinho - precisa listar o pacote explicitamente.
 *
 * <p>Isolado numa classe @Configuration própria (em vez de direto em CardSyncApplication) de
 * propósito, mesmo padrão do CommonsIntegrationConfig do NimbusFlowServer/NimbusNovaxServer (ver
 * NimbusCommonsServer): evita o mesmo problema achado lá com @EntityScan/@EnableJpaRepositories
 * direto na classe @SpringBootApplication vazando pra dentro de testes de fatia. Sem
 * @EntityScan/@EnableJpaRepositories aqui de propósito - nimbus-commons-legacy não tem nenhuma
 * @Entity/@Repository (só DTOs, config, filtros de segurança e utilitários).
 */
@Configuration
@ComponentScan(basePackages = "com.nimbussystems.commons.legacy")
public class CommonsIntegrationConfig {}
