package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.AcquirerFileTypeEnum;
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
   * Quando o grupo é BANK, indica o domicílio bancário específico que não terá arquivo.
   * Nulo = todos os domicílios bancários do dia.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "banking_domicile_id")
  private BankingDomicileEntity bankingDomicile;

  /**
   * Quando o grupo é ADQ, indica a adquirente específica que não terá arquivo.
   * Nulo = todas as adquirentes do dia.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "acquirer_id")
  private AcquirerEntity acquirer;

  /**
   * Tipo de arquivo da adquirente que não será recebido no dia.
   * Obrigatório para registros do grupo ADQ.
   */
  @Column(name = "acquirer_file_type", length = 20)
  private String acquirerFileType;

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


  public AcquirerFileTypeEnum getAcquirerFileType() {
    return acquirerFileType != null ? AcquirerFileTypeEnum.valueOf(acquirerFileType) : null;
  }

  public void setAcquirerFileType(AcquirerFileTypeEnum type) {
    this.acquirerFileType = type != null ? type.name() : null;
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