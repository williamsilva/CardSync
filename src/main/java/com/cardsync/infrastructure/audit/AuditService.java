package com.cardsync.infrastructure.audit;

import com.cardsync.core.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final Clock clock;
  private final ObjectMapper om;
  private final AuditRepository repo;

  @Transactional
  public void log(AuditEventType type, Authentication auth, HttpServletRequest req, Object payload) {
    AuditEvent ev = new AuditEvent();
    ev.setEventType(type.name());
    ev.setPrincipal(auth != null ? auth.getName() : null);
    ev.setIp(req != null ? req.getRemoteAddr() : null);
    ev.setUserAgent(req != null ? req.getHeader("User-Agent") : null);

    Object cid = req != null ? req.getAttribute(CorrelationIdFilter.ATTR) : null;
    ev.setCorrelationId(cid instanceof java.util.UUID u ? u : null);

    ev.setPayloadJson(payload != null ? toJson(payload) : null);
    ev.setCreatedAt(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
    repo.save(ev);
  }

  private String toJson(Object payload) {
    try {
      return om.writeValueAsString(payload);
    } catch (Exception e) {
      return "{\"_error\":\"payload_serialization_failed\"}";
    }
  }

}
