package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.NoFileDayInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.NoFileDayRepository;
import com.cardsync.infrastructure.repository.spec.NoFileDaySpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoFileDayService {

  private final NoFileDaySpecs noFileDaySpecs;
  private final AcquirerRepository acquirerRepository;
  private final NoFileDayRepository noFileDayRepository;
  private final BankingDomicileRepository bankingDomicileRepository;

  @Transactional(readOnly = true)
  public NoFileDayEntity getById(UUID noFileDayId) {
    return noFileDayRepository.findById(noFileDayId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Dia sem arquivo não encontrado: " + noFileDayId
      ));
  }

  @Transactional(readOnly = true)
  public Page<NoFileDayEntity> search(Pageable pageable, ListQueryDto<NoFileDayFilter> query) {
    return noFileDayRepository.findAll(noFileDaySpecs.fromQuery(query), pageable);
  }

  @Transactional
  public NoFileDayEntity create(NoFileDayInput request) {
    NoFileDayEntity entity = new NoFileDayEntity();
    apply(entity, request);
    return noFileDayRepository.save(entity);
  }

  @Transactional
  public NoFileDayEntity update(UUID id, NoFileDayInput request) {
    NoFileDayEntity entity = getById(id);
    apply(entity, request);
    return noFileDayRepository.save(entity);
  }

  @Transactional
  public void delete(UUID id) {
    noFileDayRepository.delete(getById(id));
  }

  /**
   * Exclui vários dias sem arquivo em uma única transação.

   * Todos os identificadores são validados antes da exclusão. Caso algum registro
   * não exista, nenhuma exclusão é realizada.
   */
  @Transactional
  public void deleteBulk(List<UUID> ids) {
    List<NoFileDayEntity> entities = loadAll(ids);
    noFileDayRepository.deleteAll(entities);
  }

  @Transactional
  public void activate(UUID id) {
    NoFileDayEntity entity = getById(id);
    requireStatus(entity, List.of(StatusEnum.INACTIVE, StatusEnum.BLOCKED), "ativado");
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID id) {
    NoFileDayEntity entity = getById(id);
    requireStatus(entity, List.of(StatusEnum.ACTIVE), "inativado");
    entity.inactivate();
  }

  @Transactional
  public void block(UUID id) {
    NoFileDayEntity entity = getById(id);
    requireStatus(entity, List.of(StatusEnum.ACTIVE), "bloqueado");
    entity.block();
  }

  @Transactional
  public void activateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      requireStatus(entity, List.of(StatusEnum.INACTIVE, StatusEnum.BLOCKED), "ativado");
      entity.activate();
    });
  }

  @Transactional
  public void deactivateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      requireStatus(entity, List.of(StatusEnum.ACTIVE), "inativado");
      entity.inactivate();
    });
  }

  @Transactional
  public void blockBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      requireStatus(entity, List.of(StatusEnum.ACTIVE), "bloqueado");
      entity.block();
    });
  }

  private void requireStatus(NoFileDayEntity entity, List<StatusEnum> allowed, String action) {
    if (!allowed.contains(entity.getStatus())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "O dia sem arquivo não pode ser " + action + " no status atual: " + entity.getStatus()
      );
    }
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<NoFileDayEntity> updater) {
    List<NoFileDayEntity> entities = loadAll(ids);
    entities.forEach(updater);
    noFileDayRepository.saveAll(entities);
  }

  private List<NoFileDayEntity> loadAll(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Informe ao menos um dia sem arquivo."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Informe ao menos um dia sem arquivo."
      );
    }

    List<NoFileDayEntity> entities = noFileDayRepository.findAllById(distinctIds);
    Map<UUID, NoFileDayEntity> byId = entities.stream()
      .collect(Collectors.toMap(NoFileDayEntity::getId, entity -> entity));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Dias sem arquivo não encontrados: " + missingIds
      );
    }

    return distinctIds.stream()
      .map(byId::get)
      .toList();
  }

  private void apply(NoFileDayEntity entity, NoFileDayInput request) {
    validateRequest(request);

    entity.setNoFileDate(request.noFileDate());
    entity.setDescription(request.description().trim());
    entity.setDayType(request.dayType());
    entity.setFileGroup(request.fileGroup());
    entity.setStatus(request.status() != null ? request.status() : StatusEnum.ACTIVE);

    switch (request.fileGroup()) {
      case BANK -> {
        entity.setBankingDomicile(request.bankingDomicileId() == null ? null : loadBankingDomicile(request.bankingDomicileId()));
        entity.setAcquirer(null);
        entity.setAcquirerFileType(null);
      }
      case ADQ -> {
        entity.setBankingDomicile(null);
        entity.setAcquirer(request.acquirerId() == null ? null : loadAcquirer(request.acquirerId()));
        entity.setAcquirerFileType(request.acquirerFileType());
      }
      case ERP -> {
        entity.setBankingDomicile(null);
        entity.setAcquirer(null);
        entity.setAcquirerFileType(null);
      }
    }
  }

  private void validateRequest(NoFileDayInput request) {
    if (request.fileGroup() == FileGroupEnum.ADQ && request.acquirerFileType() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Informe o tipo de arquivo da adquirente."
      );
    }
  }

  private BankingDomicileEntity loadBankingDomicile(UUID id) {
    return bankingDomicileRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Domicílio bancário não encontrado: " + id));
  }

  private AcquirerEntity loadAcquirer(UUID id) {
    return acquirerRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Adquirente não encontrada: " + id));
  }
}
