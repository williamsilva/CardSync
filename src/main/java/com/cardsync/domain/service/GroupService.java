package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.GroupInput;
import com.cardsync.bff.controller.v1.representation.model.GroupOptionModel;
import com.cardsync.bff.controller.v1.representation.model.PermissionOptionModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.GroupsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.domain.model.PermissionEntity;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.GroupRepository;
import com.cardsync.domain.repository.PermissionRepository;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.domain.service.IntegrityErrorMapper.GroupPersistenceErrorMapper;
import com.cardsync.domain.service.ValidationSupport.GroupCommandValidator;
import com.cardsync.infrastructure.repository.spec.GroupSpecs;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

  private static final String EXCLUDED_GROUP_NAME = "SUPPORT";
  private static final String EXCLUDED_PERMISSION_NAME = "SUPPORT";

  private final GroupSpecs groupSpecs;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final GroupCommandValidator validation;
  private final PermissionRepository permissionRepository;
  private final GroupPersistenceErrorMapper integrityErrorMapper;

  @Transactional(readOnly = true)
  public GroupEntity getById(UUID groupId) {
    return groupRepository.findDetailedById(groupId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.GROUP_NOT_FOUND,
        "Group not found for id " + groupId
      ));
  }

  @Transactional(readOnly = true)
  public List<GroupOptionModel> listSelectableGroups() {
    return groupRepository.findAllByNameNotIgnoreCaseOrderByNameAsc(EXCLUDED_GROUP_NAME)
      .stream()
      .map(g -> new GroupOptionModel(g.getId(), g.getName(), g.getDescription()))
      .toList();
  }

  @Transactional(readOnly = true)
  public List<PermissionOptionModel> listPermissionOptions() {
    return permissionRepository.findAllByNameNotIgnoreCaseOrderByNameAsc(EXCLUDED_PERMISSION_NAME)
      .stream()
      .map(p -> new PermissionOptionModel(p.getId(), p.getName(), p.getDescription()))
      .toList();
  }

  @Transactional(readOnly = true)
  public Page<GroupEntity> list(Pageable pageable, ListQueryDto<GroupsFilter> query) {
    Specification<GroupEntity> spec = groupSpecs.fromQuery(query);
    Page<GroupEntity> page = groupRepository.findAll(spec, pageable);

    List<UUID> ids = page.getContent().stream().map(GroupEntity::getId).toList();
    if (ids.isEmpty()) {
      return page;
    }

    var detailedById = groupRepository.findDetailedByIdIn(ids).stream()
      .collect(Collectors.toMap(GroupEntity::getId, g -> g));

    List<GroupEntity> ordered = page.getContent().stream()
      .map(g -> detailedById.getOrDefault(g.getId(), g))
      .toList();

    return new PageImpl<>(ordered, pageable, page.getTotalElements());
  }

  @Transactional
  public GroupEntity create(GroupInput input) {
    String normalizedName = validation.requireName(input.name());

    if (groupRepository.existsByNameIgnoreCase(normalizedName)) {
      throw BusinessException.conflict(
        ErrorCode.GROUP_NAME_ALREADY_EXISTS,
        "Group already exists with name: " + normalizedName
      );
    }

    GroupEntity g = new GroupEntity();
    g.setName(validation.requireTrim(input.name(), "name"));
    g.setDescription(input.description() == null ? null : input.description().trim());

    try {
      return groupRepository.save(g);
    } catch (DataIntegrityViolationException ex) {
      throw integrityErrorMapper.mapSaveError(ex, normalizedName);
    }
  }

  @Transactional
  public GroupEntity update(UUID groupId, GroupInput input) {
    GroupEntity group = getById(groupId);
    ensureNotSupportGroup(group);

    String normalizedName = validation.requireName(input.name());

    if (groupRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, groupId)) {
      throw BusinessException.conflict(
        ErrorCode.GROUP_NAME_ALREADY_EXISTS,
        "Another group already exists with name: " + normalizedName
      );
    }

    group.setName(validation.requireTrim(input.name(), "name"));
    group.setDescription(input.description() == null ? null : input.description().trim());

    try {
      return groupRepository.save(group);
    } catch (DataIntegrityViolationException ex) {
      throw integrityErrorMapper.mapSaveError(ex, normalizedName);
    }
  }

  @Transactional
  public void delete(UUID groupId) {
    GroupEntity group = getById(groupId);
    ensureNotSupportGroup(group);

    if (group.getUsers() != null && !group.getUsers().isEmpty()) {
      throw BusinessException.conflict(
        ErrorCode.GROUP_DELETE_IN_USE,
        "Group has users associated and cannot be deleted. groupId=" + groupId
      );
    }

    groupRepository.delete(group);
  }

  @Transactional
  public GroupEntity updatePermissions(UUID groupId, List<UUID> permissionIds) {
    GroupEntity group = getById(groupId);
    ensureNotSupportGroup(group);
    List<UUID> ids = distinctIds(permissionIds);
    List<PermissionEntity> permissions = ids.isEmpty() ? List.of() : permissionRepository.findAllById(ids);

    if (permissions.size() != ids.size()) {
      Set<UUID> foundIds = permissions.stream().map(PermissionEntity::getId).collect(Collectors.toSet());
      List<UUID> missing = ids.stream().filter(id -> !foundIds.contains(id)).toList();
      throw BusinessException.notFound(ErrorCode.PERMISSION_NOT_FOUND, "Permission(s) not found for ids: " + missing);
    }

    boolean containsSupportPermission = permissions.stream()
      .anyMatch(permission -> permission.getName() != null && EXCLUDED_PERMISSION_NAME.equalsIgnoreCase(permission.getName()));

    if (containsSupportPermission) {
      throw BusinessException.forbidden(
        ErrorCode.GROUP_DELETE_SUPPORT_NOT_ALLOWED,
        "Support permission cannot be assigned to another group. groupId=" + groupId
      );
    }

    group.setPermissions(new LinkedHashSet<>(permissions));
    return groupRepository.save(group);
  }

  @Transactional
  public GroupEntity updateUsers(UUID groupId, List<UUID> userIds) {
    GroupEntity group = getById(groupId);
    ensureNotSupportGroup(group);
    List<UUID> ids = distinctIds(userIds);
    List<UserEntity> users = ids.isEmpty() ? List.of() : userRepository.findDetailedByIdIn(ids);

    if (users.size() != ids.size()) {
      Set<UUID> foundIds = users.stream().map(UserEntity::getId).collect(Collectors.toSet());
      List<UUID> missing = ids.stream().filter(id -> !foundIds.contains(id)).toList();
      throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND, "User(s) not found for ids: " + missing);
    }

    group.setUsers(new LinkedHashSet<>(users));
    return groupRepository.save(group);
  }

  private List<UUID> distinctIds(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
  }

  private void ensureNotSupportGroup(GroupEntity group) {
    if (group.getName() != null && EXCLUDED_GROUP_NAME.equalsIgnoreCase(group.getName())) {
      throw BusinessException.forbidden(
        ErrorCode.GROUP_DELETE_SUPPORT_NOT_ALLOWED,
        "Support group cannot be deleted or reassigned. groupId=" + group.getId()
      );
    }
  }
}
