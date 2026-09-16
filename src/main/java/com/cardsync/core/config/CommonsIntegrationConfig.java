package com.cardsync.core.config;

import com.nimbussystems.commons.notification.mail.BffEmailSettingsController;
import com.nimbussystems.commons.notification.mail.InternalEmailSettingsController;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * com.nimbussystems.commons.* (NimbusCommonsLegacy + NimbusCommonsServer) vive fora da árvore
 * com.cardsync, então o component-scan default do Spring Boot (que só cobre o pacote da classe
 * @SpringBootApplication e sub-pacotes) não acha os @Configuration/@Component/@Service de lá (ex.:
 * ClockConfig, ModelMapperConfig, CsrfCookieFilter, e agora EmailSenderServiceRouter/
 * EmailSettingsService da Fase 4) sozinho - precisa listar o pacote explicitamente.
 *
 * <p>Escaneia os 2 SUBPACOTES específicos (legacy + notification.mail), NÃO o pacote pai
 * com.nimbussystems.commons inteiro - mesmo achado do NimbusAuthServer: escanear o pai também traz
 * com.nimbussystems.commons.audit.AuditService, cujo bean-name default colide com uma classe local
 * homônima sem relação nenhuma.
 *
 * <p>excludeFilters: BffEmailSettingsController exige CurrentUserProvider (sessão OIDC de app
 * CLIENTE do NimbusAuth - não se aplica aqui, mesmo raciocínio) e InternalEmailSettingsController
 * da lib colidiria por bean-name com o InternalEmailSettingsController PRÓPRIO do CardSync
 * (com.cardsync.api.internal.controller, criado na Fase 2 pra ser chamado PELO NimbusAuth) -
 * CardSync mantém os dois controllers locais, rota/contrato inalterados.
 *
 * <p>@EntityScan/@EnableJpaRepositories novos (2026-09-15): com.nimbussystems.commons.
 * notification.mail TEM @Entity/@Repository (EmailSettingsEntity/Repository) - nimbus-commons-
 * legacy sozinho não tinha, por isso não existiam antes. com.cardsync precisa estar junto na MESMA
 * anotação - um @EntityScan(basePackages=only-commons) NÃO SOMA ao scan default, SUBSTITUI.
 */
@Configuration
@ComponentScan(
    basePackages = {
        "com.nimbussystems.commons.legacy",
        "com.nimbussystems.commons.notification.mail"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {BffEmailSettingsController.class, InternalEmailSettingsController.class}
    )
)
@EntityScan(basePackages = {"com.cardsync", "com.nimbussystems.commons.notification.mail"})
@EnableJpaRepositories(basePackages = {"com.cardsync", "com.nimbussystems.commons.notification.mail"})
public class CommonsIntegrationConfig {}
