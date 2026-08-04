package com.cardsync.core.conciliation.analysis;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Cobre a esteira de conciliação ERP x Adquirente (Etapa 1) generalizada para múltiplas
 * adquirentes (ver [[project_reconciliation_pipeline_prerequisite_chain]] no plano de
 * generalização): NSU é um contador sequencial por terminal/POS que pode colidir entre
 * adquirentes diferentes (ex.: Rede e Cielo usando o mesmo NSU numérico em terminais
 * distintos). O guard sameAcquirerForReconciliation existe exatamente para esse cenário —
 * sem ele, ampliar a busca de candidatas para todas as adquirentes ativas arriscaria casar
 * uma venda ERP com a venda ADQ errada, de outra adquirente, só por coincidência de
 * NSU/autorização/valor/data.
 */
class ConciliationAnalysisServiceAcquirerScopeTest {

  private final ReconciliationSettingsService reconciliationSettingsService = mock(ReconciliationSettingsService.class);

  private final ConciliationAnalysisService service = new ConciliationAnalysisService(
    null, null, null, null, null, null, reconciliationSettingsService, null, null, null
  );

  @Test
  void matchesCandidateFromSameAcquirerEvenWhenAnotherAcquirerSharesIdentity() {
    AcquirerEntity acquirerRede = withId(new AcquirerEntity());
    AcquirerEntity acquirerCielo = withId(new AcquirerEntity());

    TransactionErpEntity erp = erpSale(acquirerRede, 100L, "AUTH1", new BigDecimal("50.00"));

    TransactionAcqEntity acqSameAcquirer = acqSale(acquirerRede, 100L, "AUTH1", new BigDecimal("50.00"));
    TransactionAcqEntity acqOtherAcquirer = acqSale(acquirerCielo, 100L, "AUTH1", new BigDecimal("50.00"));

    ConciliationAnalysisService.ErpAcquirerMatchResult result = service.findBestAcquirerMatchForReconciliation(
      erp, List.of(acqSameAcquirer, acqOtherAcquirer), false
    );

    assertThat(result.status()).isEqualTo(ConciliationAnalysisService.ErpAcquirerMatchStatus.MATCHED);
    assertThat(result.acquirerSale()).isSameAs(acqSameAcquirer);
  }

  @Test
  void returnsAcquirerDivergenceWhenOnlyIdentityMatchIsFromADifferentAcquirer() {
    AcquirerEntity acquirerRede = withId(new AcquirerEntity());
    AcquirerEntity acquirerCielo = withId(new AcquirerEntity());

    TransactionErpEntity erp = erpSale(acquirerRede, 100L, "AUTH1", new BigDecimal("50.00"));
    TransactionAcqEntity acqOtherAcquirer = acqSale(acquirerCielo, 100L, "AUTH1", new BigDecimal("50.00"));

    ConciliationAnalysisService.ErpAcquirerMatchResult result = service.findBestAcquirerMatchForReconciliation(
      erp, List.of(acqOtherAcquirer), false
    );

    assertThat(result.status()).isEqualTo(ConciliationAnalysisService.ErpAcquirerMatchStatus.ACQUIRER_DIVERGENCE);
    assertThat(result.acquirerSale()).isNull();
  }

  private TransactionErpEntity erpSale(AcquirerEntity acquirer, Long nsu, String authorization, BigDecimal grossValue) {
    TransactionErpEntity erp = withId(new TransactionErpEntity());
    erp.setAcquirer(acquirer);
    erp.setNsu(nsu);
    erp.setAuthorization(authorization);
    erp.setGrossValue(grossValue);
    erp.setSaleDate(OffsetDateTime.now());
    return erp;
  }

  private TransactionAcqEntity acqSale(AcquirerEntity acquirer, Long nsu, String authorization, BigDecimal grossValue) {
    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setAcquirer(acquirer);
    acq.setNsu(nsu);
    acq.setAuthorization(authorization);
    acq.setGrossValue(grossValue);
    acq.setSaleDate(OffsetDateTime.now());
    return acq;
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
