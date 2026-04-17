package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"flag", "company"})
@Table(
  name = "cs_flag_company",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_cs_flag_company_flag_company", columnNames = {"flag_id", "company_id"})
  }
)
public class RelationFlagCompanyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flag_id", nullable = false)
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private CompanyEntity company;
}