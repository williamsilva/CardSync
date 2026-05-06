package com.cardsync.core.file.service.dashboard;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingDashboardModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingDivergenceContextModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingMetricModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingStatusCountModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.FileProcessingTopErrorFileModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard.ReconciliationStatusAmountModel;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileProcessingDashboardService {

  private static final int RECONCILED_STATUS = 2;

  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public FileProcessingDashboardModel dashboard() {
    return new FileProcessingDashboardModel(
      buildCards(),
      filesByStatus(),
      reconciliationByStatus(),
      divergenceContexts(),
      topFilesWithErrors()
    );
  }

  @Transactional(readOnly = true)
  public List<FileProcessingDivergenceContextModel> divergenceContexts() {
    List<FileProcessingDivergenceContextModel> result = new ArrayList<>();
    result.addAll(bankReleaseDivergenceContexts());
    result.addAll(creditOrderDivergenceContexts());
    result.addAll(installmentDivergenceContexts());
    result.addAll(erpCommercialDivergenceContexts());
    return result.stream()
      .limit(100)
      .toList();
  }

  private List<FileProcessingMetricModel> buildCards() {
    long totalFiles = count("select count(p.id) from ProcessedFileEntity p");
    long filesWithErrors = entityManager.createQuery("""
      select count(p.id)
      from ProcessedFileEntity p
      where p.errorLines > 0
         or p.warningLines > 0
         or p.status in :badStatuses
      """, Number.class)
      .setParameter("badStatuses", List.of(FileStatusEnum.ERROR, FileStatusEnum.INVALID))
      .getSingleResult()
      .longValue();
    long erpPending = count("""
      select count(t.id)
      from TransactionErpEntity t
      where t.commercialStatus <> :ok
         or t.commercialStatus is null
         or t.company is null
         or t.establishment is null
         or t.acquirer is null
      """, "ok", ErpCommercialStatusEnum.OK);
    long bankPending = count("""
      select count(r.id)
      from ReleasesBankEntity r
      where r.reconciliationStatus is null or r.reconciliationStatus <> :reconciled
      """, "reconciled", RECONCILED_STATUS);
    long creditOrdersPending = count("""
      select count(c.id)
      from CreditOrderEntity c
      where c.reconciliationStatus is null or c.reconciliationStatus <> :reconciled
      """, "reconciled", RECONCILED_STATUS);
    long installmentsPending = count("""
      select count(i.id)
      from InstallmentAcqEntity i
      where i.statusPaymentBank is null or i.statusPaymentBank <> :reconciled
      """, "reconciled", RECONCILED_STATUS);

    return List.of(
      FileProcessingMetricModel.of("totalFiles", "Arquivos processados", totalFiles),
      FileProcessingMetricModel.of("filesWithErrors", "Arquivos com erro/alerta", filesWithErrors),
      FileProcessingMetricModel.of("erpPending", "Vendas ERP pendentes", erpPending),
      FileProcessingMetricModel.of("bankPending", "Lançamentos bancários pendentes", bankPending),
      FileProcessingMetricModel.of("creditOrdersPending", "Ordens de crédito pendentes", creditOrdersPending),
      FileProcessingMetricModel.of("installmentsPending", "Parcelas adquirente pendentes", installmentsPending)
    );
  }

  private List<FileProcessingStatusCountModel> filesByStatus() {
    return entityManager.createQuery("""
        select p.group, p.status, count(p.id)
        from ProcessedFileEntity p
        group by p.group, p.status
        order by p.group, p.status
        """, Object[].class)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingStatusCountModel(
        asString(row[0]),
        asString(row[1]),
        asLong(row[2])
      ))
      .toList();
  }

  private List<ReconciliationStatusAmountModel> reconciliationByStatus() {
    List<ReconciliationStatusAmountModel> result = new ArrayList<>();
    result.addAll(statusAmounts("BANK_RELEASE", """
      select r.reconciliationStatus, count(r.id), coalesce(sum(r.releaseValue), 0)
      from ReleasesBankEntity r
      group by r.reconciliationStatus
      order by r.reconciliationStatus
      """));
    result.addAll(statusAmounts("CREDIT_ORDER", """
      select c.reconciliationStatus, count(c.id), coalesce(sum(c.releaseValue), 0)
      from CreditOrderEntity c
      group by c.reconciliationStatus
      order by c.reconciliationStatus
      """));
    result.addAll(statusAmounts("ACQ_INSTALLMENT", """
      select i.statusPaymentBank, count(i.id), coalesce(sum(i.liquidValue), 0)
      from InstallmentAcqEntity i
      group by i.statusPaymentBank
      order by i.statusPaymentBank
      """));
    return result;
  }

  private List<ReconciliationStatusAmountModel> statusAmounts(String source, String jpql) {
    return entityManager.createQuery(jpql, Object[].class)
      .getResultList()
      .stream()
      .map(row -> new ReconciliationStatusAmountModel(source, asInteger(row[0]), asLong(row[1]), asBigDecimal(row[2])))
      .toList();
  }

  private List<FileProcessingTopErrorFileModel> topFilesWithErrors() {
    return entityManager.createQuery("""
        select p.id, p.file, o.code, p.group, p.status, count(e.id), coalesce(p.warningLines, 0)
        from ProcessedFileErrorEntity e
        join e.processedFile p
        left join p.originFile o
        group by p.id, p.file, o.code, p.group, p.status, p.warningLines
        order by count(e.id) desc, p.file asc
        """, Object[].class)
      .setMaxResults(20)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingTopErrorFileModel(
        (UUID) row[0],
        asString(row[1]),
        asString(row[2]),
        asString(row[3]),
        asString(row[4]),
        asLong(row[5]),
        asLong(row[6])
      ))
      .toList();
  }

  private List<FileProcessingDivergenceContextModel> bankReleaseDivergenceContexts() {
    return entityManager.createQuery("""
        select coalesce(c.fantasyName, c.socialReason, 'Sem empresa'),
               coalesce(a.fantasyName, a.socialReason, 'Sem adquirente'),
               coalesce(b.name, 'Sem banco'),
               coalesce(f.name, 'Sem bandeira'),
               count(r.id),
               coalesce(sum(r.releaseValue), 0)
        from ReleasesBankEntity r
        left join r.company c
        left join r.acquirer a
        left join r.bank b
        left join r.flag f
        where r.reconciliationStatus is null or r.reconciliationStatus <> :reconciled
        group by c.fantasyName, c.socialReason, a.fantasyName, a.socialReason, b.name, f.name
        order by count(r.id) desc
        """, Object[].class)
      .setParameter("reconciled", RECONCILED_STATUS)
      .setMaxResults(25)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingDivergenceContextModel(
        "BANK_RELEASE",
        asString(row[0]),
        asString(row[1]),
        asString(row[2]),
        asString(row[3]),
        asLong(row[4]),
        asBigDecimal(row[5])
      ))
      .toList();
  }

  private List<FileProcessingDivergenceContextModel> creditOrderDivergenceContexts() {
    return entityManager.createQuery("""
        select coalesce(c.fantasyName, c.socialReason, 'Sem empresa'),
               coalesce(a.fantasyName, a.socialReason, 'Sem adquirente'),
               'Sem banco',
               coalesce(f.name, 'Sem bandeira'),
               count(o.id),
               coalesce(sum(o.releaseValue), 0)
        from CreditOrderEntity o
        left join o.company c
        left join o.acquirer a
        left join o.flag f
        where o.reconciliationStatus is null or o.reconciliationStatus <> :reconciled
        group by c.fantasyName, c.socialReason, a.fantasyName, a.socialReason, f.name
        order by count(o.id) desc
        """, Object[].class)
      .setParameter("reconciled", RECONCILED_STATUS)
      .setMaxResults(25)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingDivergenceContextModel(
        "CREDIT_ORDER",
        asString(row[0]),
        asString(row[1]),
        asString(row[2]),
        asString(row[3]),
        asLong(row[4]),
        asBigDecimal(row[5])
      ))
      .toList();
  }

  private List<FileProcessingDivergenceContextModel> installmentDivergenceContexts() {
    return entityManager.createQuery("""
        select coalesce(c.fantasyName, c.socialReason, 'Sem empresa'),
               coalesce(a.fantasyName, a.socialReason, 'Sem adquirente'),
               'Sem banco',
               coalesce(f.name, 'Sem bandeira'),
               count(i.id),
               coalesce(sum(i.liquidValue), 0)
        from InstallmentAcqEntity i
        join i.transaction t
        left join t.company c
        left join t.acquirer a
        left join t.flag f
        where i.statusPaymentBank is null or i.statusPaymentBank <> :reconciled
        group by c.fantasyName, c.socialReason, a.fantasyName, a.socialReason, f.name
        order by count(i.id) desc
        """, Object[].class)
      .setParameter("reconciled", RECONCILED_STATUS)
      .setMaxResults(25)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingDivergenceContextModel(
        "ACQ_INSTALLMENT",
        asString(row[0]),
        asString(row[1]),
        asString(row[2]),
        asString(row[3]),
        asLong(row[4]),
        asBigDecimal(row[5])
      ))
      .toList();
  }

  private List<FileProcessingDivergenceContextModel> erpCommercialDivergenceContexts() {
    return entityManager.createQuery("""
        select coalesce(c.fantasyName, c.socialReason, t.sourceCompanyName, 'Sem empresa'),
               coalesce(a.fantasyName, a.socialReason, 'Sem adquirente'),
               'Sem banco',
               coalesce(f.name, 'Sem bandeira'),
               count(t.id),
               coalesce(sum(t.grossValue), 0)
        from TransactionErpEntity t
        left join t.company c
        left join t.acquirer a
        left join t.flag f
        where t.commercialStatus <> :ok
           or t.commercialStatus is null
           or t.company is null
           or t.establishment is null
           or t.acquirer is null
        group by c.fantasyName, c.socialReason, t.sourceCompanyName, a.fantasyName, a.socialReason, f.name
        order by count(t.id) desc
        """, Object[].class)
      .setParameter("ok", ErpCommercialStatusEnum.OK)
      .setMaxResults(25)
      .getResultList()
      .stream()
      .map(row -> new FileProcessingDivergenceContextModel(
        "ERP_COMMERCIAL",
        asString(row[0]),
        asString(row[1]),
        asString(row[2]),
        asString(row[3]),
        asLong(row[4]),
        asBigDecimal(row[5])
      ))
      .toList();
  }

  private long count(String jpql) {
    Number number = entityManager.createQuery(jpql, Number.class).getSingleResult();
    return number == null ? 0L : number.longValue();
  }

  private long count(String jpql, String parameterName, Object parameterValue) {
    Number number = entityManager.createQuery(jpql, Number.class)
      .setParameter(parameterName, parameterValue)
      .getSingleResult();
    return number == null ? 0L : number.longValue();
  }

  private String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Long asLong(Object value) {
    if (value == null) {
      return 0L;
    }
    return ((Number) value).longValue();
  }

  private Integer asInteger(Object value) {
    if (value == null) {
      return null;
    }
    return ((Number) value).intValue();
  }

  private BigDecimal asBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return new BigDecimal(String.valueOf(value));
  }
}
