package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpAcquirerComparisonModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpAcquirerFieldDiffModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpAcquirerResolutionResultModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpAcquirerTruthSource;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpAcquirerResolutionService {

  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;

  @Transactional(readOnly = true)
  public ErpAcquirerComparisonModel compare(UUID erpTransactionId, UUID acquirerTransactionId) {
    TransactionErpEntity erp = findErp(erpTransactionId);
    TransactionAcqEntity acq = findAcq(acquirerTransactionId);

    List<ErpAcquirerFieldDiffModel> fields = new ArrayList<>();

    fields.add(diff("saleDate", value(erp.getSaleDate()), value(acq.getSaleDate())));
    fields.add(diff("nsu", value(erp.getNsu()), value(acq.getNsu())));
    fields.add(diff("authorization", value(erp.getAuthorization()), value(acq.getAuthorization())));
    fields.add(diff("tid", value(erp.getTid()), value(acq.getTid())));
    fields.add(diff("grossValue", money(erp.getGrossValue()), money(acq.getGrossValue())));
    fields.add(diff("liquidValue", money(erp.getLiquidValue()), money(acq.getLiquidValue())));
    fields.add(diff("discountValue", money(erp.getDiscountValue()), money(acq.getDiscountValue())));
    fields.add(diff("acquirer", label(erp.getAcquirer()), label(acq.getAcquirer())));
    fields.add(diff("company", label(erp.getCompany()), label(acq.getCompany())));
    fields.add(diff("establishment", label(erp.getEstablishment()), label(acq.getEstablishment())));
    fields.add(diff("flag", label(erp.getFlag()), label(acq.getFlag())));
    fields.add(diff("modality", value(erp.getModality()), value(acq.getModality())));
    fields.add(diff("installment", value(erp.getInstallment()), value(acq.getInstallment())));

    boolean hasDivergence = fields.stream().anyMatch(ErpAcquirerFieldDiffModel::different);

    return new ErpAcquirerComparisonModel(
      erp.getId(),
      acq.getId(),
      hasDivergence,
      fields
    );
  }

  @Transactional
  public ErpAcquirerResolutionResultModel reconcileManually(
    UUID erpTransactionId,
    UUID acquirerTransactionId,
    ErpAcquirerTruthSource truthSource
  ) {
    TransactionErpEntity erp = findErp(erpTransactionId);
    TransactionAcqEntity acq = findAcq(acquirerTransactionId);

    if (truthSource == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Fonte da verdade deve ser informada: ERP ou ACQUIRER."
      );
    }

    if (truthSource == ErpAcquirerTruthSource.ACQUIRER) {
      copyAcquirerToErp(erp, acq, false);
      erp.setObservations(appendObservation(
        erp.getObservations(),
        "Conciliação manual usando a adquirente como fonte da verdade."
      ));
    } else {
      applyAcquirerBusinessContextOnly(erp, acq);
      erp.setObservations(appendObservation(
        erp.getObservations(),
        "Conciliação manual usando o ERP como fonte da verdade."
      ));
    }

    OffsetDateTime now = OffsetDateTime.now();

    erp.setTransactionAcq(acq);
    erp.setSaleReconciliationDate(now);
    erp.setStatusTransaction(StatusTransactionEnum.MANUALLY_RECONCILED.getCode());
    erp.setStatusTransactionReason(StatusTransactionReasonEnum.SCHEDULED.getCode());

    acq.setSaleReconciliationDate(now);
    acq.setStatusTransaction(StatusTransactionEnum.MANUALLY_RECONCILED.getCode());
    acq.setStatusTransactionReason(StatusTransactionReasonEnum.SCHEDULED.getCode());

    erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
    erp.setCommercialStatusMessage(null);

    transactionErpRepository.save(erp);
    transactionAcqRepository.save(acq);

    log.info(
      "🤝 Venda ERP x adquirente conciliada manualmente. erpId={}, acqId={}, truthSource={}",
      erp.getId(),
      acq.getId(),
      truthSource
    );

    return new ErpAcquirerResolutionResultModel(
      erp.getId(),
      acq.getId(),
      "MANUAL_RECONCILIATION",
      "OK",
      "Venda conciliada manualmente usando " + truthSource + " como fonte da verdade."
    );
  }

  private void copyAcquirerToErp(TransactionErpEntity erp, TransactionAcqEntity acq, boolean newEntity) {
    erp.setTransactionAcq(acq);

    erp.setNsu(acq.getNsu());
    erp.setTid(acq.getTid());
    erp.setMachine(acq.getMachine());
    erp.setCardNumber(acq.getCardNumber());
    erp.setAuthorization(acq.getAuthorization());
    erp.setTransactionType(acq.getTransactionType());

    erp.setCapture(acq.getCapture());
    erp.setModality(acq.getModality());
    erp.setInstallment(acq.getInstallment());

    if (newEntity) {
      erp.setLineNumber(acq.getLineNumber());
    }

    erp.setSaleDate(acq.getSaleDate());
    erp.setCanceledDate(acq.getCanceledDate());

    erp.setGrossValue(acq.getGrossValue());
    erp.setLiquidValue(acq.getLiquidValue());
    erp.setDiscountValue(acq.getDiscountValue());

    erp.setAcquirer(acq.getAcquirer());
    erp.setFlag(acq.getFlag());
    erp.setCompany(acq.getCompany());
    erp.setEstablishment(acq.getEstablishment());
    erp.setAdjustment(acq.getAdjustment());
    erp.setBankingDomicile(resolveBankingDomicile(acq));

    applyAcquirerSourceContext(erp, acq);
  }

  private void applyAcquirerBusinessContextOnly(TransactionErpEntity erp, TransactionAcqEntity acq) {
    erp.setTransactionAcq(acq);

    if (acq.getAcquirer() != null) {
      erp.setAcquirer(acq.getAcquirer());
    }

    if (acq.getFlag() != null) {
      erp.setFlag(acq.getFlag());
    }

    if (acq.getCompany() != null) {
      erp.setCompany(acq.getCompany());
    }

    if (acq.getEstablishment() != null) {
      erp.setEstablishment(acq.getEstablishment());
    }

    if (acq.getAdjustment() != null) {
      erp.setAdjustment(acq.getAdjustment());
    }

    BankingDomicileEntity bankingDomicile = resolveBankingDomicile(acq);
    if (bankingDomicile != null) {
      erp.setBankingDomicile(bankingDomicile);
    }

    applyAcquirerSourceContext(erp, acq);
  }

  private void applyAcquirerSourceContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq.getCompany() != null) {
      erp.setSourceCompanyCnpj(acq.getCompany().getCnpj());
      erp.setSourceCompanyName(acq.getCompany().getFantasyName());
    }

    if (acq.getEstablishment() != null) {
      erp.setSourceEstablishmentPvNumber(acq.getEstablishment().getPvNumber());
    }
  }

  private BankingDomicileEntity resolveBankingDomicile(TransactionAcqEntity acq) {
    if (acq == null) {
      return null;
    }

    SalesSummaryEntity salesSummary = acq.getSalesSummary();
    if (salesSummary == null) {
      return null;
    }

    return salesSummary.getBankingDomicile();
  }

  private TransactionErpEntity findErp(UUID id) {
    return transactionErpRepository.findForManualResolutionById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Venda ERP não encontrada: " + id
      ));
  }

  private TransactionAcqEntity findAcq(UUID id) {
    return transactionAcqRepository.findForManualResolutionById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Venda da adquirente não encontrada: " + id
      ));
  }

  private ErpAcquirerFieldDiffModel diff(String field, String erpValue, String acquirerValue) {
    boolean different = !Objects.equals(normalize(erpValue), normalize(acquirerValue));
    return new ErpAcquirerFieldDiffModel(field, erpValue, acquirerValue, different);
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim();

    if (normalized.matches("\\d+")) {
      normalized = normalized.replaceFirst("^0+(?!$)", "");
    }

    return normalized;
  }

  private String value(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String money(BigDecimal value) {
    return value != null ? value.stripTrailingZeros().toPlainString() : null;
  }

  private String label(AcquirerEntity entity) {
    return entity != null ? entity.getFantasyName() : null;
  }

  private String label(CompanyEntity entity) {
    return entity != null ?  entity.getFantasyName() : null;
  }

  private String label(EstablishmentEntity entity) {
    return entity != null ? entity.getAcquirer().getFantasyName() + " - " + entity.getPvNumber() : null;
  }

  private String label(FlagEntity entity) {
    return entity != null ? entity.getName() : null;
  }

  @SuppressWarnings("unused")
  private String id(AuditableEntityBase entity) {
    return entity != null && entity.getId() != null ? String.valueOf(entity.getId()) : null;
  }

  private String appendObservation(String current, String message) {
    if (message == null || message.isBlank()) {
      return current;
    }

    if (current == null || current.isBlank()) {
      return message;
    }

    return current + " | " + message;
  }
}
