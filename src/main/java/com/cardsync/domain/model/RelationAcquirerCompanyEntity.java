package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"acquirer", "company"})
@Table(
  name = "cs_acquirer_company",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_cs_acquirer_company_acquirer_company", columnNames = {"acquirer_id", "company_id"})
  }
)
public class RelationAcquirerCompanyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id", nullable = false)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private CompanyEntity company;
}