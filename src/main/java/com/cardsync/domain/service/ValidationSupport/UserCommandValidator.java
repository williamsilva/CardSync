package com.cardsync.domain.service.ValidationSupport;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.domain.repository.GroupRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCommandValidator {

  private static final Pattern EMAIL_PATTERN =
    Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

  private final GroupRepository groupsRepository;

  public String requireEmail(String value) {
    String email = value == null ? "" : value.trim().toLowerCase();

    if (email.isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.INVALID_EMAIL,
        "E-mail is required"
      );
    }

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw BusinessException.badRequest(
        ErrorCode.INVALID_EMAIL,
        "Invalid e-mail: " + value
      );
    }

    return email;
  }

  public String requireTrim(String value, String field) {
    String v = value == null ? "" : value.trim();
    if (!v.isBlank()) {
      return v;
    }

    ErrorCode code = switch (field) {
      case "name" -> ErrorCode.NAME_REQUIRED;
      default -> ErrorCode.VALIDATION_ERROR;
    };

    throw BusinessException.badRequest(
      code,
      "Required field is blank: " + field
    );
  }

  public void requireNotBlank(String value, String field) {
    if (value != null && !value.trim().isBlank()) {
      return;
    }

    ErrorCode code = switch (field) {
      case "document" -> ErrorCode.DOCUMENT_REQUIRED;
      case "name" -> ErrorCode.NAME_REQUIRED;
      default -> ErrorCode.VALIDATION_ERROR;
    };

    throw BusinessException.badRequest(
      code,
      "Required field is blank: " + field
    );
  }

  public void requireGroupIds(Set<UUID> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.GROUP_REQUIRED,
        "At least one group must be informed"
      );
    }
  }

  public Set<GroupEntity> loadGroups(Set<UUID> groupIds) {
    requireGroupIds(groupIds);

    Set<GroupEntity> groups = new HashSet<>();
    for (UUID gid : groupIds) {
      GroupEntity group = groupsRepository.findById(gid)
        .orElseThrow(() -> BusinessException.notFound(
          ErrorCode.GROUP_NOT_FOUND,
          "Group not found for id " + gid
        ));
      groups.add(group);
    }
    return groups;
  }
}
