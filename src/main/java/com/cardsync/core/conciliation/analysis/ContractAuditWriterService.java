package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.ContractAuditStatusEnum;
import com.cardsync.domain.repository.ContractAuditRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAuditWriterService {

  private final EntityManager entityManager;
  private final ContractAuditRepository contractAuditRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveOrUpdate(ContractAuditCommand command) {
    saveOrUpdateAll(command == null ? List.of() : List.of(command));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveOrUpdateAll(Collection<ContractAuditCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      return;
    }

    Map<UUID, ContractAuditCommand> commandsByTransactionAcqId = commands.stream()
      .filter(command -> command != null && command.transactionAcqId() != null)
      .collect(Collectors.toMap(
        ContractAuditCommand::transactionAcqId,
        Function.identity(),
        (left, right) -> right,
        LinkedHashMap::new
      ));

    if (commandsByTransactionAcqId.isEmpty()) {
      return;
    }

    // Mesma restrição de 65.535 parâmetros do Postgres/JDBC do deleteByTransactionAcqIds:
    // busca em lotes para não estourar o limite em reprocessamentos grandes.
    Map<UUID, ContractAuditEntity> existingByTransactionAcqId = findByTransactionAcqIdsBatched(
      commandsByTransactionAcqId.keySet())
      .stream()
      .filter(audit -> audit.getTransactionAcq() != null && audit.getTransactionAcq().getId() != null)
      .collect(Collectors.toMap(
        audit -> audit.getTransactionAcq().getId(),
        Function.identity(),
        (left, right) -> left,
        LinkedHashMap::new
      ));

    List<ContractAuditEntity> audits = commandsByTransactionAcqId.values().stream()
      .map(command -> fill(existingByTransactionAcqId.getOrDefault(command.transactionAcqId(), new ContractAuditEntity()), command))
      .toList();

    contractAuditRepository.saveAll(audits);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteByTransactionAcqId(UUID transactionAcqId) {
    deleteByTransactionAcqIds(transactionAcqId == null ? List.of() : List.of(transactionAcqId));
  }

  // Postgres/JDBC limita uma PreparedStatement a 65.535 parâmetros no total; um WHERE id
  // IN (...) com um lote de reprocessamento grande (ex.: backfill histórico) pode facilmente
  // passar disso. Divide em lotes seguros abaixo desse teto, tanto pra busca quanto pro delete.
  private static final int IN_CLAUSE_BATCH_SIZE = 1000;

  private List<ContractAuditEntity> findByTransactionAcqIdsBatched(Collection<UUID> transactionAcqIds) {
    List<UUID> ids = transactionAcqIds.stream().filter(java.util.Objects::nonNull).distinct().toList();

    if (ids.isEmpty()) {
      return List.of();
    }

    List<ContractAuditEntity> result = new java.util.ArrayList<>(ids.size());
    for (int start = 0; start < ids.size(); start += IN_CLAUSE_BATCH_SIZE) {
      List<UUID> chunk = ids.subList(start, Math.min(start + IN_CLAUSE_BATCH_SIZE, ids.size()));
      result.addAll(contractAuditRepository.findByTransactionAcq_IdIn(chunk));
    }
    return result;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteByTransactionAcqIds(Collection<UUID> transactionAcqIds) {
    if (transactionAcqIds == null || transactionAcqIds.isEmpty()) {
      return;
    }

    List<UUID> ids = transactionAcqIds.stream()
      .filter(java.util.Objects::nonNull)
      .distinct()
      .toList();

    if (ids.isEmpty()) {
      return;
    }

    for (int start = 0; start < ids.size(); start += IN_CLAUSE_BATCH_SIZE) {
      List<UUID> chunk = ids.subList(start, Math.min(start + IN_CLAUSE_BATCH_SIZE, ids.size()));
      contractAuditRepository.deleteByTransactionAcqIdInBulk(chunk);
    }
  }

  private ContractAuditEntity fill(ContractAuditEntity audit, ContractAuditCommand command) {
    audit.setStatus(command.status());
    audit.setCaptureCode(command.capture());
    audit.setModalityCode(command.modality());
    audit.setNsu(command.nsu());
    audit.setAuthorization(command.authorization());
    audit.setGrossValue(command.grossValue());
    audit.setLiquidValue(command.liquidValue());
    audit.setRateAcquirer(command.rateAcquirer());
    audit.setRateContract(command.rateContract());
    audit.setDiscountValue(command.discountValue());
    audit.setExpectedDiscountValue(command.expectedDiscountValue());
    audit.setDifferenceValue(command.differenceValue());

    audit.setFlag(reference(FlagEntity.class, command.flagId()));
    audit.setAcquirer(reference(AcquirerEntity.class, command.acquirerId()));
    audit.setContract(reference(ContractEntity.class, command.contractId()));
    audit.setCompany(reference(CompanyEntity.class, command.companyId()));
    audit.setEstablishment(reference(EstablishmentEntity.class, command.establishmentId()));
    audit.setTransactionAcq(reference(TransactionAcqEntity.class, command.transactionAcqId()));
    audit.setTransactionErp(reference(TransactionErpEntity.class, command.transactionErpId()));

    return audit;
  }

  private <T> T reference(Class<T> type, UUID id) {
    return id == null ? null : entityManager.getReference(type, id);
  }

  public record ContractAuditCommand(
    ContractAuditStatusEnum status,
    Integer capture,
    Integer modality,
    Long nsu,
    String authorization,
    BigDecimal grossValue,
    BigDecimal liquidValue,
    BigDecimal rateAcquirer,
    BigDecimal rateContract,
    BigDecimal discountValue,
    BigDecimal expectedDiscountValue,
    BigDecimal differenceValue,
    UUID flagId,
    UUID acquirerId,
    UUID contractId,
    UUID companyId,
    UUID establishmentId,
    UUID transactionAcqId,
    UUID transactionErpId
  ) {
    public static ContractAuditCommand from(ContractAuditEntity audit) {
      return new ContractAuditCommand(
        audit.getStatus(),
        audit.getCapture() == null ? null : audit.getCapture().getCode(),
        audit.getModality() == null ? null : audit.getModality().getCode(),
        audit.getNsu(),
        audit.getAuthorization(),
        audit.getGrossValue(),
        audit.getLiquidValue(),
        audit.getRateAcquirer(),
        audit.getRateContract(),
        audit.getDiscountValue(),
        audit.getExpectedDiscountValue(),
        audit.getDifferenceValue(),
        id(audit.getFlag()),
        id(audit.getAcquirer()),
        id(audit.getContract()),
        id(audit.getCompany()),
        id(audit.getEstablishment()),
        id(audit.getTransactionAcq()),
        id(audit.getTransactionErp())
      );
    }

    private static UUID id(AuditableEntityBase entity) {
      return entity == null ? null : entity.getId();
    }
  }
}
