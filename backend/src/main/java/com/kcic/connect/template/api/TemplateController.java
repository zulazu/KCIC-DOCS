package com.kcic.connect.template.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.template.api.TemplateDtos.CreateTemplateRequest;
import com.kcic.connect.template.api.TemplateDtos.PublishTemplateRequest;
import com.kcic.connect.template.api.TemplateDtos.RecordTemplateUsageRequest;
import com.kcic.connect.template.api.TemplateDtos.TemplateDetailResponse;
import com.kcic.connect.template.api.TemplateDtos.TemplateSummaryResponse;
import com.kcic.connect.template.api.TemplateDtos.TemplateUsageResponse;
import com.kcic.connect.template.api.TemplateDtos.UpdateTemplateRequest;
import com.kcic.connect.template.application.TemplateService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/api/v1/templates")
    public PageResponse<TemplateSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) UUID categoryId,
                                                      @RequestParam(required = false) UUID ownerUserId,
                                                      @RequestParam(required = false)
                                                      UUID organizationUnitId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return templateService.list(UserPrincipal.require(principal).userId(), keyword, status, categoryId,
                ownerUserId, organizationUnitId, page, size);
    }

    @PostMapping("/api/v1/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody CreateTemplateRequest payload) {
        return templateService.create(UserPrincipal.require(principal).userId(), payload);
    }

    @GetMapping("/api/v1/templates/{templateId}")
    public TemplateDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID templateId) {
        return templateService.detail(UserPrincipal.require(principal).userId(), templateId);
    }

    @PutMapping("/api/v1/templates/{templateId}")
    public TemplateDetailResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID templateId,
                                         @Valid @RequestBody UpdateTemplateRequest payload) {
        return templateService.update(UserPrincipal.require(principal).userId(), templateId, payload);
    }

    @PostMapping("/api/v1/templates/{templateId}/publish")
    public TemplateDetailResponse publish(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID templateId,
                                          @RequestBody(required = false) PublishTemplateRequest payload) {
        return templateService.publish(UserPrincipal.require(principal).userId(), templateId,
                payload == null ? null : payload.versionId());
    }

    @PostMapping("/api/v1/templates/{templateId}/archive")
    public TemplateDetailResponse archive(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID templateId) {
        return templateService.archive(UserPrincipal.require(principal).userId(), templateId);
    }

    @PostMapping("/api/v1/templates/{templateId}/usage")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateUsageResponse recordUsage(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID templateId,
                                             @Valid @RequestBody RecordTemplateUsageRequest payload) {
        return templateService.recordUsage(UserPrincipal.require(principal).userId(), templateId, payload);
    }
}
