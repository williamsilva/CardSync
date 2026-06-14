package com.cardsync.core.config;

import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "cardsync.app")
public class CardsyncAppProperties {

  @NotNull
  private ZoneId businessZone = ZoneId.of("America/Sao_Paulo");

  /**
   * Data em que o CardSync entrou em operação. Datas anteriores são tratadas
   * como completas nas agendas operacionais, pois não havia expectativa de arquivos.
   */
  @NotNull
  private LocalDate implantationDate = LocalDate.of(2024, 7, 1);

}
