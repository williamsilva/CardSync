package com.cardsync.core.file.service;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessedFilePvService {

  private final SalesSummaryRepository salesSummaryRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final EstablishmentRepository establishmentRepository;

  /** Coleta pvNumbers do SalesSummary vinculado ao arquivo (EEVC/EEVD) e auto-cadastra ausentes. */
  public void collectFromSalesSummary(ProcessedFileEntity processedFile) {
    List<Object[]> rows = salesSummaryRepository
      .findDistinctPvNumbersWithAcquirerByProcessedFileId(processedFile.getId());
    if (rows.isEmpty()) return;

    Set<Integer> pvNumbers = extractPvNumbers(rows);
    AcquirerEntity acquirer = (AcquirerEntity) rows.get(0)[1];

    processedFile.getPvNumbers().addAll(pvNumbers);
    autoRegisterMissing(pvNumbers, acquirer, processedFile.getDateFile());
  }

  /** Coleta pvCentralizer do CreditOrder vinculado ao arquivo (EEFI) e auto-cadastra ausentes. */
  public void collectFromCreditOrder(ProcessedFileEntity processedFile) {
    List<Object[]> rows = creditOrderRepository
      .findDistinctPvCentralizerWithAcquirerByProcessedFileId(processedFile.getId());
    if (rows.isEmpty()) return;

    Set<Integer> pvNumbers = extractPvNumbers(rows);
    AcquirerEntity acquirer = (AcquirerEntity) rows.get(0)[1];

    processedFile.getPvNumbers().addAll(pvNumbers);
    autoRegisterMissing(pvNumbers, acquirer, processedFile.getDateFile());
  }

  private Set<Integer> extractPvNumbers(List<Object[]> rows) {
    return rows.stream()
      .map(row -> (Integer) row[0])
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Coleta pvNumbers das releases bancárias vinculadas ao arquivo (CNAB). Sem auto-cadastro: pvCandidates de texto não são fonte definitiva. */
  public void collectFromBankReleases(ProcessedFileEntity processedFile, List<ReleasesBankEntity> releases) {
    Set<Integer> pvNumbers = releases.stream()
      .map(ReleasesBankEntity::getEstablishment)
      .filter(Objects::nonNull)
      .map(EstablishmentEntity::getPvNumber)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    processedFile.getPvNumbers().addAll(pvNumbers);
  }

  private void autoRegisterMissing(Set<Integer> pvNumbers, AcquirerEntity acquirer, LocalDate fileDate) {
    Set<Integer> existing = establishmentRepository
      .findExistingPvNumbersByAcquirerId(acquirer.getId(), pvNumbers);

    List<EstablishmentEntity> toCreate = pvNumbers.stream()
      .filter(pv -> !existing.contains(pv))
      .map(pv -> {
        EstablishmentEntity entity = new EstablishmentEntity();
        entity.setPvNumber(pv);
        entity.setAcquirer(acquirer);
        entity.setType(TypeEstablishmentEnum.PDV_TEF);
        entity.setStatus(StatusEnum.ACTIVE);
        entity.setOpeningDate(fileDate != null ? fileDate : LocalDate.now());
        return entity;
      })
      .toList();

    if (!toCreate.isEmpty()) {
      establishmentRepository.saveAll(toCreate);
    }
  }
}
