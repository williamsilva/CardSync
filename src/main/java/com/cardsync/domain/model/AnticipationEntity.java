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
@Table(name = "cs_anticipation")
public class AnticipationEntity extends AuditableEntityBase {

  private Integer agency;
  private Integer pvNumber;
  private Integer lineNumber;
  private Integer currentAccount;
  private Integer numberDocument;
  private Integer pvNumberOriginal;
  private Integer installmentNumber;
  private Integer installmentNumberMax;
  private Integer numberRvCorresponding;

  private String bank;
  private String credit;
  private String recordType;

  private LocalDate releaseDate;
  private LocalDate originalDueDate;
  private LocalDate dateRvCorresponding;

  private Boolean generatedOrders;

  private BigDecimal grossValue;
  private BigDecimal releaseValue;
  private BigDecimal discountRateValue;
  private BigDecimal originalCreditValue;

  @ManyToOne(fetch = FetchType.LAZY)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  private CompanyEntity company;

  @ManyToOne(fetch = FetchType.LAZY)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  private ProcessedFileEntity processedFile;

  @ManyToOne(fetch = FetchType.LAZY)
  private EstablishmentEntity establishment;

  @ManyToOne(fetch = FetchType.LAZY)
  private SalesSummaryEntity salesSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  private BankingDomicileEntity bankingDomicile;

  // Setado por SalesSummaryCreditOrderReconciliationService#generateSyntheticOrdersFromAnticipations
  // no momento em que a ordem sintética é gerada (junto com generatedOrders=true) - é o único jeito
  // de saber, a partir da Antecipação, se/quando o valor foi de fato confirmado no banco
  // (CreditOrderEntity.statusPaymentBank), já que a Antecipação em si não tem status de pagamento
  // próprio (achado real 2026-09-10, ver AnticipationModelAssembler).
  @ManyToOne(fetch = FetchType.LAZY)
  private CreditOrderEntity creditOrder;
}
