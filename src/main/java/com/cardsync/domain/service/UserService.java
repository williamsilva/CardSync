package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.UserInput;
import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.UsersFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.bff.controller.v1.representation.model.UserOptionModel;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.StatusUserEnum;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.domain.service.IntegrityErrorMapper.UserPersistenceErrorMapper;
import com.cardsync.domain.service.ValidationSupport.UserCommandValidator;
import com.cardsync.infrastructure.repository.spec.UserSpecs;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private static final String SUPPORT_GROUP_NAME = "SUPPORT";
  private static final String SUPPORT_USER_NAME = "suporte@cardsync.com.br";

  private final Clock clock;
  private final UserSpecs userSpecs;
  private final PasswordEncoder encoder;
  private final UserRepository usersRepository;
  private final UserCommandValidator validation;
  private final PasswordTokenService passwordTokens;
  private final CardsyncSecurityProperties securityProperties;
  private final UserPersistenceErrorMapper integrityErrorMapper;

  @Transactional(readOnly = true)
  public Page<UserEntity> list(Pageable pageable, ListQueryDto<UsersFilter> query) {
    Specification<UserEntity> spec = userSpecs.fromQuery(query);

    Page<UserEntity> page = usersRepository.findAll(spec, pageable);

    List<UUID> ids = page.getContent().stream()
      .map(UserEntity::getId)
      .toList();

    if (ids.isEmpty()) {
      return page;
    }

    var detailedById = usersRepository.findDetailedByIdIn(ids).stream()
      .collect(Collectors.toMap(UserEntity::getId, u -> u));

    List<UserEntity> ordered = page.getContent().stream()
      .map(u -> detailedById.getOrDefault(u.getId(), u))
      .toList();

    return new PageImpl<>(ordered, pageable, page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public List<UserOptionModel> listOptions() {
    return usersRepository
      .findAllByUserNameNotIgnoreCase(SUPPORT_USER_NAME, Sort.by(Sort.Direction.ASC, "name", "userName"))
      .stream()
      .map(u -> new UserOptionModel(u.getId(), u.getName(), u.getUserName()))
      .toList();
  }

  @Transactional(readOnly = true)
  public UserEntity getById(UUID userId) {
    return usersRepository.findDetailedById(userId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.USER_NOT_FOUND,
        "User not found for id " + userId
      ));
  }

  @Transactional
  public UserEntity create(UserInput input, String baseUrl) {
    String normalizedUserName = validation.requireEmail(input.userName());

    String docDigits = normalizeDocument(input.document());
    validation.requireNotBlank(docDigits, "document");

    if (usersRepository.existsByUserNameIgnoreCase(normalizedUserName)) {
      throw BusinessException.conflict(
        ErrorCode.USER_USERNAME_ALREADY_EXISTS,
        "User already exists with username/email: " + normalizedUserName
      );
    }

    if (usersRepository.existsByDocument(docDigits)) {
      throw BusinessException.conflict(
        ErrorCode.USER_DOCUMENT_ALREADY_EXISTS,
        "User already exists with document: " + docDigits
      );
    }

    Set<GroupEntity> groups = validation.loadGroups(input.groupIds());

    UserEntity u = new UserEntity();
    u.setUserName(normalizedUserName);
    u.setName(validation.requireTrim(input.name(), "name"));
    u.setDocument(docDigits);
    u.setStatusEnum(StatusUserEnum.PENDING_PASSWORD);
    u.setPasswordHash(encoder.encode(generateRandomSecret()));

    OffsetDateTime now = nowUtc();
    u.setPasswordChangedAt(null);
    u.setPasswordExpiresAt(now);
    u.setGroups(groups);

    try {
      usersRepository.save(u);
    } catch (DataIntegrityViolationException ex) {
      throw integrityErrorMapper.mapSaveError(ex, normalizedUserName, docDigits);
    }

    passwordTokens.createInviteToken(u.getId(), baseUrl);
    return u;
  }

  @Transactional
  public UserEntity update(UUID userId, UserInput input) {
    UserEntity user = getById(userId);

    ensureUserIsNotSupportForUpdate(user);

    String normalizedUserName = validation.requireEmail(input.userName());
    String docDigits = normalizeDocument(input.document());
    validation.requireNotBlank(docDigits, "document");

    if (usersRepository.existsByUserNameIgnoreCaseAndIdNot(normalizedUserName, userId)) {
      throw BusinessException.conflict(
        ErrorCode.USER_USERNAME_ALREADY_EXISTS,
        "Another user already exists with username/email: " + normalizedUserName
      );
    }

    if (usersRepository.existsByDocumentAndIdNot(docDigits, userId)) {
      throw BusinessException.conflict(
        ErrorCode.USER_DOCUMENT_ALREADY_EXISTS,
        "Another user already exists with document: " + docDigits
      );
    }

    Set<GroupEntity> groups = validation.loadGroups(input.groupIds());

    user.setName(validation.requireTrim(input.name(), "name"));
    user.setUserName(normalizedUserName);
    user.setDocument(docDigits);
    user.setGroups(groups);

    try {
      return usersRepository.save(user);
    } catch (DataIntegrityViolationException ex) {
      throw integrityErrorMapper.mapSaveError(ex, normalizedUserName, docDigits);
    }
  }

  @Transactional
  public void activate(UUID targetUserId) {
    UserEntity targetUser = getById(targetUserId);

    ensureUserIsNotProtectedForActivate(targetUser);
    ensureUserIsNotSupportForActivate(targetUser);
    ensureCanActivate(targetUser);

    targetUser.setStatusEnum(StatusUserEnum.ACTIVE);
    targetUser.setFailedAttempts(0);
    targetUser.setBlockedUntil(null);

    usersRepository.save(targetUser);
  }

  @Transactional
  public void deactivate(UUID targetUserId, Authentication authentication) {
    UserEntity targetUser = getById(targetUserId);

    ensureCannotDeactivateSelf(targetUser, authentication);
    ensureUserIsNotProtectedForDeactivate(targetUser);
    ensureUserIsNotSupportForDeactivate(targetUser);
    ensureCanDeactivate(targetUser);

    targetUser.setStatusEnum(StatusUserEnum.INACTIVE);
    targetUser.setFailedAttempts(0);
    targetUser.setBlockedUntil(null);

    usersRepository.save(targetUser);
  }

  @Transactional
  public void activateBulk(List<UUID> ids) {
    List<UserEntity> users = loadUsersForBulk(ids);

    for (UserEntity user : users) {
      ensureUserIsNotProtectedForActivate(user);
      ensureUserIsNotSupportForActivate(user);
      ensureCanActivate(user);

      user.setStatusEnum(StatusUserEnum.ACTIVE);
      user.setFailedAttempts(0);
      user.setBlockedUntil(null);
    }

    usersRepository.saveAll(users);
  }

  @Transactional
  public void deactivateBulk(List<UUID> ids, Authentication authentication) {
    List<UserEntity> users = loadUsersForBulk(ids);

    for (UserEntity user : users) {
      ensureCannotDeactivateSelf(user, authentication);
      ensureUserIsNotProtectedForDeactivate(user);
      ensureUserIsNotSupportForDeactivate(user);
      ensureCanDeactivate(user);

      user.setStatusEnum(StatusUserEnum.INACTIVE);
      user.setFailedAttempts(0);
      user.setBlockedUntil(null);
    }

    usersRepository.saveAll(users);
  }

  @Transactional
  public void resendInvite(UUID userId, String baseUrl) {
    UserEntity user = getById(userId);
    ensureCanResendInvite(user);

    passwordTokens.createInviteToken(user.getId(), baseUrl);
  }

  private List<UserEntity> loadUsersForBulk(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.USER_BULK_IDS_REQUIRED,
        "At least one user id must be informed"
      );
    }

    List<UUID> uniqueIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (uniqueIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.USER_BULK_IDS_REQUIRED,
        "At least one valid user id must be informed"
      );
    }

    List<UserEntity> users = usersRepository.findDetailedByIdIn(uniqueIds);
    Set<UUID> foundIds = users.stream()
      .map(UserEntity::getId)
      .collect(Collectors.toSet());

    List<UUID> missingIds = uniqueIds.stream()
      .filter(id -> !foundIds.contains(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.USER_NOT_FOUND,
        "User(s) not found for ids: " + missingIds
      );
    }

    return users;
  }

  private void ensureCanActivate(UserEntity user) {
    StatusUserEnum status = user.getStatusEnum();

    boolean activatable =
      status == StatusUserEnum.INACTIVE
        || status == StatusUserEnum.BLOCKED
        || status == StatusUserEnum.DISABLED;

    if (!activatable) {
      throw BusinessException.forbidden(
        ErrorCode.USER_ACTIVATE_INVALID_STATUS,
        "User cannot be activated from status " + status + ". userId=" + user.getId()
      );
    }
  }

  private void ensureCanDeactivate(UserEntity user) {
    StatusUserEnum status = user.getStatusEnum();

    boolean deactivatable =
      status == StatusUserEnum.ACTIVE
        || status == StatusUserEnum.PENDING_PASSWORD;

    if (!deactivatable) {
      throw BusinessException.forbidden(
        ErrorCode.USER_DEACTIVATE_INVALID_STATUS,
        "User cannot be deactivated from status " + status + ". userId=" + user.getId()
      );
    }
  }

  private void ensureCanResendInvite(UserEntity user) {
    if (user == null) {
      throw BusinessException.notFound(
        ErrorCode.USER_NOT_FOUND,
        "User is null while trying to resend invite"
      );
    }

    if (isInactive(user)) {
      throw BusinessException.forbidden(
        ErrorCode.INVITE_RESEND_USER_INACTIVE,
        "Cannot resend invite for inactive user: " + user.getId()
      );
    }

    if (user.getStatusEnum() != StatusUserEnum.PENDING_PASSWORD) {
      throw BusinessException.forbidden(
        ErrorCode.INVITE_RESEND_USER_ALREADY_ACTIVE,
        "Cannot resend invite because user is not pending password. userId=" + user.getId()
          + ", status=" + user.getStatusEnum()
      );
    }
  }

  private void ensureCannotDeactivateSelf(UserEntity targetUser, Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      return;
    }

    String loggedUsername = normalize(authentication.getName());
    String targetUsername = normalize(targetUser.getUserName());

    if (loggedUsername.equals(targetUsername)) {
      throw BusinessException.conflict(
        ErrorCode.USER_DEACTIVATE_SELF_NOT_ALLOWED,
        "Authenticated user attempted to deactivate himself"
      );
    }
  }

  private void ensureUserIsNotProtectedForActivate(UserEntity targetUser) {
    if (isProtectedUser(targetUser)) {
      throw BusinessException.forbidden(
        ErrorCode.USER_ACTIVATE_PROTECTED_NOT_ALLOWED,
        "Attempted to activate protected user: " + targetUser.getUserName()
      );
    }
  }

  private void ensureUserIsNotProtectedForDeactivate(UserEntity targetUser) {
    if (isProtectedUser(targetUser)) {
      throw BusinessException.forbidden(
        ErrorCode.USER_DEACTIVATE_PROTECTED_NOT_ALLOWED,
        "Attempted to deactivate protected user: " + targetUser.getUserName()
      );
    }
  }

  private void ensureUserIsNotSupportForUpdate(UserEntity user) {
    if (hasSupportGroup(user)) {
      throw BusinessException.forbidden(
        ErrorCode.USER_UPDATE_SUPPORT_NOT_ALLOWED,
        "Attempted to update user with SUPPORT group: " + user.getUserName()
      );
    }
  }

  private void ensureUserIsNotSupportForActivate(UserEntity user) {
    if (hasSupportGroup(user)) {
      throw BusinessException.forbidden(
        ErrorCode.USER_ACTIVATE_SUPPORT_NOT_ALLOWED,
        "Attempted to activate user with SUPPORT group: " + user.getUserName()
      );
    }
  }

  private void ensureUserIsNotSupportForDeactivate(UserEntity user) {
    if (hasSupportGroup(user)) {
      throw BusinessException.forbidden(
        ErrorCode.USER_DEACTIVATE_SUPPORT_NOT_ALLOWED,
        "Attempted to deactivate user with SUPPORT group: " + user.getUserName()
      );
    }
  }

  private boolean hasSupportGroup(UserEntity user) {
    return user != null
      && user.getGroups() != null
      && user.getGroups().stream()
      .map(GroupEntity::getName)
      .map(this::normalize)
      .anyMatch(SUPPORT_GROUP_NAME.toLowerCase()::equals);
  }

  private boolean isProtectedUser(UserEntity targetUser) {
    String targetUsername = normalize(targetUser.getUserName());

    return securityProperties.getProtectedUsernames()
      .stream()
      .map(this::normalize)
      .anyMatch(targetUsername::equals);
  }

  private boolean isInactive(UserEntity user) {
    StatusUserEnum status = user.getStatusEnum();
    return status == StatusUserEnum.INACTIVE || status == StatusUserEnum.DISABLED;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private String normalizeDocument(String document) {
    return document == null ? "" : document.replaceAll("\\D+", "");
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private String generateRandomSecret() {
    return UUID.randomUUID().toString() + UUID.randomUUID();
  }
}