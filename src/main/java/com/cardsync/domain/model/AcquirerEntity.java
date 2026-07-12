package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_acquirer")
public class AcquirerEntity extends AuditableEntityBase {

  @Column(name = "status", nullable = false)
  private Integer status;

  @Column(name = "status_date")
  private OffsetDateTime statusDate;

  @Column(name = "opening_date")
  private LocalDate openingDate;

  @Column(name = "closing_date")
  private LocalDate closingDate;

  @Column(name = "cnpj", nullable = false, unique = true, length = 20)
  private String cnpj;

  @Column(name = "fantasy_name", nullable = false, length = 50)
  private String fantasyName;

  @Column(name = "social_reason", nullable = false, length = 50)
  private String socialReason;

  @Column(name = "file_identifier", nullable = false, unique = true, length = 30)
  private String fileIdentifier;

  @OneToMany(mappedBy = "acquirer", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RelationAcquirerEstablishmentEntity> acquirerEstablishments = new ArrayList<>();

  @OneToMany(mappedBy = "acquirer", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RelationAcquirerCompanyEntity> acquirerCompanies = new ArrayList<>();

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    StatusEnum normalizedStatus = status != null ? status : StatusEnum.NULL;
    Integer newCode = normalizedStatus.getCode();

    if (!java.util.Objects.equals(this.status, newCode)) {
      this.statusDate = OffsetDateTime.now();
    }

    this.status = newCode;
  }

  public void activate() {
    setStatus(StatusEnum.ACTIVE);
    this.closingDate = null;
  }

  public void inactivate() {
    setStatus(StatusEnum.INACTIVE);
    this.closingDate = LocalDate.now();
  }

  public void block() {
    setStatus(StatusEnum.BLOCKED);
    this.closingDate = LocalDate.now();
  }
}