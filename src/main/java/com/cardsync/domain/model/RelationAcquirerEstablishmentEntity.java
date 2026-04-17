package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"acquirer", "establishment"})
@Table(
  name = "cs_acquirer_establishment",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_cs_acquirer_establishment_acquirer_establishment", columnNames = {"acquirer_id", "establishment_id"})
  }
)
public class RelationAcquirerEstablishmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id", nullable = false)
  private AcquirerEntity acquirer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establishment_id", nullable = false)
  private EstablishmentEntity establishment;
}