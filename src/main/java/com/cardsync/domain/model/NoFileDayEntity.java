package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "cs_no_file_day")
public class NoFileDayEntity extends AuditableEntityBase {

  @Column(name = "no_file_date", nullable = false)
  private LocalDate noFileDate;

  @Column(name = "description", length = 255, nullable = false)
  private String description;

  @Column(name = "day_type", nullable = false)
  private Integer dayType = NoFileDayTypeEnum.NO_MOVEMENT.getCode();

  /**
   * Grupo que não terá arquivo no dia: ERP, ADQ ou BANK.
   */
  @Column(name = "file_group", nullable = false)
  private String fileGroup;

  /**
   * Quando o grupo é BANK, indica o banco específico que não terá arquivo.
   * Nulo = todos os bancos do dia.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bank_id")
  private BankEntity bank;

  /**
   * Quando o grupo é ADQ, indica a adquirente específica que não terá arquivo.
   * Nulo = todas as adquirentes do dia.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id")
  private AcquirerEntity acquirer;

  @Column(name = "status", nullable = false)
  private Integer status = StatusEnum.ACTIVE.getCode();

  @Column(name = "status_date")
  private OffsetDateTime statusDate;

  public NoFileDayTypeEnum getDayType() {
    return NoFileDayTypeEnum.fromCode(dayType);
  }

  public void setDayType(NoFileDayTypeEnum type) {
    NoFileDayTypeEnum normalized = type != null ? type : NoFileDayTypeEnum.NULL;
    this.dayType = normalized.getCode();
  }

  public FileGroupEnum getFileGroup() {
    return fileGroup != null ? FileGroupEnum.valueOf(fileGroup) : null;
  }

  public void setFileGroup(FileGroupEnum group) {
    this.fileGroup = group != null ? group.name() : null;
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
  }

  public void inactivate() {
    setStatus(StatusEnum.INACTIVE);
  }

  public void block() {
    setStatus(StatusEnum.BLOCKED);
  }
}