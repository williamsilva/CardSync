package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_flag")
public class FlagEntity  {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;
  private Integer status;
  private Integer erpCode;

  @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<FlagAcquirerEntity> flagAcquirers = new LinkedHashSet<>();

  @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<FlagCompanyEntity> flagCompanies = new LinkedHashSet<>();

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
