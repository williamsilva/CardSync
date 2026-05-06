package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_serasa_consultation")
public class SerasaConsultationEntity extends AuditableEntityBase {

  private Integer pvNumber;
  private Integer lineNumber;
  private String recordType;
  private String serviceType;
  private Integer numberConsultationCarriedOut;
  private BigDecimal totalValueConsultation;
  private LocalDate startConsultationPeriod;
  private LocalDate endConsultationPeriod;
  private BigDecimal valueConsultationPeriod;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;
}
