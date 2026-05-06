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

  private BigDecimal rate;
  private Integer modality;
  private Integer paymentTermDays;
  private Integer installmentMin;
  private Integer installmentMax;
  private BigDecimal rateEcommerce;
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
