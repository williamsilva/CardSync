package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.ConciliationWaitingAcqModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.ConciliationWaitingErpModelAssembler;
import com.cardsync.bff.controller.v1.mapper.model.ConciliationWaitingOtherDivergenceModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ConciliationWaitingOtherDivergencePair;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.ConciliationWaitingAcqSpecs;
import com.cardsync.infrastructure.repository.spec.ConciliationWaitingErpSpecs;
import com.cardsync.infrastructure.repository.spec.ConciliationWaitingOtherDivergenceSpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliationWaitingService {

  private static final String ORIGIN_ACQUIRER_GENERATED = "ACQUIRER_GENERATED";

  private final TransactionTotalsQueryService totalsQueryService;

  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionErpRepository transactionErpRepository;

  private final ConciliationWaitingAcqSpecs conciliationWaitingAcqSpecs;
  private final ConciliationWaitingErpSpecs conciliationWaitingErpSpecs;
  private final ConciliationWaitingOtherDivergenceSpecs conciliationWaitingOtherDivergenceSpecs;

  private final ConciliationWaitingAcqModelAssembler conciliationWaitingAcqModelAssembler;
  private final ConciliationWaitingErpModelAssembler conciliationWaitingErpModelAssembler;
  private final ConciliationWaitingOtherDivergenceModelAssembler conciliationWaitingOtherDivergenceModelAssembler;

  @Transactional(readOnly = true)
  public Page<ConciliationWaitingModel> missingAcquirer(Pageable pageable, ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> filterSpec = conciliationWaitingErpSpecs.fromQueryForTotals(query);
    Specification<TransactionErpEntity> dataSpec   = conciliationWaitingErpSpecs.fromQuery(query);

    long total = transactionErpRepository.count(filterSpec);

    List<ConciliationWaitingModel> content = total == 0
      ? List.of()
      : transactionErpRepository.findAll(dataSpec, pageable)
      .stream()
      .map(conciliationWaitingErpModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public Page<ConciliationWaitingModel> missingErp(Pageable pageable, ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionAcqEntity> filterSpec = conciliationWaitingAcqSpecs.fromQueryForTotals(query);
    Specification<TransactionAcqEntity> dataSpec   = conciliationWaitingAcqSpecs.fromQuery(query);

    long total = transactionAcqRepository.count(filterSpec);

    List<ConciliationWaitingModel> content = total == 0
      ? List.of()
      : transactionAcqRepository.findAll(dataSpec, pageable)
      .stream()
      .map(conciliationWaitingAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public Page<ConciliationWaitingModel> otherDivergences(Pageable pageable, ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> filterSpec = conciliationWaitingOtherDivergenceSpecs.fromQueryForTotals(query);
    Specification<TransactionErpEntity> dataSpec   = conciliationWaitingOtherDivergenceSpecs.fromQuery(query);

    long total = transactionErpRepository.count(filterSpec);

    List<ConciliationWaitingModel> content = total == 0
      ? List.of()
      : transactionErpRepository.findAll(dataSpec, pageable)
      .stream()
      .map(erp -> new ConciliationWaitingOtherDivergencePair(
        erp,
        findOtherDivergenceAcquirerCandidate(erp).orElse(null)
      ))
      .map(conciliationWaitingOtherDivergenceModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional
  public ErpAcquirerResolutionResultModel createErpFromAcquirer(UUID acquirerTransactionId) {
    TransactionAcqEntity acq = findAcq(acquirerTransactionId);

    transactionErpRepository.findFirstByTransactionAcq_Id(acq.getId())
      .ifPresent(existing -> {
        throw BusinessException.conflict(
          ErrorCode.BUSINESS_ERROR,
          "Já existe venda ERP vinculada à venda da adquirente: " + existing.getId()
        );
      });

    TransactionErpEntity erp = new TransactionErpEntity();
    copyAcquirerToErp(erp, acq, true);

    OffsetDateTime now = OffsetDateTime.now();

    erp.setOrigin(ORIGIN_ACQUIRER_GENERATED);
    erp.setCommercialStatus(ErpCommercialStatusEnum.OK);
    erp.setCommercialStatusMessage(null);
    erp.setSaleReconciliationDate(now);
    erp.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
    erp.setStatusTransactionReason(StatusTransactionReasonEnum.SCHEDULED);
    erp.setObservations(appendObservation(
      erp.getObservations(),
      "Venda ERP criada automaticamente a partir da venda da adquirente " + acq.getId()
    ));

    acq.setSaleReconciliationDate(now);
    acq.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
    acq.setStatusTransactionReason(StatusTransactionReasonEnum.SCHEDULED);

    copyAcquirerInstallmentsToErp(erp, acq);

    TransactionErpEntity saved = transactionErpRepository.save(erp);
    transactionAcqRepository.save(acq);

    log.info("✅ Venda ERP criada a partir da adquirente. erpId={}, acqId={}, nsu={}, authorization={}",
      saved.getId(), acq.getId(), acq.getNsu(), acq.getAuthorization());

    return new ErpAcquirerResolutionResultModel(
      saved.getId(), acq.getId(), "CREATE_ERP_FROM_ACQUIRER", "OK",
      "Venda ERP criada a partir da venda da adquirente e conciliada automaticamente."
    );
  }

  @Transactional
  public ErpAcquirerBatchResolutionResultModel createErpFromAcquirerBatch(List<UUID> acquirerTransactionIds) {
    List<UUID> ids = normalizeIds(acquirerTransactionIds);
    List<ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel> items = new ArrayList<>();

    for (UUID id : ids) {
      try {
        ErpAcquirerResolutionResultModel result = createErpFromAcquirer(id);
        items.add(new ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel(
          id,
          result.erpId(),
          result.acquirerId(),
          result.status(),
          result.message()
        ));
      } catch (RuntimeException ex) {
        log.warn("⚠️ Falha ao criar venda ERP em lote. acqId={}, error={}", id, ex.getMessage());
        items.add(new ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel(
          id,
          null,
          id,
          "ERROR",
          ex.getMessage()
        ));
      }
    }

    return batchResult("CREATE_ERP_FROM_ACQUIRER_BATCH", ids.size(), items);
  }

  @Transactional
  public ErpAcquirerResolutionResultModel markErpAsDeletedMissingAcquirer(UUID erpTransactionId, ErpMarkDeletedRequestModel model) {
    return markErpAsDeletedMissingAcquirer(erpTransactionId, model.reason(), model.observations());
  }

  @Transactional
  public ErpAcquirerResolutionResultModel markErpAsDeletedMissingAcquirer(
    UUID erpTransactionId,
    String reason,
    String observations
  ) {
    TransactionErpEntity erp = findErp(erpTransactionId);

    if (erp.getTransactionAcq() != null) {
      throw BusinessException.conflict(
        ErrorCode.BUSINESS_ERROR,
        "A venda ERP já possui vínculo com venda da adquirente: " + erp.getTransactionAcq().getId()
      );
    }

    OffsetDateTime now = OffsetDateTime.now();

    erp.setDeletedDate(now);
    erp.setStatusTransaction(StatusTransactionEnum.DELETED);
    erp.setStatusTransactionReason(StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ);
    erp.setReasonExclusionStatus(resolveExclusionReason(reason).getCode());
    erp.setObservations(appendObservation(
      erp.getObservations(),
      buildDeletionObservation(reason, observations)
    ));

    transactionErpRepository.save(erp);

    log.info(
      "🗑️ Venda ERP marcada como excluída por ausência na adquirente. erpId={}, nsu={}, authorization={}, reason={}",
      erp.getId(),
      erp.getNsu(),
      erp.getAuthorization(),
      reason
    );

    return new ErpAcquirerResolutionResultModel(
      erp.getId(),
      null,
      "MARK_ERP_AS_DELETED",
      "OK",
      "Venda ERP marcada como excluída por não existir na adquirente."
    );
  }

  private StatusTransactionReasonExclusionEnum resolveExclusionReason(String reason) {
    StatusTransactionReasonExclusionEnum resolved = StatusTransactionReasonExclusionEnum.fromName(reason);
    return resolved != null ? resolved : StatusTransactionReasonExclusionEnum.OTHER;
  }

  private String buildDeletionObservation(String reason, String observations) {
    String base = "Venda marcada como excluída porque não foi localizada na adquirente.";

    StringBuilder sb = new StringBuilder(base);
    if (reason != null && !reason.isBlank()) {
      sb.append(" Motivo: ").append(reason.trim()).append('.');
    }
    if (observations != null && !observations.isBlank()) {
      sb.append(' ').append(observations.trim());
    }
    return sb.toString();
  }

  @Transactional
  public ErpAcquirerResolutionResultModel updateErpIdentity(UUID erpId, ErpUpdateIdentityRequest request) {
    TransactionErpEntity erp = findErp(erpId);

    if (!Objects.equals(erp.getCapture(), CaptureEnum.MANUAL.getCode())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Edição de NSU e autorização permitida apenas para vendas com captura manual."
      );
    }

    erp.setNsu(request.nsu());
    erp.setAuthorization(request.authorization() != null ? request.authorization().trim() : null);
    transactionErpRepository.save(erp);

    log.info(
      "✏️ Identidade da venda ERP manual atualizada. erpId={}, nsu={}, authorization={}",
      erp.getId(),
      erp.getNsu(),
      erp.getAuthorization()
    );

    return new ErpAcquirerResolutionResultModel(
      erp.getId(),
      null,
      "UPDATE_ERP_IDENTITY",
      "OK",
      "NSU e autorização atualizados com sucesso."
    );
  }

  @Transactional
  public ErpAcquirerBatchResolutionResultModel markErpAsDeletedMissingAcquirerBatch(ErpAcquirerBatchRequestModel request) {
    List<UUID> ids = normalizeIds(request.transactionIds());
    List<ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel> items = new ArrayList<>();

    for (UUID id : ids) {
      try {
        ErpAcquirerResolutionResultModel result = markErpAsDeletedMissingAcquirer(id, request.reason(), request.observations());
        items.add(new ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel(
          id,
          result.erpId(),
          result.acquirerId(),
          result.status(),
          result.message()
        ));
      } catch (RuntimeException ex) {
        log.warn("⚠️ Falha ao marcar venda ERP como excluída em lote. erpId={}, error={}", id, ex.getMessage());
        items.add(new ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel(
          id,
          id,
          null,
          "ERROR",
          ex.getMessage()
        ));
      }
    }

    return batchResult("MARK_ERP_AS_DELETED_BATCH", ids.size(), items);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel missingAcquirerTotals(ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> spec = conciliationWaitingErpSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel missingErpTotals(ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionAcqEntity> spec = conciliationWaitingAcqSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionAcqEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel otherDivergencesTotals(ListQueryDto<ConciliationWaitingModelFilter> query) {
    Specification<TransactionErpEntity> spec = conciliationWaitingOtherDivergenceSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }

  private Optional<TransactionAcqEntity> findOtherDivergenceAcquirerCandidate(TransactionErpEntity erp) {
    if (erp == null || erp.getNsu() == null) {
      return Optional.empty();
    }

    StatusTransactionReasonEnum reason = statusReasonOrNull(erp.getStatusTransactionReason().getCode());
    UUID acquirerId = shouldFilterAcquirer(reason) && erp.getAcquirer() != null
      ? erp.getAcquirer().getId()
      : null;

    List<TransactionAcqEntity> candidates = transactionAcqRepository.findCandidatesForOtherDivergencePair(
      erp.getNsu(),
      blankToNull(erp.getAuthorization()),
      acquirerId,
      PageRequest.of(0, 20)
    );

    return candidates.stream()
      .max(Comparator.comparingInt(candidate -> scoreOtherDivergenceCandidate(erp, candidate, reason)));
  }

  private StatusTransactionReasonEnum statusReasonOrNull(Integer code) {
    try {
      return StatusTransactionReasonEnum.fromCode(code);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private boolean shouldFilterAcquirer(StatusTransactionReasonEnum reason) {
    return reason != StatusTransactionReasonEnum.ACQUIRER_MISMATCH
      && reason != StatusTransactionReasonEnum.AMBIGUOUS_MATCH;
  }

  private int scoreOtherDivergenceCandidate(
    TransactionErpEntity erp, TransactionAcqEntity acq, StatusTransactionReasonEnum reason) {
    if (erp == null || acq == null) {
      return 0;
    }

    int score = 0;

    if (Objects.equals(erp.getNsu(), acq.getNsu())) {
      score += 100;
    }

    if (normalizedEquals(erp.getAuthorization(), acq.getAuthorization())) {
      score += 100;
    }

    if (sameEntityId(erp.getAcquirer(), acq.getAcquirer())) {
      score += reason == StatusTransactionReasonEnum.ACQUIRER_MISMATCH ? 5 : 80;
    }

    if (sameSaleDay(erp, acq)) {
      score += 30;
    }

    if (sameMoney(erp.getGrossValue(), acq.getGrossValue())) {
      score += reason == StatusTransactionReasonEnum.VALUE_MISMATCH ? 5 : 25;
    }

    if (sameEntityId(erp.getFlag(), acq.getFlag())) {
      score += reason == StatusTransactionReasonEnum.FLAG_MISMATCH ? 5 : 20;
    }

    if (Objects.equals(erp.getInstallment(), acq.getInstallment())) {
      score += reason == StatusTransactionReasonEnum.DIFFERENT_PLANS ? 5 : 15;
    }

    return score;
  }

  private boolean sameSaleDay(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (erp.getSaleDate() == null || acq.getSaleDate() == null) {
      return false;
    }
    return erp.getSaleDate().toLocalDate().equals(acq.getSaleDate().toLocalDate());
  }

  private boolean sameMoney(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
      return false;
    }
    return left.compareTo(right) == 0;
  }

  private boolean normalizedEquals(String left, String right) {
    String a = blankToNull(left);
    String b = blankToNull(right);
    if (a == null || b == null) {
      return false;
    }
    return a.equalsIgnoreCase(b);
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private boolean sameEntityId(AuditableEntityBase left, AuditableEntityBase right) {
    if (left == null || right == null) {
      return false;
    }
    return Objects.equals(left.getId(), right.getId());
  }

  private TransactionAcqEntity findAcq(UUID id) {
    return transactionAcqRepository.findForManualResolutionById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Venda da adquirente não encontrada: " + id
      ));
  }

  private TransactionErpEntity findErp(UUID id) {
    return transactionErpRepository.findForManualResolutionById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Venda ERP não encontrada: " + id
      ));
  }

  private List<UUID> normalizeIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Informe ao menos uma venda para processar."
      );
    }

    return ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  private ErpAcquirerBatchResolutionResultModel batchResult(
    String action, int requested, List<ErpAcquirerBatchResolutionResultModel.ErpAcquirerBatchResolutionItemModel> items
  ) {
    int success = (int) items.stream()
      .filter(item -> "OK".equalsIgnoreCase(item.status()))
      .count();

    int failed = items.size() - success;

    return new ErpAcquirerBatchResolutionResultModel(
      action,
      requested,
      success,
      failed,
      items
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

  private void applyAcquirerSourceContext(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq.getCompany() != null) {
      erp.setSourceCompanyCnpj(acq.getCompany().getCnpj());
      erp.setSourceCompanyName(acq.getCompany().getFantasyName());
    }

    if (acq.getEstablishment() != null) {
      erp.setSourceEstablishmentPvNumber(acq.getEstablishment().getPvNumber());
    }
  }

  private void copyAcquirerInstallmentsToErp(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq.getInstallments() == null || acq.getInstallments().isEmpty()) {
      return;
    }

    acq.getInstallments().stream()
      .sorted(Comparator.comparing(InstallmentAcqEntity::getInstallment, Comparator.nullsLast(Integer::compareTo)))
      .forEach(acqInstallment -> {
        InstallmentErpEntity erpInstallment = new InstallmentErpEntity();

        erpInstallment.setGrossValue(acqInstallment.getGrossValue());
        erpInstallment.setLiquidValue(acqInstallment.getLiquidValue());
        erpInstallment.setDiscountValue(acqInstallment.getDiscountValue());
        erpInstallment.setInstallment(acqInstallment.getInstallment());
        erpInstallment.setStatusPaymentBank(acqInstallment.getStatusPaymentBank());
        erpInstallment.setInstallmentStatus(acqInstallment.getInstallmentStatus());
        erpInstallment.setReconciliationBankLine(acqInstallment.getReconciliationBankLine());
        erpInstallment.setPaymentDate(acqInstallment.getPaymentDate());
        erpInstallment.setCancellationDate(acqInstallment.getCancellationDate());
        erpInstallment.setExpectedPaymentDate(acqInstallment.getExpectedPaymentDate());
        erpInstallment.setReconciliationBankProcessedAt(acqInstallment.getReconciliationBankProcessedAt());
        erpInstallment.setReconciliationBankFile(acqInstallment.getReconciliationBankFile());

        erp.addInstallment(erpInstallment);
      });
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