package com.cardsync.domain.service;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.CsDefaultSecurityMethod;
import com.cardsync.core.util.TokenHash;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.InviteToken;
import com.cardsync.domain.model.ResetToken;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.repository.InviteTokenRepository;
import com.cardsync.domain.repository.ResetTokenRepository;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.infrastructure.mail.MailMessageResolver;
import com.cardsync.infrastructure.mail.MailTemplateUtils;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordTokenService extends CsDefaultSecurityMethod {

  private static final String INLINE_LOGO_CID = "cardsync-logo";
  private static final String INLINE_LOGO_PATH = "static/assets/cardsync-logo.png";
  private static final String INLINE_LOGO_CONTENT_TYPE = "image/png";

  private final Clock clock;
  private final CardsyncSecurityProperties props;
  private final UserRepository users;
  private final ResetTokenRepository resets;
  private final InviteTokenRepository invites;
  private final EmailSenderService emailSender;
  private final MailMessageResolver mailMessageResolver;
  private final MailTemplateUtils mailTemplateUtils;

  private final SecureRandom random = new SecureRandom();

  public record TokenPair(UUID userId, String rawToken) {}

  @Transactional
  public TokenPair createResetToken(String usernameOrEmailLike) {
    return createResetToken(usernameOrEmailLike, null);
  }

  @Transactional
  public TokenPair createResetToken(String usernameOrEmailLike, String baseUrlFromRequest) {
    var userOpt = users.findByUserNameIgnoreCase(usernameOrEmailLike);
    if (userOpt.isEmpty()) {
      return null;
    }

    UserEntity user = userOpt.get();

    assertResetRateLimit(user);
    invalidateOpenResetTokens(user.getId());

    String raw = generateToken();
    String hash = TokenHash.sha256Hex(raw);

    ResetToken rt = new ResetToken();
    rt.setUser(user);
    rt.setTokenHash(hash);
    rt.setCreatedAt(nowUtc());
    rt.setExpiresAt(nowUtc().plus(props.getPassword().getTokens().getResetTtl()));
    resets.save(rt);

    String prefix = effectiveBaseUrl(baseUrlFromRequest);
    String link = prefix + "/password/reset/" + user.getId() + "/" + raw;

    Locale locale = resolveLocale();
    String validity = mailTemplateUtils.formatDuration(
      props.getPassword().getTokens().getResetTtl(),
      locale
    );

    var message = EmailSenderService.Message.builder()
      .to(user.getUserName())
      .subject(mailMessageResolver.get("mail.reset.subject", locale))
      .template("mail/reset-senha-mail.html")
      .locale(locale)
      .requestedByUserId(getCurrentUserIdOrNull())
      .eventType(EmailLogEventTypeEnum.PASSWORD_RESET)
      .data("resetUrl", link)
      .data("validity", validity)
      .data("name", user.getName())
      .data("userName", user.getUserName())
      .data("logoCid", "cid:" + INLINE_LOGO_CID)
      .inline(
        EmailSenderService.InlineResource.builder()
          .contentId(INLINE_LOGO_CID)
          .resource(new ClassPathResource(INLINE_LOGO_PATH))
          .contentType(INLINE_LOGO_CONTENT_TYPE)
          .build()
      )
      .build();

    emailSender.sendThymeleaf(message);

    return new TokenPair(user.getId(), raw);
  }

  @Transactional
  public TokenPair createInviteToken(UUID userId) {
    return createInviteToken(userId, null);
  }

  @Transactional
  public TokenPair createInviteToken(UUID userId, String baseUrlFromRequest) {
    UserEntity user = users.findById(userId).orElseThrow();

    invalidateOpenInviteTokens(user.getId());

    String raw = generateToken();
    String hash = TokenHash.sha256Hex(raw);

    InviteToken it = new InviteToken();
    it.setUser(user);
    it.setTokenHash(hash);
    it.setCreatedAt(nowUtc());
    it.setExpiresAt(nowUtc().plus(props.getPassword().getTokens().getInviteTtl()));
    invites.save(it);

    String prefix = effectiveBaseUrl(baseUrlFromRequest);
    String link = prefix + "/password/set/" + user.getId() + "/" + raw;

    Locale locale = resolveLocale();
    String validity = mailTemplateUtils.formatDuration(
      props.getPassword().getTokens().getInviteTtl(),
      locale
    );

    var message = EmailSenderService.Message.builder()
      .to(user.getUserName())
      .subject(mailMessageResolver.get("mail.firstPassword.subject", locale))
      .template("mail/first-password-mail.html")
      .locale(locale)
      .requestedByUserId(getCurrentUserIdOrNull())
      .eventType(EmailLogEventTypeEnum.FIRST_PASSWORD)
      .data("setPasswordUrl", link)
      .data("validity", validity)
      .data("name", user.getName())
      .data("userName", user.getUserName())
      .data("logoCid", "cid:" + INLINE_LOGO_CID)
      .inline(
        EmailSenderService.InlineResource.builder()
          .contentId(INLINE_LOGO_CID)
          .resource(new ClassPathResource(INLINE_LOGO_PATH))
          .contentType(INLINE_LOGO_CONTENT_TYPE)
          .build()
      )
      .build();

    emailSender.sendThymeleaf(message);

    return new TokenPair(user.getId(), raw);
  }

  @Transactional(readOnly = true)
  public ResetToken validateReset(UUID userId, String rawToken) {
    String hash = TokenHash.sha256Hex(rawToken);
    ResetToken rt = resets.findByTokenHash(hash).orElse(null);

    if (rt == null) return null;
    if (!rt.getUser().getId().equals(userId)) return null;
    if (rt.getUsedAt() != null) return null;
    if (rt.getExpiresAt().isBefore(nowUtc())) return null;

    return rt;
  }

  @Transactional(readOnly = true)
  public InviteToken validateInvite(UUID userId, String rawToken) {
    String hash = TokenHash.sha256Hex(rawToken);
    InviteToken it = invites.findByTokenHash(hash).orElse(null);

    if (it == null) return null;
    if (!it.getUser().getId().equals(userId)) return null;
    if (it.getUsedAt() != null) return null;
    if (it.getExpiresAt().isBefore(nowUtc())) return null;

    return it;
  }

  @Transactional
  public void markUsed(ResetToken rt) {
    rt.setUsedAt(nowUtc());
  }

  @Transactional
  public void markUsed(InviteToken it) {
    it.setUsedAt(nowUtc());
  }

  @Transactional
  public void invalidateAllPasswordTokens(UUID userId) {
    OffsetDateTime now = nowUtc();

    resets.findAllByUserIdAndUsedAtIsNull(userId)
      .forEach(token -> token.setUsedAt(now));

    invites.findAllByUserIdAndUsedAtIsNull(userId)
      .forEach(token -> token.setUsedAt(now));
  }

  private void invalidateOpenResetTokens(UUID userId) {
    OffsetDateTime now = nowUtc();

    resets.findAllByUserIdAndUsedAtIsNull(userId)
      .forEach(token -> token.setUsedAt(now));
  }

  private void invalidateOpenInviteTokens(UUID userId) {
    OffsetDateTime now = nowUtc();

    invites.findAllByUserIdAndUsedAtIsNull(userId)
      .forEach(token -> token.setUsedAt(now));
  }

  private void assertResetRateLimit(UserEntity user) {
    var rateLimit = props.getPassword().getTokens().getRateLimit();

    if (rateLimit == null || !rateLimit.isEnabled()) {
      return;
    }

    OffsetDateTime threshold = nowUtc().minus(rateLimit.getWindow());
    long count = resets.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), threshold);

    if (count >= rateLimit.getMaxRequests()) {
      throw BusinessException.notFound(
        ErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED,
        "Too many password reset requests. Please try again later."
      );
    }
  }

  private Locale resolveLocale() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale != null ? locale : new Locale("pt", "BR");
  }

  private String effectiveBaseUrl(String baseUrlFromRequest) {
    String configured = props.getPassword().getTokens().getPublicBaseUrl();
    if (configured != null && !configured.isBlank()) return configured;
    if (baseUrlFromRequest != null && !baseUrlFromRequest.isBlank()) return baseUrlFromRequest;
    return "";
  }

  private String generateToken() {
    byte[] buf = new byte[32];
    random.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }
}