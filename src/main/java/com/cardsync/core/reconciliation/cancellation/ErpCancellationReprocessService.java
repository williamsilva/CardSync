package com.cardsync.core.reconciliation.cancellation;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpCancellationReprocessResult;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCancellationReprocessService {

  private static final int BATCH_SIZE = 1_000;

  private final EntityManager entityManager;
  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionErpRepository transactionErpRepository;

  /**
   * Reprocessa vendas canceladas da adquirente para um mês informado, propagando o cancelamento
   * para as vendas ERP vinculadas que ainda não foram canceladas.
   *
   * Cobre dois cenários:
   * 1. ERP já vinculado ao ACQ via FK (transactionAcq) mas não cancelado.
   * 2. ERP não vinculado ao ACQ — buscado por NSU + autorização + adquirente; se encontrado,
   *    vincula e cancela na mesma operação.
   */
  @Transactional
  public ErpCancellationReprocessResult reprocess(int year, int month) {
    List<UUID> acqIds = transactionAcqRepository.findCancelledAcqIdsForMonthReprocess(
      StatusTransactionEnum.CANCELED.getCode(), year, month);
    log.info("🔁 Reprocessamento de cancelamentos ERP iniciado. year={}, month={}, candidatos={}", year, month, acqIds.size());
    int[] counts = processAcqIds(acqIds);
    log.info("✅ Reprocessamento de cancelamentos ERP finalizado. year={}, month={}, acqAnalisadas={}, erpCanceladas={}, vinculadas={}, parcelas={}, jaCanceladas={}, semErp={}",
      year, month, counts[0], counts[1], counts[3], counts[2], counts[4], counts[5]);
    return new ErpCancellationReprocessResult(year, month, counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
  }

  @Transactional
  public ErpCancellationReprocessResult reprocessPendingAll() {
    List<UUID> acqIds = transactionAcqRepository.findCancelledAcqIdsForPipelineReprocess(
      StatusTransactionEnum.CANCELED.getCode());
    log.info("🔁 Reprocessamento de cancelamentos ERP (esteira) iniciado. candidatos={}", acqIds.size());
    int[] counts = processAcqIds(acqIds);
    log.info("✅ Reprocessamento de cancelamentos ERP (esteira) finalizado. acqAnalisadas={}, erpCanceladas={}, vinculadas={}, parcelas={}, jaCanceladas={}, semErp={}",
      counts[0], counts[1], counts[3], counts[2], counts[4], counts[5]);
    return new ErpCancellationReprocessResult(0, 0, counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
  }

  private int[] processAcqIds(List<UUID> acqIds) {
    int totalBatches = (int) Math.ceil(acqIds.size() / (double) BATCH_SIZE);
    int acqSalesCancelled = 0;
    int erpSalesCancelled = 0;
    int erpInstallmentsCancelled = 0;
    int erpLinkedBeforeCancel = 0;
    int skippedAlreadyCancelled = 0;
    int skippedNoErpLinked = 0;

    for (int start = 0, batch = 1; start < acqIds.size(); start += BATCH_SIZE, batch++) {
      List<UUID> batchIds = acqIds.subList(start, Math.min(start + BATCH_SIZE, acqIds.size()));
      List<TransactionAcqEntity> acqBatch = transactionAcqRepository.findBatchForCancelledMonthReprocess(batchIds);
      Map<UUID, TransactionErpEntity> erpByAcqId = findErpByAcqId(batchIds);

      int batchErpCancelled = 0;
      int batchInstallmentsCancelled = 0;
      int batchLinked = 0;
      int batchSkippedAlready = 0;
      int batchSkippedNoErp = 0;
      Map<UUID, TransactionErpEntity> erpToSave = new LinkedHashMap<>();

      for (TransactionAcqEntity acq : acqBatch) {
        acqSalesCancelled++;
        TransactionErpEntity erp = erpByAcqId.get(acq.getId());

        if (erp == null) {
          erp = findUnlinkedErpByNsuAuth(acq);
          if (erp != null) {
            erp.setTransactionAcq(acq);
            applyAcqBusinessContext(erp, acq);
            batchLinked++;
            log.debug("🔗 ERP sem vínculo encontrado por NSU/auth/adquirente e vinculado ao ACQ. erpId={}, acqId={}, nsu={}, auth={}",
              erp.getId(), acq.getId(), acq.getNsu(), acq.getAuthorization());
          }
        }

        if (erp == null) {
          batchSkippedNoErp++;
          continue;
        }

        if (isCanceled(StatusTransactionEnum.toCode(erp.getStatusTransaction()))) {
          batchSkippedAlready++;
          continue;
        }

        LocalDate cancellationDate = resolveCancellationDate(acq);
        StatusTransactionReasonEnum reason = resolveReason(acq);
        OffsetDateTime now = OffsetDateTime.now();

        cancelErpSale(erp, reason, cancellationDate, now);
        batchInstallmentsCancelled += cancelErpInstallments(erp, cancellationDate);
        batchErpCancelled++;
        erpToSave.put(erp.getId(), erp);
      }

      if (!erpToSave.isEmpty()) {
        transactionErpRepository.saveAll(erpToSave.values());
      }
      entityManager.flush();
      entityManager.clear();

      erpSalesCancelled += batchErpCancelled;
      erpInstallmentsCancelled += batchInstallmentsCancelled;
      erpLinkedBeforeCancel += batchLinked;
      skippedAlreadyCancelled += batchSkippedAlready;
      skippedNoErpLinked += batchSkippedNoErp;

      log.info("🔄 Reprocessamento batch={}/{}: erpCanceladas={}, vinculadas={}, parcelasCanceladas={}, jaCanceladas={}, semErp={}",
        batch, totalBatches, batchErpCancelled, batchLinked, batchInstallmentsCancelled,
        batchSkippedAlready, batchSkippedNoErp);
    }

    return new int[]{acqSalesCancelled, erpSalesCancelled, erpInstallmentsCancelled, erpLinkedBeforeCancel, skippedAlreadyCancelled, skippedNoErpLinked};
  }

  private Map<UUID, TransactionErpEntity> findErpByAcqId(Collection<UUID> acqIds) {
    List<TransactionErpEntity> erpList = transactionErpRepository.findByTransactionAcqIdsForCancellationReconciliation(acqIds);
    Map<UUID, TransactionErpEntity> result = new LinkedHashMap<>();
    for (TransactionErpEntity erp : erpList) {
      if (erp.getTransactionAcq() != null && erp.getTransactionAcq().getId() != null) {
        result.put(erp.getTransactionAcq().getId(), erp);
      }
    }
    return result;
  }

  private TransactionErpEntity findUnlinkedErpByNsuAuth(TransactionAcqEntity acq) {
    if (acq.getNsu() == null || acq.getAuthorization() == null || acq.getAcquirer() == null) {
      return null;
    }
    List<TransactionErpEntity> candidates = transactionErpRepository
      .findUnlinkedByNsuAuthorizationAndAcquirerForCancellationReprocess(
        acq.getNsu(),
        acq.getAuthorization(),
        acq.getAcquirer().getId()
      );
    return candidates.isEmpty() ? null : candidates.get(0);
  }

  private void applyAcqBusinessContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (erp.getFlag() == null && acq.getFlag() != null) {
      erp.setFlag(acq.getFlag());
    }
    if (erp.getCompany() == null && acq.getCompany() != null) {
      erp.setCompany(acq.getCompany());
    }
    if (erp.getEstablishment() == null && acq.getEstablishment() != null) {
      erp.setEstablishment(acq.getEstablishment());
    }
    if (erp.getBankingDomicile() == null && acq.getSalesSummary() != null) {
      BankingDomicileEntity bd = acq.getSalesSummary().getBankingDomicile();
      if (bd != null) {
        erp.setBankingDomicile(bd);
      }
    }
  }

  private void cancelErpSale(
    TransactionErpEntity erp,
    StatusTransactionReasonEnum reason,
    LocalDate cancellationDate,
    OffsetDateTime reconciliationDate
  ) {
    setIfDifferent(erp::getStatusTransaction, erp::setStatusTransaction, StatusTransactionEnum.CANCELED);
    setIfDifferent(erp::getStatusTransactionReason, erp::setStatusTransactionReason, reason);
    setIfDifferent(erp::getFeeReconciliationStatus, erp::setFeeReconciliationStatus, FeeReconciliationStatusEnum.RECONCILED);
    setIfDifferent(erp::getCanceledDate, erp::setCanceledDate, cancellationDate);
    setIfDifferent(erp::getSaleReconciliationDate, erp::setSaleReconciliationDate, reconciliationDate);
  }

  private int cancelErpInstallments(TransactionErpEntity erp, LocalDate cancellationDate) {
    if (erp.getInstallments() == null || erp.getInstallments().isEmpty()) {
      return 0;
    }

    int updated = 0;
    for (InstallmentErpEntity installment : erp.getInstallments()) {
      boolean changed = false;
      changed |= setIfDifferent(installment::getInstallmentStatus, installment::setInstallmentStatus, StatusInstallmentEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getStatusPaymentBank, installment::setStatusPaymentBank, StatusPaymentBankEnum.CANCELED.getCode());
      changed |= setIfDifferent(installment::getCancellationDate, installment::setCancellationDate, cancellationDate);
      if (changed) {
        updated++;
      }
    }
    return updated;
  }

  private LocalDate resolveCancellationDate(TransactionAcqEntity acq) {
    if (acq.getCanceledDate() != null) {
      return acq.getCanceledDate();
    }
    return acq.getSaleDate() != null ? acq.getSaleDate().toLocalDate() : LocalDate.now();
  }

  private StatusTransactionReasonEnum resolveReason(TransactionAcqEntity acq) {
    StatusTransactionReasonEnum acqReason = acq.getStatusTransactionReason();
    return acqReason != null ? acqReason : StatusTransactionReasonEnum.CANCELLATION_ACQUIRER;
  }

  private boolean isCanceled(Integer status) {
    return Objects.equals(status, StatusTransactionEnum.CANCELED.getCode());
  }

  private <T> boolean setIfDifferent(Supplier<T> getter, Consumer<T> setter, T newValue) {
    if (newValue == null || Objects.equals(getter.get(), newValue)) {
      return false;
    }
    setter.accept(newValue);
    return true;
  }
}
