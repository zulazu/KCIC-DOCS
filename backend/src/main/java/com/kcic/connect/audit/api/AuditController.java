package com.kcic.connect.audit.api;

import com.kcic.connect.audit.api.AuditDtos.AuditEventResponse;
import com.kcic.connect.audit.application.AuditQueryService;
import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/api/v1/audit-events")
    public PageResponse<AuditEventResponse> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.search(UserPrincipal.require(principal).userId(), actor, action, resourceType,
                resourceId, outcome, from, to, page, size);
    }
}
