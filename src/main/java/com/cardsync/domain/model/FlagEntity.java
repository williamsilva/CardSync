package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_flag")
public class FlagEntity extends AuditableEntityBase {

  private String name;
  private Integer status;
  private Integer erpCode;
  private String textColor;

  @OneToMany(mappedBy = "flag")
  private List<FlagAcquirerEntity> flagAcquirers = new ArrayList<>();

  @OneToMany(mappedBy = "flag")
  private List<FlagCompanyEntity> flagCompanies = new ArrayList<>();

  public StatusEnum getStatus() {
    return StatusEnum.fromCode(status);
  }

  public void setStatus(StatusEnum status) {
    this.status = (status!=null ? status:StatusEnum.NULL).getCode();
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
