package com.cardsync.api.internal.controller;

import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.repository.EmailLogRepository;
import com.nimbussystems.commons.legacy.model.enums.EmailLogEventTypeEnum;
import com.nimbussystems.commons.legacy.model.enums.EmailLogStatusEnum;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API interna machine-to-machine (rota /internal/email-log/**, ver InternalBackupSecretFilter/
 *  internalEmailLogChain em SecurityConfig) - consumida pelo NimbusCore pra federar a tela central
 *  de Auditoria de E-mail.
 *
 * <p>Deliberadamente NÃO reaproveita EmailLogService.list/EmailLogSpecs.fromQuery (o caminho que
 * o BffEmailLogController usa) - achado durante o teste manual deste endpoint:
 * EmailLogSpecs.fromQuery chama {@code contains("subject", a.subject())} (mesmo padrão em
 * CompanySpecs/FlagSpecs/HolidaySpecs), mas BaseSpecificationSupport#contains(String value,
 * String field) espera (valor, nomeDoCampo) - os dois argumentos estão trocados em todos esses
 * call sites, então SEMPRE que o filtro vier com algum campo preenchido (ou mesmo null, dependendo
 * do caminho) a Specification tenta {@code root.get(<valor digitado ou null>)}, que quebra com
 * "Could not resolve attribute". Bug pré-existente, fora do escopo desta federação - reportado à
 * parte; aqui monta-se uma Specification própria e correta, direto no repositório. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/email-log")
public class InternalEmailLogController {

  /** Allow-list de ordenação (sortField do request -> propriedade real da entidade) - os únicos
   *  campos que o painel "Auditoria dos Apps" do NimbusCoreWeb expõe pra sort. Campo ausente ou
   *  desconhecido cai no fallback (sentAt desc), mesmo comportamento de antes desta feature. */
  private static final Map<String, String> SORTABLE_FIELDS = Map.of(
      "recipient", "recipient",
      "subject", "subject",
      "eventType", "eventType",
      "status", "status",
      "sentAt", "sentAt");

  private final EmailLogRepository repository;

  @GetMapping("/search")
  public PageModel search(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String recipient,
      @RequestParam(required = false) String subject,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sentAtFrom,
      @RequestParam(required = false) String sentAtTo,
      @RequestParam(required = false) String sortField,
      @RequestParam(required = false) String sortOrder) {

    OffsetDateTime from = parseOffsetDateTime(sentAtFrom);
    OffsetDateTime to = parseOffsetDateTime(sentAtTo);
    Integer statusCode = statusCode(status);
    Integer eventTypeCode = eventTypeCode(eventType);

    Specification<EmailLogEntity> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (recipient != null && !recipient.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("recipient")), "%" + recipient.toLowerCase(Locale.ROOT) + "%"));
      }
      if (subject != null && !subject.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("subject")), "%" + subject.toLowerCase(Locale.ROOT) + "%"));
      }
      if (eventTypeCode != null) {
        predicates.add(cb.equal(root.get("eventType"), eventTypeCode));
      }
      if (statusCode != null) {
        predicates.add(cb.equal(root.get("status"), statusCode));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };

    Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size, resolveSort(sortField, sortOrder));
    Page<EmailLogEntity> result = repository.findAll(spec, pageable);
    List<ItemModel> content = result.getContent().stream().map(InternalEmailLogController::toItem).toList();

    return new PageModel(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  private static Sort resolveSort(String sortField, String sortOrder) {
    // SORTABLE_FIELDS.get(null) lanca NullPointerException (Map.of() nao aceita chave nula,
    // diferente de HashMap) - achado real em producao: sortField vem null na carga inicial da
    // tela (antes do usuario clicar numa coluna), derrubando a busca inteira com 500, nao so a
    // ordenacao. Ver mesmo fix em NimbusDeskServer/NimbusNovaxServer.
    String property = sortField == null ? null : SORTABLE_FIELDS.get(sortField);
    if (property == null) {
      return Sort.by(Sort.Direction.DESC, "sentAt");
    }
    Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }

  private static Integer statusCode(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return EmailLogStatusEnum.valueOf(value).getCode();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Integer eventTypeCode(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return EmailLogEventTypeEnum.valueOf(value).getCode();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static OffsetDateTime parseOffsetDateTime(String value) {
    return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
  }

  private static ItemModel toItem(EmailLogEntity e) {
    return new ItemModel(
        e.getRecipient(),
        e.getSubject(),
        e.getTemplate(),
        e.getStatus() == null ? null : e.getStatus().name(),
        e.getEventType() == null ? null : e.getEventType().name(),
        e.getErrorMessage(),
        e.getSentAt() == null ? null : e.getSentAt().toString());
  }

  public record ItemModel(
      String recipients, String subject, String template, String status, String eventType,
      String errorMessage, String sentAt) {}

  public record PageModel(List<ItemModel> content, int number, int size, long totalElements, int totalPages) {}
}
