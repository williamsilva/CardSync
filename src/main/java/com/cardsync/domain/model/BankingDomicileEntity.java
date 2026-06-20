package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_banking_domicile")
public class BankingDomicileEntity extends AuditableEntityBase {

  @Column(name = "agency", nullable = false)
  private Integer agency;

  @Column(name = "agency_digit", length = 5)
  private String agencyDigit;

  @Column(name = "current_account", nullable = false)
  private Integer currentAccount;

  @Column(name = "account_digit")
  private String accountDigit;

  @NotNull
  @Column(name = "account_opening_date", nullable = false)
  private LocalDate accountOpeningDate;

  @Column(name = "account_closing_date")
  private LocalDate accountClosingDate;

  @NotNull
  @Column(name = "expects_file", nullable = false)
  private Boolean expectsFile = Boolean.TRUE;

  @Column(name = "status_date")
  private OffsetDateTime statusDate;

  @Column(name = "status", nullable = false)
  private Integer status = StatusEnum.ACTIVE.getCode();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bank_id", nullable = false)
  private BankEntity bank;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private CompanyEntity company;

  @AssertTrue(message = "A data de encerramento da conta deve ser igual ou posterior à data de abertura")
  public boolean isAccountPeriodValid() {
    if (accountOpeningDate == null || accountClosingDate == null) {
      return true;
    }
    return !accountClosingDate.isBefore(accountOpeningDate);
  }

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    StatusEnum normalizedStatus = status != null ? status : StatusEnum.NULL;
    Integer newCode = normalizedStatus.getCode();

    if (!Objects.equals(this.status, newCode)) {
      this.statusDate = OffsetDateTime.now();
    }

    this.status = newCode;
  }

  public void activate() {
    setStatus(StatusEnum.ACTIVE);
    this.accountClosingDate = null;
  }

  public void inactivate() {
    setStatus(StatusEnum.INACTIVE);
    this.accountClosingDate = LocalDate.now();
  }

  public void block() {
    setStatus(StatusEnum.BLOCKED);
    this.accountClosingDate = LocalDate.now();
  }
}
