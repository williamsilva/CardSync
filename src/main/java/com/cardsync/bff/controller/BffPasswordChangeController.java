package com.cardsync.bff.controller;

import com.cardsync.api.v1.mapper.model.ChangeMyPasswordModel;
import com.cardsync.core.security.password.PasswordService;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.infrastructure.audit.AuditEventType;
import com.cardsync.infrastructure.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/me")
public class BffPasswordChangeController {

  private final PasswordEncoder encoder;
  private final AuditService auditService;
  private final UserRepository usersRepository;
  private final PasswordService passwordService;

  @PutMapping("/password/change")
  public ResponseEntity<Void> changeMyPassword(
    @Valid @RequestBody ChangeMyPasswordModel request,
    Authentication authentication,
    HttpServletRequest httpRequest
  ) {
    String username = authentication != null ? authentication.getName() : null;

    if (username == null || username.isBlank()) {
      auditService.log(AuditEventType.change_password_fail, authentication, httpRequest,
        "{\"reason\":\"unauthenticated\"}");

      throw BusinessException.forbidden(
        ErrorCode.ACCESS_DENIED,
        "Unauthenticated user tried to change password"
      );
    }

    UserEntity user = usersRepository.findByUserNameIgnoreCase(username)
      .orElseThrow(() -> {
        auditService.log(AuditEventType.change_password_fail, authentication, httpRequest,
          "{\"reason\":\"user_not_found\",\"username\":\"" + safeJson(username) + "\"}");

        return BusinessException.notFound(
          ErrorCode.USER_NOT_FOUND,
          "Authenticated user not found for username: " + username
        );
      });

    if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
      auditService.log(
        AuditEventType.change_password_fail, authentication, httpRequest,
        "{\"reason\":\"current_password_invalid\",\"userId\":\"" + user.getId() + "\"}");

      throw BusinessException.badRequest(
        ErrorCode.PASSWORD_CURRENT_INVALID,
        "Current password does not match"
      );
    }

    try {
      passwordService.changePassword(user, request.newPassword(),request.confirmPassword());
      usersRepository.save(user);

      auditService.log(AuditEventType.change_password_success, authentication, httpRequest,
        "{\"userId\":\"" + user.getId() + "\"}");

      return ResponseEntity.noContent().build();

    } catch (IllegalArgumentException ex) {
      auditService.log(AuditEventType.change_password_fail, authentication,
        httpRequest, "{\"reason\":\"policy_validation_failed\",\"userId\":\"" + user.getId() + "\"}");

      throw BusinessException.badRequest(
        ErrorCode.PASSWORD_POLICY_INVALID,
        ex.getMessage()
      );
    }
  }

  private static String safeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
