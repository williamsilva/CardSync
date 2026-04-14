package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_contract_flags")
public class ContractFlagEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
}
