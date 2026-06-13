package com.cardsync.core.file.rede.service;

import com.cardsync.bff.controller.v1.representation.model.rede.RedeTotalizerModel;
import com.cardsync.domain.model.CreditTotalizerEntity;
import com.cardsync.domain.model.TotalizerMatrixEntity;
import com.cardsync.domain.repository.CreditTotalizerRepository;
import com.cardsync.domain.repository.TotalizerMatrixRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedeFinancialQueryService {

  private final CreditTotalizerRepository creditTotalizerRepository;
  private final TotalizerMatrixRepository totalizerMatrixRepository;

  @Transactional(readOnly = true)
  public Page<RedeTotalizerModel> listTotalizers(Pageable pageable) {
    List<RedeTotalizerModel> items = new ArrayList<>();
    creditTotalizerRepository.findAll().stream().map(this::toCreditTotalizerModel).forEach(items::add);
    totalizerMatrixRepository.findAll().stream().map(this::toMatrixTotalizerModel).forEach(items::add);

    items.sort(Comparator
      .comparing(RedeTotalizerModel::processedFile, Comparator.nullsLast(String::compareToIgnoreCase))
      .thenComparing(RedeTotalizerModel::lineNumber, Comparator.nullsLast(Integer::compareTo)));

    int start = Math.toIntExact(Math.min(pageable.getOffset(), items.size()));
    int end = Math.min(start + pageable.getPageSize(), items.size());
    return new PageImpl<>(items.subList(start, end), pageable, items.size());
  }

  private RedeTotalizerModel toCreditTotalizerModel(CreditTotalizerEntity entity) {
    return new RedeTotalizerModel(
      entity.getId(), "CREDIT_TOTALIZER", fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getPvNumber(),
      entity.getCreditDate(), entity.getTotalCreditValue(), entity.getTotalValueAdvanceCredits(), null,
      null, null, null, null, null, null, acquirerName(entity.getAcquirer()), companyName(entity.getCompany()), null
    );
  }

  private RedeTotalizerModel toMatrixTotalizerModel(TotalizerMatrixEntity entity) {
    return new RedeTotalizerModel(
      entity.getId(), "MATRIX_TOTALIZER", fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getPvNumber(),
      null, null, null, entity.getTotalNumberMatrixSummaries(), entity.getTotalValueNormalCredits(),
      entity.getTotalValueAnticipated(), entity.getAmountCreditAdjustments(), entity.getTotalValueCreditAdjustments(),
      entity.getAmountDebitAdjustments(), entity.getTotalValueDebitAdjustments(), acquirerName(entity.getAcquirer()),
      companyName(entity.getCompany()), entity.getEstablishment() != null ? String.valueOf(entity.getEstablishment().getPvNumber()) : null
    );
  }

  private String fileName(com.cardsync.domain.model.ProcessedFileEntity processedFile) {
    return processedFile != null ? processedFile.getFile() : null;
  }

  private String acquirerName(com.cardsync.domain.model.AcquirerEntity acquirer) {
    return acquirer != null ? acquirer.getFantasyName() : null;
  }

  private String companyName(com.cardsync.domain.model.CompanyEntity company) {
    return company != null ? company.getFantasyName() : null;
  }
}
