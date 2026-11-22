package com.kcic.connect.workflow.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.workflow.api.RequestDtos.CreateRequestRequest;
import com.kcic.connect.workflow.api.RequestDtos.DecisionRequest;
import com.kcic.connect.workflow.api.RequestDtos.FulfillRequest;
import com.kcic.connect.workflow.api.RequestDtos.RequestDetailResponse;
import com.kcic.connect.workflow.api.RequestDtos.RequestSummaryResponse;
import com.kcic.connect.workflow.application.RequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping("/api/v1/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody CreateRequestRequest payload) {
        return requestService.create(UserPrincipal.require(principal).userId(), payload);
    }

    @GetMapping("/api/v1/requests")
    public PageResponse<RequestSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestParam(defaultValue = "own") String scope,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return requestService.list(UserPrincipal.require(principal).userId(), scope, status, type, keyword,
                page, size);
    }

    @GetMapping("/api/v1/requests/{requestId}")
    public RequestDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID requestId) {
        return requestService.detail(UserPrincipal.require(principal).userId(), requestId);
    }

    @PostMapping("/api/v1/requests/{requestId}/start-review")
    public RequestDetailResponse startReview(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID requestId,
                                             @RequestBody(required = false) DecisionRequest payload) {
        return requestService.startReview(UserPrincipal.require(principal).userId(), requestId,
                payload == null ? null : payload.comment());
    }

    @PostMapping("/api/v1/requests/{requestId}/approve")
    public RequestDetailResponse approve(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID requestId,
                                         @RequestBody(required = false) DecisionRequest payload) {
        return requestService.approve(UserPrincipal.require(principal).userId(), requestId,
                payload == null ? null : payload.comment());
    }

    @PostMapping("/api/v1/requests/{requestId}/reject")
    public RequestDetailResponse reject(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID requestId,
                                        @Valid @RequestBody DecisionRequest payload) {
        return requestService.reject(UserPrincipal.require(principal).userId(), requestId, payload.comment());
    }

    @PostMapping("/api/v1/requests/{requestId}/fulfill")
    public RequestDetailResponse fulfill(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID requestId,
                                         @Valid @RequestBody FulfillRequest payload) {
        return requestService.fulfill(UserPrincipal.require(principal).userId(), requestId, payload);
    }

    @PostMapping(value = "/api/v1/requests/{requestId}/fulfill/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RequestDetailResponse fulfillUpload(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable UUID requestId,
                                               @RequestParam("documentId") UUID documentId,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String note,
                                               @RequestParam(required = false) String versionLabel) {
        return requestService.fulfillUpload(UserPrincipal.require(principal).userId(), requestId,
                documentId, file, note, versionLabel);
    }

    @PostMapping("/api/v1/requests/{requestId}/cancel")
    public RequestDetailResponse cancel(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID requestId,
                                        @RequestBody(required = false) DecisionRequest payload) {
        return requestService.cancel(UserPrincipal.require(principal).userId(), requestId,
                payload == null ? null : payload.comment());
    }

    @PostMapping("/api/v1/requests/{requestId}/close")
    public RequestDetailResponse close(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID requestId,
                                       @RequestBody(required = false) DecisionRequest payload) {
        return requestService.close(UserPrincipal.require(principal).userId(), requestId,
                payload == null ? null : payload.comment());
    }
}
