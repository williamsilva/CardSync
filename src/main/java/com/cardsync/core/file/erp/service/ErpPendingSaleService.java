package com.cardsync.core.file.erp.service;

import com.cardsync.bff.controller.v1.representation.model.erp.ErpPendingSaleModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ReprocessPendingErpResultModel;
import com.cardsync.core.file.erp.calculator.InstallmentErpGenerator;
import com.cardsync.core.file.erp.calculator.TransactionErpFeeCalculator;
import com.cardsync.core.file.erp.resolver.ErpBusinessContextResolver;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.TransactionErpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPendingSaleService {
  private static final Set<ErpCommercialStatusEnum> PENDING_STATUSES = EnumSet.of(
    ErpCommercialStatusEnum.PENDING_COMPANY,
    ErpCommercialStatusEnum.PENDING_ESTABLISHMENT,
    ErpCommercialStatusEnum.PENDING_CONTRACT,
    ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT
  );

  private final TransactionErpRepository transactionErpRepository;
  private final TransactionErpFeeCalculator transactionErpFeeCalculator;
  private final InstallmentErpGenerator installmentErpGenerator;
  private final ErpBusinessContextResolver erpBusinessContextResolver;

  @Transactional(readOnly = true)
  public Page<ErpPendingSaleModel> listPending(Pageable pageable) {
    return transactionErpRepository.findByCommercialStatusIn(PENDING_STATUSES, pageable).map(this::toModel);
  }

  @Transactional(readOnly = true)
  public ErpPendingSaleModel findPending(UUID id) {
    TransactionErpEntity tx = transactionErpRepository.findByIdAndCommercialStatusIn(id, PENDING_STATUSES)
      .orElseThrow(() -> new IllegalArgumentException("Venda ERP pendente não encontrada: " + id));
    return toModel(tx);
  }

  @Transactional
  public ReprocessPendingErpResultModel reprocessPending() {
    var transactions = transactionErpRepository.findTop500ByCommercialStatusInOrderBySaleDateAsc(PENDING_STATUSES);
    int reprocessed = 0;
    int resolved = 0;
    int stillPendingContract = 0;
    int stillPendingBusinessContext = 0;
    int errors = 0;

    for (TransactionErpEntity tx : transactions) {
      try {
        reprocessed++;

        // 1) Pendências de empresa/PV tentam resolver novamente usando os dados brutos salvos na importação.
        if (tx.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_COMPANY
          || tx.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_ESTABLISHMENT
          || tx.getCommercialStatus() == ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT) {
          erpBusinessContextResolver.resolve(tx);
        }

        boolean contextIncomplete = tx.getCompany() == null || tx.getEstablishment() == null;
        if (contextIncomplete) {
          applyBusinessContextPending(tx);
          stillPendingBusinessContext++;
          log.warn("⚠ Pendência ERP ainda sem contexto comercial. id={}, nsu={}, cnpjOrigem={}, pvOrigem={}",
            tx.getId(), tx.getNsu(), tx.getSourceCompanyCnpj(), tx.getSourceEstablishmentPvNumber());
          continue;
        }

        // 2) Com contexto resolvido, recalcula contrato/taxa considerando também installmentCount.
        var feeResult = transactionErpFeeCalculator.calculateContractedFee(tx);
        if (feeResult.contractFound()) {
          tx.getInstallments().clear();
          installmentErpGenerator.generate(tx, feeResult.paymentTermDays()).forEach(tx::addInstallment);
          tx.setCommercialStatus(ErpCommercialStatusEnum.OK);
          tx.setCommercialStatusMessage("Pendência ERP resolvida: contexto comercial e contrato vigente encontrados no reprocessamento.");
          resolved++;
          log.info("✅ Pendência ERP resolvida. id={}, nsu={}, parcelas={}, taxa={}",
            tx.getId(), tx.getNsu(), tx.getInstallment(), tx.getContractedFee());
        } else {
          tx.getInstallments().clear();
          installmentErpGenerator.generate(tx, feeResult.paymentTermDays()).forEach(tx::addInstallment);
          tx.setCommercialStatus(ErpCommercialStatusEnum.PENDING_CONTRACT);
          tx.setCommercialStatusMessage("Venda ainda pendente de contrato/taxa vigente após reprocessamento.");
          tx.setTransactionStatus(StatusTransactionEnum.PENDING.getCode());
          stillPendingContract++;
          log.warn("⚠ Pendência ERP ainda sem contrato. id={}, nsu={}, company={}, establishment={}, parcelas={}",
            tx.getId(), tx.getNsu(),
            tx.getCompany() != null ? tx.getCompany().getId() : null,
            tx.getEstablishment() != null ? tx.getEstablishment().getId() : null,
            tx.getInstallment());
        }
      } catch (Exception ex) {
        errors++;
        log.error("❌ Erro ao reprocessar pendência ERP id={}: {}", tx.getId(), ex.getMessage(), ex);
      }
    }

    log.info("✅ Reprocessamento ERP finalizado: avaliadas={}, reprocessadas={}, resolvidas={}, pendentesContrato={}, pendentesContexto={}, erros={}",
      transactions.size(), reprocessed, resolved, stillPendingContract, stillPendingBusinessContext, errors);
    return new ReprocessPendingErpResultModel(transactions.size(), reprocessed, resolved, stillPendingContract, stillPendingBusinessContext, errors);
  }

  private void applyBusinessContextPending(TransactionErpEntity tx) {
    if (tx.getCompany() == null && tx.getEstablishment() == null) {
      tx.setCommercialStatus(ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT);
    } else if (tx.getCompany() == null) {
      tx.setCommercialStatus(ErpCommercialStatusEnum.PENDING_COMPANY);
    } else {
      tx.setCommercialStatus(ErpCommercialStatusEnum.PENDING_ESTABLISHMENT);
    }
    tx.setCommercialStatusMessage("Venda ainda pendente de empresa/PV/estabelecimento após reprocessamento.");
    tx.setTransactionStatus(StatusTransactionEnum.PENDING.getCode());
  }

  private ErpPendingSaleModel toModel(TransactionErpEntity tx) {
    return new ErpPendingSaleModel(
      tx.getId(),
      tx.getLineNumber(),
      tx.getProcessedFile() != null ? tx.getProcessedFile().getFile() : null,
      tx.getSaleDate(),
      tx.getNsu(),
      tx.getAuthorization(),
      tx.getAcquirer() != null ? tx.getAcquirer().getFantasyName() : null,
      tx.getFlag() != null ? tx.getFlag().getName() : null,
      tx.getCompany() != null ? tx.getCompany().getFantasyName() : null,
      tx.getEstablishment() != null ? String.valueOf(tx.getEstablishment().getPvNumber()) : null,
      tx.getSourceCompanyCnpj(),
      tx.getSourceCompanyName(),
      tx.getSourceEstablishmentPvNumber(),
      tx.getSourceEstablishmentName(),
      tx.getInstallment(),
      tx.getGrossValue(),
      tx.getLiquidValue(),
      tx.getDiscountValue(),
      tx.getContractedFee(),
      tx.getCommercialStatus(),
      tx.getCommercialStatusMessage()
    );
  }
}
