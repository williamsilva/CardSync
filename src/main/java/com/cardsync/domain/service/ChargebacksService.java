package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackAnalysisModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackAnalysisTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackLifecycleModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ChargebackTimelineEventModel;
import com.cardsync.core.conciliation.analysis.ConciliationDebitChargebackClassifier;
import com.cardsync.domain.filter.ChargebackAnalysisFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ChargebackAnalysisStatus;
import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.domain.model.enums.ChargebackEventSourceType;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.InstallmentUnschedulingRepository;
import com.cardsync.domain.repository.PendingDebtRepository;
import com.cardsync.domain.repository.RequestNoticeRepository;
import com.cardsync.domain.repository.SettledDebtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebacksService {

  private final AdjustmentRepository adjustmentRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final SettledDebtRepository settledDebtRepository;
  private final RequestNoticeRepository redeRequestNoticeRepository;
  private final ConciliationDebitChargebackClassifier debitChargebackClassifier;
  private final InstallmentUnschedulingRepository installmentUnschedulingRepository;

  @Transactional(readOnly = true)
  public Page<ChargebackAnalysisModel> listChargebacks(Pageable pageable, ListQueryDto<ChargebackAnalysisFilter> body) {
    return page(buildChargebackItems(body), pageable);
  }

  @Transactional(readOnly = true)
  public Page<ChargebackLifecycleModel> listChargebackLifecycles(Pageable pageable, ListQueryDto<ChargebackAnalysisFilter> body) {
    return page(buildChargebackLifecycles(body), pageable);
  }

  @Transactional(readOnly = true)
  public ChargebackAnalysisTotalsModel chargebacksTotals(ListQueryDto<ChargebackAnalysisFilter> body) {
    List<ChargebackLifecycleModel> lifecycles = buildChargebackLifecycles(body);

    return new ChargebackAnalysisTotalsModel(
      lifecycles.size(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.REQUEST_RECEIVED).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.DOCUMENTATION_DUE).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.DOCUMENTATION_OVERDUE).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.PENDING_DEBIT).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.BANK_DEBIT_SCHEDULED).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.NET_COMPENSATION_SCHEDULED).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.DESCHEDULED).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.LIQUIDATED
        || item.getCurrentStatus() == ChargebackAnalysisStatus.LOST).count(),
      lifecycles.stream().filter(item -> item.getCurrentStatus() == ChargebackAnalysisStatus.REVERSED
        || item.getCurrentStatus() == ChargebackAnalysisStatus.WON).count(),
      sum(lifecycles.stream().map(ChargebackLifecycleModel::getSaleValue)),
      sum(lifecycles.stream().map(ChargebackLifecycleModel::getDisputedValue)),
      sum(lifecycles.stream().map(ChargebackLifecycleModel::getPendingValue)),
      sum(lifecycles.stream().map(ChargebackLifecycleModel::getSettledValue)),
      sum(lifecycles.stream().map(ChargebackLifecycleModel::getCompensatedValue))
    );
  }

  private List<ChargebackAnalysisModel> buildChargebackItems(ListQueryDto<ChargebackAnalysisFilter> body) {
    ChargebackAnalysisFilter filter = body != null ? body.advanced() : null;
    String global = firstNonBlank(body != null ? body.globalFilter() : null, filter != null ? filter.global() : null);

    List<ChargebackAnalysisModel> items = new ArrayList<>();
    redeRequestNoticeRepository.findAll().stream().map(this::toRequestChargebackModel).forEach(items::add);
    pendingDebtRepository.findAll().stream().filter(debitChargebackClassifier::isChargeback).map(this::toOpenChargebackModel).forEach(items::add);
    settledDebtRepository.findAll().stream().filter(debitChargebackClassifier::isChargeback).map(this::toSettledChargebackModel).forEach(items::add);
    installmentUnschedulingRepository.findAll().stream().filter(debitChargebackClassifier::isChargeback).map(this::toUnschedulingChargebackModel).forEach(items::add);
    adjustmentRepository.findAll().stream()
      .filter(debitChargebackClassifier::isChargeback)
      .map(this::toAdjustmentChargebackModel)
      .forEach(items::add);

    return items.stream()
      .filter(item -> matchesChargebackFilter(item, filter, global))
      .sorted(Comparator
        .comparing(ChargebackAnalysisModel::getLastEventDate, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(item -> item.getStatus() == null ? null : item.getStatus().name(), Comparator.nullsLast(String::compareTo)))
      .toList();
  }

  private List<ChargebackLifecycleModel> buildChargebackLifecycles(ListQueryDto<ChargebackAnalysisFilter> body) {
    Map<String, List<ChargebackAnalysisModel>> grouped = new LinkedHashMap<>();

    for (ChargebackAnalysisModel item : buildChargebackItems(body)) {
      grouped.computeIfAbsent(transactionTrackingKey(item), ignored -> new ArrayList<>()).add(item);
    }

    return grouped.entrySet().stream()
      .map(entry -> toLifecycle(entry.getKey(), entry.getValue()))
      .sorted(Comparator
        .comparing(ChargebackLifecycleModel::getLastEventDate, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ChargebackLifecycleModel::getTrackingKey, Comparator.nullsLast(String::compareTo)))
      .toList();
  }

  private ChargebackLifecycleModel toLifecycle(String trackingKey, List<ChargebackAnalysisModel> events) {
    List<ChargebackAnalysisModel> sortedEvents = events.stream()
      .sorted(Comparator
        .comparing(this::timelineDate, Comparator.nullsLast(LocalDate::compareTo))
        .thenComparing(item -> statusRank(item.getStatus())))
      .toList();

    ChargebackAnalysisModel representative = sortedEvents.stream()
      .max(Comparator
        .comparing((ChargebackAnalysisModel item) -> statusRank(item.getStatus()))
        .thenComparing(this::timelineDate, Comparator.nullsLast(LocalDate::compareTo)))
      .orElseGet(() -> events.get(0));

    List<ChargebackTimelineEventModel> timeline = sortedEvents.stream()
      .flatMap(item -> timelineEvents(item).stream())
      .sorted(Comparator
        .comparing(ChargebackTimelineEventModel::getEventDate, Comparator.nullsLast(LocalDate::compareTo))
        .thenComparing(event -> statusRank(event.getStatus())))
      .toList();

    return ChargebackLifecycleModel.builder()
      .trackingKey(trackingKey)
      .currentStatus(currentStatus(timeline, representative))
      .firstEventDate(timeline.stream().map(ChargebackTimelineEventModel::getEventDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null))
      .lastEventDate(timeline.stream().map(ChargebackTimelineEventModel::getEventDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null))
      .company(firstFrom(sortedEvents, ChargebackAnalysisModel::getCompany))
      .establishment(firstFrom(sortedEvents, ChargebackAnalysisModel::getEstablishment))
      .acquirer(firstFrom(sortedEvents, ChargebackAnalysisModel::getAcquirer))
      .flag(firstFrom(sortedEvents, ChargebackAnalysisModel::getFlag))
      .pvNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getPvNumber))
      .originalPvNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getOriginalPvNumber))
      .rvNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getRvNumber))
      .originalRvNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getOriginalRvNumber))
      .nsu(firstFrom(sortedEvents, ChargebackAnalysisModel::getNsu))
      .authorization(firstFrom(sortedEvents, ChargebackAnalysisModel::getAuthorization))
      .tid(firstFrom(sortedEvents, ChargebackAnalysisModel::getTid))
      .orderNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getOrderNumber))
      .processNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getProcessNumber))
      .debitOrderNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getDebitOrderNumber))
      .retentionProcessNumber(firstFrom(sortedEvents, ChargebackAnalysisModel::getRetentionProcessNumber))
      .saleValue(firstNonZeroOrFirst(sortedEvents, ChargebackAnalysisModel::getSaleValue))
      .disputedValue(maxValue(sortedEvents.stream().map(ChargebackAnalysisModel::getDisputedValue)))
      .pendingValue(sumNonNull(sortedEvents.stream().map(ChargebackAnalysisModel::getPendingValue)))
      .settledValue(sumNonNull(sortedEvents.stream().map(ChargebackAnalysisModel::getSettledValue)))
      .compensatedValue(sumNonNull(sortedEvents.stream().map(ChargebackAnalysisModel::getCompensatedValue)))
      .reasonCode(firstFrom(sortedEvents, ChargebackAnalysisModel::getReasonCode))
      .reasonDescription(firstFrom(sortedEvents, ChargebackAnalysisModel::getReasonDescription))
      .requestedDocuments(firstFrom(sortedEvents, ChargebackAnalysisModel::getRequestedDocuments))
      .compensationCode(firstFrom(sortedEvents, ChargebackAnalysisModel::getCompensationCode))
      .compensationDescription(firstFrom(sortedEvents, ChargebackAnalysisModel::getCompensationDescription))
      .timeline(timeline)
      .build();
  }


  private ChargebackAnalysisStatus currentStatus(List<ChargebackTimelineEventModel> timeline, ChargebackAnalysisModel fallback) {
    return timeline.stream()
      .max(Comparator
        .comparing((ChargebackTimelineEventModel event) -> statusRank(event.getStatus()))
        .thenComparing(ChargebackTimelineEventModel::getEventDate, Comparator.nullsLast(LocalDate::compareTo)))
      .map(ChargebackTimelineEventModel::getStatus)
      .orElse(fallback != null ? fallback.getStatus() : ChargebackAnalysisStatus.UNDER_REVIEW);
  }

  private List<ChargebackTimelineEventModel> timelineEvents(ChargebackAnalysisModel item) {
    List<ChargebackTimelineEventModel> events = new ArrayList<>();

    events.add(ChargebackTimelineEventModel.builder()
      .id(item.getId())
      .status(item.getStatus())
      .sourceType(item.getSourceType())
      .eventDate(timelineDate(item))
      .title(eventTitle(item))
      .description(eventDescription(item))
      .amount(firstNonNull(item.getDisputedValue(), item.getSaleValue()))
      .pendingValue(item.getPendingValue())
      .settledValue(item.getSettledValue())
      .compensatedValue(item.getCompensatedValue())
      .processNumber(item.getProcessNumber())
      .debitOrderNumber(item.getDebitOrderNumber())
      .processedFile(item.getProcessedFile())
      .build());

    if (item.getDocumentationDueDate() != null && isRequest(item)) {
      ChargebackAnalysisStatus deadlineStatus = item.getDocumentationDueDate().isBefore(LocalDate.now())
        ? ChargebackAnalysisStatus.DOCUMENTATION_OVERDUE
        : ChargebackAnalysisStatus.DOCUMENTATION_DUE;

      events.add(ChargebackTimelineEventModel.builder()
        .id(item.getId())
        .status(deadlineStatus)
        .sourceType(item.getSourceType())
        .eventDate(item.getDocumentationDueDate())
        .title(deadlineStatus == ChargebackAnalysisStatus.DOCUMENTATION_OVERDUE ? "Prazo de documentação vencido" : "Prazo de documentação")
        .description(item.getRequestedDocuments())
        .amount(item.getDisputedValue())
        .processNumber(item.getProcessNumber())
        .processedFile(item.getProcessedFile())
        .build());
    }

    return events;
  }

  private ChargebackAnalysisModel toRequestChargebackModel(RequestNoticeEntity entity) {
    return ChargebackAnalysisModel.builder()
      .id(entity.getId())
      .saleDate(entity.getSaleDate())
      .disputeDate(entity.getSaleDate())
      .requestDate(entity.getSaleDate())
      .documentationDueDate(entity.getDeadline())
      .dueDate(entity.getDeadline())
      .lastEventDate(firstNonNull(entity.getDeadline(), entity.getSaleDate()))
      .company(companyName(entity.getCompany()))
      .establishment(establishmentName(entity.getEstablishment()))
      .acquirer(acquirerName(entity.getAcquirer()))
      .flag(flagName(entity.getFlag()))
      .pvNumber(entity.getPvNumber())
      .originalPvNumber(entity.getPvNumber())
      .rvNumber(entity.getRvNumber())
      .originalRvNumber(entity.getRvNumber())
      .nsu(entity.getNsu())
      .authorization(entity.getAuthorization())
      .tid(entity.getTid())
      .orderNumber(entity.getEcommerceOrderNumber())
      .processNumber(code(entity.getProcessNumber()))
      .saleValue(entity.getTransactionValue())
      .disputedValue(entity.getTransactionValue())
      .reasonCode(code(entity.getRequestCode()))
      .reasonDescription(requestDescription(entity.getRequestCode()))
      .requestedDocuments(requestDocuments(entity.getRequestCode()))
      .sourceType("033".equals(entity.getRecordType()) ? ChargebackEventSourceType.REQUEST_ECOMMERCE : ChargebackEventSourceType.REQUEST)
      .processedFile(fileName(entity.getProcessedFile()))
      .status(ChargebackAnalysisStatus.REQUEST_RECEIVED)
      .build();
  }

  private ChargebackAnalysisModel toOpenChargebackModel(PendingDebtEntity entity) {
    return ChargebackAnalysisModel.builder()
      .id(entity.getId())
      .saleDate(entity.getDateOriginalTransaction())
      .disputeDate(entity.getDateDebitOrder())
      .dueDate(entity.getPaymentDate())
      .debitDate(entity.getDateDebitOrder())
      .lastEventDate(firstNonNull(entity.getPaymentDate(), entity.getDateDebitOrder(), entity.getDateOriginalTransaction()))
      .company(companyName(entity.getCompany()))
      .establishment(establishmentName(entity.getEstablishment()))
      .acquirer(acquirerName(entity.getAcquirer()))
      .flag(flagName(entity.getFlag()))
      .pvNumber(entity.getPvNumber())
      .originalPvNumber(entity.getPvNumberOriginal())
      .originalRvNumber(entity.getNumberRvOriginal())
      .nsu(entity.getNsu())
      .authorization(entity.getAuthorization())
      .tid(entity.getTid())
      .processNumber(code(entity.getNumberProcessChargeback()))
      .debitOrderNumber(code(entity.getNumberDebitOrder()))
      .retentionProcessNumber(code(entity.getRetentionProcessNumber()))
      .saleValue(entity.getOriginalTransactionValue())
      .disputedValue(firstNonNull(entity.getValueDebitOrder(), entity.getPendingValue()))
      .pendingValue(entity.getPendingValue())
      .compensatedValue(entity.getCompensatedValue())
      .reasonCode(code(entity.getReasonCode()))
      .reasonDescription(entity.getReasonDescription())
      .compensationCode(code(entity.getCompensationCode()))
      .compensationDescription(entity.getCompensationDescription())
      .sourceType(debitChargebackClassifier.type(entity))
      .processedFile(fileName(entity.getProcessedFile()))
      .status(pendingChargebackStatus(entity))
      .build();
  }

  private ChargebackAnalysisModel toSettledChargebackModel(SettledDebtEntity entity) {
    return ChargebackAnalysisModel.builder()
      .id(entity.getId())
      .saleDate(entity.getDateOriginalTransaction())
      .disputeDate(entity.getDateDebitOrder())
      .dueDate(entity.getLiquidatedDate())
      .debitDate(entity.getDateDebitOrder())
      .settlementDate(entity.getLiquidatedDate())
      .lastEventDate(firstNonNull(entity.getLiquidatedDate(), entity.getDateDebitOrder(), entity.getDateOriginalTransaction()))
      .acquirer(acquirerName(entity.getAcquirer()))
      .flag(flagName(entity.getFlag()))
      .pvNumber(entity.getPvNumber())
      .originalPvNumber(entity.getPvNumberOriginal())
      .originalRvNumber(entity.getNumberRvOriginal())
      .nsu(entity.getNsu())
      .authorization(entity.getAuthorization())
      .tid(entity.getTid())
      .processNumber(code(entity.getNumberProcessChargeback()))
      .debitOrderNumber(code(entity.getNumberDebitOrder()))
      .retentionProcessNumber(code(entity.getRetentionProcessNumber()))
      .saleValue(entity.getOriginalTransactionValue())
      .disputedValue(firstNonNull(entity.getRequestedCancellationValue(), entity.getValueDebitOrder(), entity.getLiquidatedValue()))
      .settledValue(entity.getLiquidatedValue())
      .compensatedValue(entity.getLiquidatedValue())
      .reasonCode(code(entity.getReasonCode()))
      .reasonDescription(entity.getReasonDescription())
      .compensationCode(code(entity.getCodeCompensation()))
      .compensationDescription(entity.getCompensation())
      .sourceType(debitChargebackClassifier.type(entity))
      .processedFile(fileName(entity.getProcessedFile()))
      .status(ChargebackAnalysisStatus.LIQUIDATED)
      .build();
  }

  private ChargebackAnalysisModel toAdjustmentChargebackModel(AdjustmentEntity entity) {
    LocalDate debitDate = debitChargebackClassifier.debitDate(entity);
    LocalDate settlementDate = debitChargebackClassifier.settlementDate(entity);
    ChargebackAnalysisStatus status = adjustmentChargebackStatus(entity);
    return ChargebackAnalysisModel.builder()
      .id(entity.getId())
      .saleDate(entity.getTransactionDate())
      .disputeDate(debitDate)
      .dueDate(firstNonNull(settlementDate, entity.getCreditDate(), entity.getOriginalDueDate()))
      .debitDate(debitDate)
      .settlementDate(settlementDate)
      .lastEventDate(firstNonNull(settlementDate, debitDate, entity.getTransactionDate()))
      .company(companyName(entity.getCompany()))
      .establishment(establishmentName(entity.getEstablishment()))
      .acquirer(acquirerName(entity.getAcquirer()))
      .flag(flagName(firstNonNull(entity.getRvFlagAdjustment(), entity.getRvFlagOrigin())))
      .pvNumber(firstNonNull(entity.getPvNumberAdjustment(), entity.getPvNumber()))
      .originalPvNumber(entity.getPvNumberOriginal())
      .rvNumber(firstNonNull(entity.getRvNumberAdjustment(), entity.getRvNumberInstallmentAdjusted()))
      .originalRvNumber(firstNonNull(entity.getRvNumberOriginal(), entity.getRvNumberInstallmentOriginal()))
      .nsu(entity.getNsu())
      .authorization(entity.getAuthorization())
      .tid(entity.getTid())
      .orderNumber(entity.getEcommerceOrderNumber())
      .debitOrderNumber(code(entity.getNumberDebitOrder()))
      .saleValue(firstNonNull(entity.getTransactionValue(), entity.getOriginalGrossSalesSummaryValue(), entity.getGrossValue()))
      .disputedValue(debitChargebackClassifier.debitValue(entity))
      .pendingValue(entity.getPendingValue())
      .settledValue(debitChargebackClassifier.settledValue(entity))
      .compensatedValue(debitChargebackClassifier.settledValue(entity))
      .reasonCode(firstNonBlank(adjustmentReasonCode(entity), code(entity.getAdjustmentReason2()), entity.getRawAdjustmentCode()))
      .reasonDescription(firstNonBlank(entity.getAdjustmentDescription(), entity.getAdjustmentType(), entity.getDebitType(), entity.getSourceRecordIdentifier()))
      .sourceType(debitChargebackClassifier.type(entity))
      .processedFile(fileName(entity.getProcessedFile()))
      .status(status)
      .build();
  }


  private ChargebackAnalysisModel toUnschedulingChargebackModel(InstallmentUnschedulingEntity entity) {
    LocalDate eventDate = debitChargebackClassifier.unschedulingDate(entity);

    return ChargebackAnalysisModel.builder()
      .id(entity.getId())
      .saleDate(entity.getTransactionDate())
      .disputeDate(firstNonNull(entity.getCancellationDate(), eventDate))
      .dueDate(firstNonNull(entity.getAdjustedCreditDate(), entity.getDateCredit()))
      .debitDate(firstNonNull(entity.getCancellationDate(), eventDate))
      .lastEventDate(firstNonNull(eventDate, entity.getTransactionDate()))
      .company(companyName(entity.getCompany()))
      .establishment(establishmentName(entity.getEstablishment()))
      .acquirer(acquirerName(entity.getAcquirer()))
      .flag(flagName(firstNonNull(entity.getFlagRvAdjusted(), entity.getFlagRvOrigin())))
      .pvNumber(firstNonNull(entity.getAdjustedPvNumber(), entity.getPvNumberOriginal()))
      .originalPvNumber(entity.getPvNumberOriginal())
      .rvNumber(firstNonNull(entity.getAdjustedRvNumber(), entity.getRvNumberOriginal()))
      .originalRvNumber(entity.getRvNumberOriginal())
      .nsu(entity.getNsu())
      .tid(entity.getTid())
      .orderNumber(entity.getOrderNumber())
      .saleValue(firstNonNull(entity.getRvValueOriginal(), entity.getOriginalValueChangedInstallment()))
      .disputedValue(debitChargebackClassifier.debitValue(entity))
      .pendingValue(entity.getNewInstallmentValue())
      .reasonCode(unschedulingReasonCode(entity))
      .reasonDescription(unschedulingReasonDescription(entity))
      .sourceType(debitChargebackClassifier.type(entity))
      .processedFile(fileName(entity.getProcessedFile()))
      .status(debitChargebackClassifier.chargebackStatus(entity))
      .build();
  }


  private String unschedulingReasonCode(InstallmentUnschedulingEntity entity) {
    if (entity == null) return null;
    String recordType = trim(entity.getRecordType());
    if ("08".equals(recordType)) return code(entity.getUnschedulingStatus());
    return firstNonBlank(entity.getTypeDebit(), code(entity.getUnschedulingStatus()));
  }

  private String unschedulingReasonDescription(InstallmentUnschedulingEntity entity) {
    if (entity == null) return "Desagendamento de parcela";

    String recordType = trim(entity.getRecordType());
    if ("08".equals(recordType)) {
      if (Integer.valueOf(1).equals(entity.getUnschedulingStatus())) return "Desagendamento por chargeback";
      if (Integer.valueOf(0).equals(entity.getUnschedulingStatus())) return "Desagendamento por cancelamento";
    }

    String typeDebit = trim(entity.getTypeDebit());
    if ("2".equals(typeDebit)) return "Cancelamento via emissor / chargeback";
    if ("1".equals(typeDebit)) return "Cancelamento via estabelecimento";
    if (Boolean.TRUE.equals(entity.getEcommerce())) return "Desagendamento e-commerce";
    return "Desagendamento de parcela";
  }

  private boolean matchesChargebackFilter(ChargebackAnalysisModel item, ChargebackAnalysisFilter filter, String global) {
    if (item == null) return false;
    if (filter == null && (global == null || global.isBlank())) return true;

    if (filter != null) {
      if (!empty(filter.statuses()) && !filter.statuses().contains(item.getStatus())) return false;
      if (!between(item.getSaleDate(), filter.saleDateStart(), filter.saleDateEnd())) return false;
      if (!between(item.getRequestDate(), filter.requestDateStart(), filter.requestDateEnd())) return false;
      if (!between(item.getDocumentationDueDate(), filter.deadlineStart(), filter.deadlineEnd())) return false;
      if (!between(item.getDebitDate(), filter.debitDateStart(), filter.debitDateEnd())) return false;
      if (!between(item.getSettlementDate(), filter.settlementDateStart(), filter.settlementDateEnd())) return false;
      if (!between(item.getLastEventDate(), filter.eventDateStart(), filter.eventDateEnd())) return false;
      if (!between(chargebackValue(item), filter.valueStart(), filter.valueEnd())) return false;
      if (!contains(code(item.getNsu()), filter.nsu())) return false;
      if (!contains(code(item.getPvNumber()), filter.pvNumber())) return false;
      if (!contains(code(item.getRvNumber()), filter.rvNumber())) return false;
      if (!contains(item.getAuthorization(), filter.authorization())) return false;
      if (!contains(item.getTid(), filter.tid())) return false;
      if (!contains(item.getProcessNumber(), filter.processNumber())) return false;
      if (!contains(item.getDebitOrderNumber(), filter.debitOrderNumber())) return false;
      if (!contains(item.getReasonCode(), filter.reasonCode())) return false;
      if (!matchesReason(item, filter.reason())) return false;
    }

    if (global == null || global.isBlank()) return true;
    String haystack = String.join(" ",
      nullToBlank(item.getCompany()), nullToBlank(item.getEstablishment()), nullToBlank(item.getAcquirer()), nullToBlank(item.getFlag()),
      nullToBlank(code(item.getNsu())), nullToBlank(item.getAuthorization()), nullToBlank(item.getTid()), nullToBlank(item.getOrderNumber()),
      nullToBlank(item.getProcessNumber()), nullToBlank(item.getDebitOrderNumber()), nullToBlank(item.getReasonCode()),
      nullToBlank(item.getReasonDescription()), item.getStatus() == null ? "" : item.getStatus().name(),
      item.getSourceType() == null ? "" : item.getSourceType().name(), nullToBlank(item.getProcessedFile())
    );
    return contains(haystack, global);
  }

  /**
   * Chave operacional da venda contestada.
   *
   * Importante: não priorizar número de processo. Nos arquivos reais da Rede, o Request
   * e o evento financeiro da mesma venda podem chegar com processos diferentes ou com
   * processo preenchido somente em uma das pontas. A chave mais estável para rastrear
   * uma única transação é PV + NSU + autorização; para e-commerce, TID também é forte.
   */
  private String transactionTrackingKey(ChargebackAnalysisModel item) {
    if (item == null) return "UNKNOWN|" + UUID.randomUUID();

    String pv = code(item.getPvNumber());
    String rv = code(item.getRvNumber());
    String nsu = code(item.getNsu());
    String authorization = normalizeKey(item.getAuthorization());
    String tid = normalizeKey(item.getTid());
    String processNumber = normalizeKey(item.getProcessNumber());
    String debitOrderNumber = normalizeKey(item.getDebitOrderNumber());

    if (notBlank(pv) && notBlank(nsu) && notBlank(authorization)) {
      return "PV_NSU_AUTH|" + pv + "|" + nsu + "|" + authorization;
    }

    if (notBlank(tid)) {
      return "TID|" + tid;
    }

    if (notBlank(pv) && notBlank(rv) && notBlank(nsu)) {
      return "PV_RV_NSU|" + pv + "|" + rv + "|" + nsu;
    }

    if (notBlank(pv) && notBlank(nsu)) {
      return "PV_NSU|" + pv + "|" + nsu;
    }

    if (notBlank(processNumber)) {
      return "PROCESS|" + processNumber;
    }

    if (notBlank(debitOrderNumber)) {
      return "DEBIT_ORDER|" + debitOrderNumber;
    }

    return "ID|" + item.getId();
  }

  private String eventTitle(ChargebackAnalysisModel item) {
    return switch (item.getStatus()) {
      case REQUEST_RECEIVED -> "Request recebido";
      case DOCUMENTATION_DUE -> "Prazo de documentação";
      case DOCUMENTATION_OVERDUE -> "Prazo de documentação vencido";
      case PENDING_DEBIT -> "Débito pendente";
      case BANK_DEBIT_SCHEDULED -> "Débito via banco";
      case NET_COMPENSATION_SCHEDULED -> "Compensação via Net";
      case DESCHEDULED -> "Desagendamento de parcela";
      case LIQUIDATED, LOST -> "Débito liquidado";
      case REVERSED, WON -> "Chargeback revertido";
      case UNDER_REVIEW -> "Em análise";
      default -> "Evento de chargeback";
    };
  }

  private String eventDescription(ChargebackAnalysisModel item) {
    List<String> parts = new ArrayList<>();
    if (notBlank(item.getReasonCode()) || notBlank(item.getReasonDescription())) {
      parts.add("Motivo " + nullToBlank(item.getReasonCode()) + " - " + nullToBlank(item.getReasonDescription()).trim());
    }
    if (notBlank(item.getProcessNumber())) parts.add("Processo " + item.getProcessNumber());
    if (notBlank(item.getDebitOrderNumber())) parts.add("Ordem de débito " + item.getDebitOrderNumber());
    if (notBlank(item.getRequestedDocuments())) parts.add(item.getRequestedDocuments());
    return String.join(". ", parts);
  }

  private LocalDate timelineDate(ChargebackAnalysisModel item) {
    return firstNonNull(
      item.getRequestDate(),
      item.getDebitDate(),
      item.getSettlementDate(),
      item.getDisputeDate(),
      item.getLastEventDate(),
      item.getSaleDate()
    );
  }

  private int statusRank(ChargebackAnalysisStatus status) {
    if (status == null) return 0;
    return switch (status) {
      case REQUEST_RECEIVED -> 10;
      case DOCUMENTATION_DUE -> 20;
      case DOCUMENTATION_OVERDUE -> 30;
      case PENDING_DEBIT -> 40;
      case BANK_DEBIT_SCHEDULED, NET_COMPENSATION_SCHEDULED -> 50;
      case DESCHEDULED -> 60;
      case UNDER_REVIEW -> 65;
      case LIQUIDATED, LOST -> 80;
      case REVERSED, WON -> 90;
    };
  }

  private boolean isRequest(ChargebackAnalysisModel item) {
    return item.getSourceType() == ChargebackEventSourceType.REQUEST || item.getSourceType() == ChargebackEventSourceType.REQUEST_ECOMMERCE;
  }

  private boolean between(LocalDate value, LocalDate start, LocalDate end) {
    if (start == null && end == null) return true;
    if (value == null) return false;
    if (start != null && value.isBefore(start)) return false;
    return end == null || !value.isAfter(end);
  }

  private boolean between(BigDecimal value, BigDecimal start, BigDecimal end) {
    if (start == null && end == null) return true;
    if (value == null) return false;
    if (start != null && value.compareTo(start) < 0) return false;
    return end == null || value.compareTo(end) <= 0;
  }

  private BigDecimal chargebackValue(ChargebackAnalysisModel item) {
    return firstNonNull(item.getDisputedValue(), item.getPendingValue(), item.getSettledValue(), item.getSaleValue());
  }

  private boolean matchesReason(ChargebackAnalysisModel item, String reason) {
    if (reason == null || reason.isBlank()) return true;

    return contains(item.getReasonCode(), reason)
      || contains(item.getReasonDescription(), reason)
      || contains(item.getRequestedDocuments(), reason);
  }

  private boolean contains(String value, String term) {
    if (term == null || term.isBlank()) return true;
    return value != null && value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT).trim());
  }

  private boolean empty(Collection<?> values) {
    return values == null || values.isEmpty();
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private ChargebackAnalysisStatus pendingChargebackStatus(PendingDebtEntity entity) {
    if (entity == null) return ChargebackAnalysisStatus.PENDING_DEBIT;
    if (entity.getPendingValue() != null && entity.getPendingValue().compareTo(BigDecimal.ZERO) <= 0
      && nz(entity.getCompensatedValue()).compareTo(BigDecimal.ZERO) > 0) return ChargebackAnalysisStatus.REVERSED;
    if ("038".equals(trim(entity.getRecordType()))) return ChargebackAnalysisStatus.BANK_DEBIT_SCHEDULED;
    return ChargebackAnalysisStatus.PENDING_DEBIT;
  }

  private ChargebackAnalysisStatus adjustmentChargebackStatus(AdjustmentEntity entity) {
    if (entity == null) return ChargebackAnalysisStatus.NET_COMPENSATION_SCHEDULED;
    String recordType = trim(entity.getRecordType());
    if ("049".equals(recordType) || "057".equals(recordType) || "069".equals(recordType)
      || "D".equalsIgnoreCase(trim(entity.getNet()))) return ChargebackAnalysisStatus.DESCHEDULED;
    if (debitChargebackClassifier.isCreditAdjustment(entity) || "043".equals(recordType)) return ChargebackAnalysisStatus.REVERSED;
    if ("045".equals(recordType) || "056".equals(recordType)) return ChargebackAnalysisStatus.LIQUIDATED;
    if ("038".equals(recordType) || "054".equals(recordType)) return ChargebackAnalysisStatus.BANK_DEBIT_SCHEDULED;
    return ChargebackAnalysisStatus.NET_COMPENSATION_SCHEDULED;
  }

  private String requestDescription(Integer requestCode) {
    if (requestCode == null) return "Solicitação de documentação comprobatória";
    return "Request " + requestCode + " - solicitação de documentação comprobatória";
  }

  private String requestDocuments(Integer requestCode) {
    if (requestCode == null) return "Consultar tabela de motivos e documentos para Request da Rede";
    return "Consultar documentos exigidos para o motivo de Request " + requestCode + " no layout EEVC";
  }

  private BigDecimal sum(Stream<BigDecimal> values) {
    return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumNonNull(Stream<BigDecimal> values) {
    BigDecimal result = sum(values);
    return result.compareTo(BigDecimal.ZERO) == 0 ? null : result;
  }

  private String code(Integer value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String code(Long value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String code(BigInteger value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String adjustmentReasonCode(AdjustmentEntity entity) {
    AdjustmentReasonEnum reason = entity.getAdjustmentReason();
    return reason != null ? code(reason.getCode()) : null;
  }

  private String flagName(FlagEntity flag) {
    return flag != null ? flag.getName() : null;
  }

  private String acquirerName(AcquirerEntity acquirer) {
    return acquirer != null ? acquirer.getFantasyName() : null;
  }

  private String companyName(CompanyEntity company) {
    return company != null ? firstNonBlank(company.getFantasyName(), company.getSocialReason(), company.getCnpj()) : null;
  }

  private String establishmentName(EstablishmentEntity establishment) {
    return establishment != null ? String.valueOf(establishment.getPvNumber()) : null;
  }

  private String fileName(ProcessedFileEntity processedFile) {
    return processedFile != null ? processedFile.getFile() : null;
  }

  private String normalizeKey(String value) {
    if (!notBlank(value)) return null;
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return normalized.trim().toUpperCase(Locale.ROOT).replaceFirst("^0+(?!$)", "");
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private <T, R> R firstFrom(List<T> items, Function<T, R> extractor) {
    return items.stream().map(extractor).filter(Objects::nonNull).findFirst().orElse(null);
  }

  private BigDecimal maxValue(Stream<BigDecimal> values) {
    return values
      .filter(Objects::nonNull)
      .max(BigDecimal::compareTo)
      .orElse(null);
  }

  private BigDecimal firstNonZeroOrFirst(List<ChargebackAnalysisModel> items, Function<ChargebackAnalysisModel, BigDecimal> extractor) {
    BigDecimal first = null;

    for (ChargebackAnalysisModel item : items) {
      BigDecimal value = extractor.apply(item);

      if (value == null) continue;
      if (first == null) first = value;
      if (value.compareTo(BigDecimal.ZERO) != 0) return value;
    }

    return first;
  }

  private <T> Page<T> page(List<T> items, Pageable pageable) {
    int start = Math.toIntExact(Math.min(pageable.getOffset(), items.size()));
    int end = Math.min(start + pageable.getPageSize(), items.size());
    return new PageImpl<>(items.subList(start, end), pageable, items.size());
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) return value;
    }
    return null;
  }
}