package com.cardsync.core.file.rede.service;

import com.cardsync.bff.controller.v1.representation.model.rede.RedeAdjustmentModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeAnticipationModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeCreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedePendingDebtModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeSettledDebtModel;
import com.cardsync.bff.controller.v1.representation.model.rede.RedeTotalizerModel;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.CreditTotalizerEntity;
import com.cardsync.domain.model.PendingDebtEntity;
import com.cardsync.domain.model.SettledDebtEntity;
import com.cardsync.domain.model.TotalizerMatrixEntity;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.AnticipationRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.CreditTotalizerRepository;
import com.cardsync.domain.repository.PendingDebtRepository;
import com.cardsync.domain.repository.SettledDebtRepository;
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

  private final CreditOrderRepository creditOrderRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final AnticipationRepository anticipationRepository;
  private final SettledDebtRepository settledDebtRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final CreditTotalizerRepository creditTotalizerRepository;
  private final TotalizerMatrixRepository totalizerMatrixRepository;

  @Transactional(readOnly = true)
  public Page<RedeCreditOrderModel> listCreditOrders(Pageable pageable) {
    return creditOrderRepository.findAll(pageable).map(this::toCreditOrderModel);
  }

  @Transactional(readOnly = true)
  public Page<RedeAdjustmentModel> listAdjustments(Pageable pageable) {
    return adjustmentRepository.findAll(pageable).map(this::toAdjustmentModel);
  }

  @Transactional(readOnly = true)
  public Page<RedeAnticipationModel> listAnticipations(Pageable pageable) {
    return anticipationRepository.findAll(pageable).map(this::toAnticipationModel);
  }

  @Transactional(readOnly = true)
  public Page<RedeSettledDebtModel> listSettledDebts(Pageable pageable) {
    return settledDebtRepository.findAll(pageable).map(this::toSettledDebtModel);
  }

  @Transactional(readOnly = true)
  public Page<RedePendingDebtModel> listPendingDebts(Pageable pageable) {
    return pendingDebtRepository.findAll(pageable).map(this::toPendingDebtModel);
  }

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

  private RedeCreditOrderModel toCreditOrderModel(CreditOrderEntity entity) {
    return new RedeCreditOrderModel(
      entity.getId(), fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getCreditOrderNumber(),
      entity.getRvNumber(), entity.getOriginalPvNumber(), entity.getInstallmentNumber(), entity.getInstallmentTotal(),
      entity.getRvDate(), entity.getReleaseDate(), entity.getCreditOrderDate(), entity.getReleaseValue(),
      entity.getGrossRvValue(), entity.getDiscountRateValue(), acquirerName(entity.getAcquirer()),
      entity.getFlag() != null ? entity.getFlag().getName() : null, companyName(entity.getCompany())
    );
  }

  private RedeAdjustmentModel toAdjustmentModel(AdjustmentEntity entity) {
    return new RedeAdjustmentModel(
      entity.getId(), fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getRecordType(),
      entity.getSourceRecordIdentifier(), entity.getEcommerce(), entity.getPvNumber(), entity.getNsu(),
      entity.getAuthorization(), entity.getTid(), entity.getAdjustmentReason(), entity.getAdjustmentDescription(),
      entity.getAdjustmentDate(), entity.getCreditDate(), entity.getReleaseDate(), entity.getAdjustmentValue(),
      entity.getGrossValue(), entity.getLiquidValue(), entity.getDiscountValue(), acquirerName(entity.getAcquirer()),
      companyName(entity.getCompany()), entity.getEstablishment() != null ? String.valueOf(entity.getEstablishment().getPvNumber()) : null
    );
  }

  private RedeAnticipationModel toAnticipationModel(AnticipationEntity entity) {
    return new RedeAnticipationModel(
      entity.getId(), fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getPvNumber(),
      entity.getNumberRvCorresponding(), entity.getInstallmentNumber(), entity.getInstallmentNumberMax(),
      entity.getReleaseDate(), entity.getOriginalDueDate(), entity.getDateRvCorresponding(), entity.getGrossValue(),
      entity.getReleaseValue(), entity.getDiscountRateValue(), entity.getOriginalCreditValue(), acquirerName(entity.getAcquirer()),
      entity.getFlag() != null ? entity.getFlag().getName() : null, companyName(entity.getCompany()),
      entity.getEstablishment() != null ? String.valueOf(entity.getEstablishment().getPvNumber()) : null
    );
  }

  private RedeSettledDebtModel toSettledDebtModel(SettledDebtEntity entity) {
    return new RedeSettledDebtModel(
      entity.getId(), fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getRecordType(),
      entity.getPvNumber(), entity.getNsu(), entity.getAuthorization(), entity.getTid(), entity.getNumberDebitOrder(),
      entity.getDateDebitOrder(), entity.getLiquidatedDate(), entity.getValueDebitOrder(), entity.getLiquidatedValue(),
      entity.getReasonCode(), entity.getReasonDescription(), acquirerName(entity.getAcquirer()),
      entity.getFlag() != null ? entity.getFlag().getName() : null
    );
  }

  private RedePendingDebtModel toPendingDebtModel(PendingDebtEntity entity) {
    return new RedePendingDebtModel(
      entity.getId(), fileName(entity.getProcessedFile()), entity.getLineNumber(), entity.getRecordType(),
      entity.getPvNumber(), entity.getNsu(), entity.getAuthorization(), entity.getTid(), entity.getNumberDebitOrder(),
      entity.getDateDebitOrder(), entity.getValueDebitOrder(), entity.getCompensatedValue(), entity.getReasonCode(),
      entity.getReasonDescription(), acquirerName(entity.getAcquirer()), entity.getFlag() != null ? entity.getFlag().getName() : null,
      companyName(entity.getCompany()), entity.getEstablishment() != null ? String.valueOf(entity.getEstablishment().getPvNumber()) : null
    );
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
