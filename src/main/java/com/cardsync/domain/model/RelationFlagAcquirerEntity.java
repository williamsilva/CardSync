package com.cardsync.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"flag", "acquirer"})
@Table(
  name = "cs_flag_acquirer",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_cs_flag_acquirer_flag_acquirer", columnNames = {"flag_id", "acquirer_id"})
  }
)
public class RelationFlagAcquirerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "acquirer_code", nullable = false, length = 2)
  private String acquirerCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flag_id")
  private FlagEntity flag;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id")
  private AcquirerEntity acquirer;
}
