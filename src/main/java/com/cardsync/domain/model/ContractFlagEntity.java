package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_contract_flags")
public class ContractFlagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flag_id")
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id")
  private ContractEntity contract;

  @OneToMany(mappedBy = "contractFlag", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("modality ASC")
  private List<ContractRateEntity> contractRates = new ArrayList<>();
}
