package com.cardsync.core.conciliation.analysis;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractRateEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.FeeReconciliationStatusEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o achado de análise profunda da Etapa 4 (2026-09-12): {@code VALUE_TOLERANCE} era uma
 * constante fixa (0.05) dentro da classe, em vez de usar {@link ReconciliationSettingsService#getValueTolerance()}
 * - a mesma tolerância de valor configurável que as Etapas 1, 3, 6 e 7 já respeitam. Isso fazia com
 * que alterar a tolerância na tela de configurações não tivesse nenhum efeito nesta etapa. Os dois
 * primeiros testes usam exatamente os mesmos dados de venda, variando só a tolerância configurada,
 * para provar que o resultado agora muda de acordo com a configuração.
 */
class ConciliationFeeAnalysisServiceTest {

  private final ContractAuditWriterService contractAuditWriterService = mock(ContractAuditWriterService.class);
  private final ContractedAcquirerRateLookupService contractedAcquirerRateLookupService =
    mock(ContractedAcquirerRateLookupService.class);
  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);

  private final ConciliationFeeAnalysisService service = new ConciliationFeeAnalysisService(
    contractAuditWriterService,
    contractedAcquirerRateLookupService,
    reconciliationSettingsService
  );

  @Test
  void flagsDivergentRateWhenDifferenceExceedsConfiguredValueTolerance() {
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));

    TransactionErpEntity erp = matchedSalePair(new BigDecimal("2.00"));
    TransactionAcqEntity acq = erp.getTransactionAcq();
    stubContractRate(acq, new BigDecimal("2.00"));

    ConciliationFeeAnalysisService.FeeReconciliationResult result = service.reconcileMatchedSale(erp, acq);

    assertThat(result.divergentRate()).isTrue();
    assertThat(erp.getFeeReconciliationStatus()).isEqualTo(FeeReconciliationStatusEnum.DIVERGENT_RATE);
    assertThat(acq.getFeeReconciliationStatus()).isEqualTo(FeeReconciliationStatusEnum.DIVERGENT_RATE);
    verify(contractAuditWriterService, times(1)).saveOrUpdate(any());
  }

  @Test
  void reconcilesSameSaleWhenConfiguredValueToleranceIsRaised() {
    // Mesmíssima venda do teste acima (diferença de R$10,00 entre taxa cobrada e contratada),
    // mas com a tolerância configurada bem acima da diferença - antes da correção, a constante
    // hardcoded (0.05) ignorava esse ajuste e a venda continuaria DIVERGENT_RATE.
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("20.00"));

    TransactionErpEntity erp = matchedSalePair(new BigDecimal("2.00"));
    TransactionAcqEntity acq = erp.getTransactionAcq();
    stubContractRate(acq, new BigDecimal("2.00"));

    ConciliationFeeAnalysisService.FeeReconciliationResult result = service.reconcileMatchedSale(erp, acq);

    assertThat(result.divergentRate()).isFalse();
    assertThat(result.missingValidContract()).isFalse();
    assertThat(erp.getFeeReconciliationStatus()).isEqualTo(FeeReconciliationStatusEnum.RECONCILED);
    assertThat(acq.getFeeReconciliationStatus()).isEqualTo(FeeReconciliationStatusEnum.RECONCILED);
    verify(contractAuditWriterService, times(1)).deleteByTransactionAcqId(acq.getId());
  }

  @Test
  void appliesAcquirerRateAndFlagsMissingContractWhenNoContractIsFound() {
    when(reconciliationSettingsService.getValueTolerance()).thenReturn(new BigDecimal("0.05"));

    TransactionErpEntity erp = matchedSalePair(new BigDecimal("2.00"));
    TransactionAcqEntity acq = erp.getTransactionAcq();
    when(contractedAcquirerRateLookupService.findContractCandidates(any(), any(), any(), any(), any()))
      .thenReturn(List.of());
    when(contractedAcquirerRateLookupService.findRateFromCandidates(anyList(), eq(acq)))
      .thenReturn(Optional.empty());

    ConciliationFeeAnalysisService.FeeReconciliationResult result = service.reconcileMatchedSale(erp, acq);

    assertThat(result.missingValidContract()).isTrue();
    assertThat(result.divergentRate()).isFalse();
    assertThat(erp.getFeeReconciliationStatus()).isEqualTo(FeeReconciliationStatusEnum.MISSING_VALID_CONTRACT);
    assertThat(erp.getMissingContractAtSale()).isTrue();
    // Sem contrato, o ERP é normalizado pela taxa REAL da adquirente (3.00%), não pela contratada.
    assertThat(erp.getContractedFee()).isEqualByComparingTo(new BigDecimal("3.00"));
    verify(contractAuditWriterService, times(1)).deleteByTransactionAcqId(acq.getId());
  }

  private TransactionErpEntity matchedSalePair(BigDecimal ignoredContractRatePlaceholder) {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setGrossValue(new BigDecimal("1000.00"));
    acq.setMdrRate(new BigDecimal("3.00"));
    acq.setDiscountValue(new BigDecimal("30.00"));
    acq.setLiquidValue(new BigDecimal("970.00"));
    acq.setAcquirer(withId(new AcquirerEntity()));
    acq.setFlag(withId(new FlagEntity()));
    acq.setModality(1);
    acq.setSaleDate(OffsetDateTime.now());

    TransactionErpEntity erp = withId(new TransactionErpEntity());
    erp.setGrossValue(new BigDecimal("1000.00"));
    erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
    erp.setTransactionAcq(acq);

    return erp;
  }

  private void stubContractRate(TransactionAcqEntity acq, BigDecimal rate) {
    ContractEntity contract = withId(new ContractEntity());
    ContractRateEntity contractRate = new ContractRateEntity();
    ContractedAcquirerRate contractedRate = new ContractedAcquirerRate(contract, contractRate, rate, 30, false);

    when(contractedAcquirerRateLookupService.findContractCandidates(any(), any(), any(), any(), any()))
      .thenReturn(List.of(contract));
    when(contractedAcquirerRateLookupService.findRateFromCandidates(anyList(), eq(acq)))
      .thenReturn(Optional.of(contractedRate));
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
