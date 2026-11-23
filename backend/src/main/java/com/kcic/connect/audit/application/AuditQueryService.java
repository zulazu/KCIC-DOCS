package com.kcic.connect.audit.application;

import com.kcic.connect.audit.api.AuditDtos.AuditEventResponse;
import com.kcic.connect.audit.domain.AuditEvent;
import com.kcic.connect.audit.infrastructure.AuditEventRepository;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import com.kcic.connect.shared.api.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit search for principals holding {@code audit.view}. The pilot scope is
 * global: governance roles (SYSTEM_ADMIN, ADMINISTRATOR), ADMIN, and AUDITOR
 * see all business events, matching the seeded role model in V11/V19.
 */
@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public AuditQueryService(AuditEventRepository auditEventRepository,
                             UserRepository userRepository,
                             AuthorizationService authorizationService) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(UUID viewerId, String actor, String action,
                                                   String resourceType, UUID resourceId, String outcome,
                                                   LocalDate from, LocalDate to, int page, int size) {
        if (!authorizationService.hasCapability(viewerId, PermissionCodes.AUDIT_VIEW)) {
            throw ApiException.forbidden("Anda tidak memiliki izin melihat audit trail.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validation("from", "Tanggal awal tidak boleh melewati tanggal akhir.");
        }
        Specification<AuditEvent> specification = specification(actor, action, resourceType, resourceId,
                outcome, from, to);
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Page<AuditEvent> events = auditEventRepository.findAll(specification, pageable);

        Map<UUID, User> actors = new HashMap<>();
        List<UUID> actorIds = events.getContent().stream()
                .map(AuditEvent::getActorUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!actorIds.isEmpty()) {
            userRepository.findAllById(actorIds).forEach(user -> actors.put(user.getId(), user));
        }
        return PageResponse.of(events.map(event -> response(event, actors.get(event.getActorUserId()))));
    }

    private Specification<AuditEvent> specification(String actor, String action, String resourceType,
                                                    UUID resourceId, String outcome,
                                                    LocalDate from, LocalDate to) {
        Specification<AuditEvent> specification = (root, query, builder) -> builder.conjunction();
        String actorKeyword = trimToNull(actor);
        if (actorKeyword != null) {
            List<UUID> actorIds = userRepository.findIdsByActorKeyword(actorKeyword);
            specification = actorIds.isEmpty()
                    ? specification.and((root, query, builder) -> builder.disjunction())
                    : specification.and((root, query, builder) -> root.get("actorUserId").in(actorIds));
        }
        String actionCode = upperOrNull(action);
        if (actionCode != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("actionCode"), actionCode));
        }
        String type = upperOrNull(resourceType);
        if (type != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("resourceType"), type));
        }
        if (resourceId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("resourceId"), resourceId));
        }
        String result = upperOrNull(outcome);
        if (result != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("outcome"), result));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), start));
        }
        if (to != null) {
            Instant endExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.<Instant>get("occurredAt"), endExclusive));
        }
        return specification;
    }

    private AuditEventResponse response(AuditEvent event, User actor) {
        return new AuditEventResponse(event.getId(), event.getActorUserId(),
                actor == null ? null : actor.getUsername(),
                actor == null ? null : actor.getDisplayName(),
                event.getActionCode(), event.getResourceType(), event.getResourceId(), event.getOutcome(),
                event.getSource(), event.getRequestId(),
                event.getSafeMetadata() == null ? Map.of() : event.getSafeMetadata(),
                event.getOccurredAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
