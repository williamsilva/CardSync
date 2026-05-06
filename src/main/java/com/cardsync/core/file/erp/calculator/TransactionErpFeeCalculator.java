package com.cardsync.core.file.erp.calculator;

import com.cardsync.core.file.erp.contract.ContractedErpRateLookupService;
import com.cardsync.domain.model.TransactionErpEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionErpFeeCalculator {

  private final ContractedErpRateLookupService contractedErpRateLookupService;

  public FeeCalculationResult calculateContractedFee(TransactionErpEntity tx) {
    /*
     * Quando o ERP/TEF não informa empresa/PV/estabelecimento, a ausência de contrato
     * é consequência esperada da pendência comercial. Evitamos lookup inútil e WARN por linha.
     * A transação fica importada com taxa zero provisória e será recalculada no reprocessamento
     * quando o contexto comercial for resolvido.
     */
    if (hasIncompleteBusinessContext(tx)) {
      applyZeroContractedFee(tx);
      log.debug(
        "ERP contrato não consultado por contexto comercial incompleto. nsu={}, adquirente={}, bandeira={}, modalidade={}, dataVenda={}",
        tx.getNsu(),
        tx.getAcquirer() != null ? tx.getAcquirer().getFantasyName() : null,
        tx.getFlag() != null ? tx.getFlag().getName() : null,
        tx.getModality(),
        tx.getSaleDate()
      );
      return new FeeCalculationResult(BigDecimal.ZERO, null, false);
    }

    var contractedRate = contractedErpRateLookupService.findRate(tx);
    BigDecimal contractedFee = contractedRate.map(rate -> rate.rate()).orElse(BigDecimal.ZERO);
    Integer paymentTermDays = contractedRate.map(rate -> rate.paymentTermDays()).orElse(null);

    tx.setContractedFee(contractedFee);
    tx.setDiscountValue(FinancialCalculator.calculateDiscountValue(tx.getGrossValue(), contractedFee));
    tx.setLiquidValue(FinancialCalculator.calculateNetValue(tx.getGrossValue(), contractedFee));

    if (contractedRate.isEmpty()) {
      log.debug(
        "Nenhum contrato vigente encontrado para ERP. nsu={}, adquirente={}, bandeira={}, modalidade={}, dataVenda={}. Taxa aplicada=0.",
        tx.getNsu(),
        tx.getAcquirer() != null ? tx.getAcquirer().getFantasyName() : null,
        tx.getFlag() != null ? tx.getFlag().getName() : null,
        tx.getModality(),
        tx.getSaleDate()
      );
    }

    return new FeeCalculationResult(contractedFee, paymentTermDays, contractedRate.isPresent());
  }

  private boolean hasIncompleteBusinessContext(TransactionErpEntity tx) {
    return tx.getCompany() == null || tx.getEstablishment() == null;
  }

  private void applyZeroContractedFee(TransactionErpEntity tx) {
    BigDecimal contractedFee = BigDecimal.ZERO;
    tx.setContractedFee(contractedFee);
    tx.setDiscountValue(FinancialCalculator.calculateDiscountValue(tx.getGrossValue(), contractedFee));
    tx.setLiquidValue(FinancialCalculator.calculateNetValue(tx.getGrossValue(), contractedFee));
  }

  public record FeeCalculationResult(BigDecimal contractedFee, Integer paymentTermDays, boolean contractFound) {
  }
}
