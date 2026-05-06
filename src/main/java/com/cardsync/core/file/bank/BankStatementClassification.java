package com.cardsync.core.file.bank;

import com.cardsync.domain.model.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class BankStatementClassification {

  @Setter
  @Getter
  private BankEntity bank;

  @Setter
  @Getter
  private CompanyEntity company;

  @Setter
  @Getter
  private BankingDomicileEntity bankingDomicile;
  @Setter
  @Getter
  private AcquirerEntity acquirer;
  @Setter
  @Getter
  private EstablishmentEntity establishment;
  @Setter
  @Getter
  private FlagEntity flag;
  @Getter
  @Setter
  private Integer modalityPaymentBank;
  @Getter
  @Setter
  private String normalizedText;
  private final List<Integer> pvCandidates = new ArrayList<>();
  private final List<String> notes = new ArrayList<>();

  public List<Integer> getPvCandidates() {
    return Collections.unmodifiableList(pvCandidates);
  }

  public void addPvCandidate(Integer pv) {
    if (pv != null && !pvCandidates.contains(pv)) pvCandidates.add(pv);
  }

  public void addPvCandidates(List<Integer> pvs) {
    if (pvs == null) return;
    pvs.forEach(this::addPvCandidate);
  }

  public List<String> getNotes() {
    return Collections.unmodifiableList(notes);
  }

  public void addNote(String note) {
    if (note != null && !note.isBlank()) notes.add(note);
  }

  public boolean isIncomplete() {
    return company == null || bankingDomicile == null || acquirer == null || establishment == null;
  }

  public String contextSummary() {
    return "empresa=" + (company != null)
      + ", domicilio=" + (bankingDomicile != null)
      + ", adquirente=" + (acquirer != null)
      + ", estabelecimento=" + (establishment != null)
      + ", bandeira=" + (flag != null)
      + ", modalidade=" + modalityPaymentBank
      + ", pvCandidates=" + pvCandidates
      + (notes.isEmpty() ? "" : ", notas=" + notes);
  }
}
