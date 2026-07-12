package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ModalityEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_contract_rates")
public class ContractRateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private BigDecimal rate;

  @Column(nullable = false)
  private Integer modality;

  @Column(name = "payment_term_days", nullable = false)
  private Integer paymentTermDays;

  private Integer installmentMin;
  private Integer installmentMax;

  @Column(name = "rate_ecommerce", nullable = false)
  private BigDecimal rateEcommerce;

  @Column(name = "payment_term_days_ecommerce", nullable = false)
  private Integer paymentTermDaysEcommerce;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_flag_id")
  private ContractFlagEntity contractFlag;

  public ModalityEnum getModality() {
    return ModalityEnum.fromCode(modality);
  }

  public void setModality(ModalityEnum modality) {
    this.modality = (modality != null ? modality : ModalityEnum.NULL).getCode();
  }
}
