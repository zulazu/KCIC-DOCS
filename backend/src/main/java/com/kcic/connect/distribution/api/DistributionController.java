package com.kcic.connect.distribution.api;

import com.kcic.connect.distribution.api.DistributionDtos.CreateDistributionRequest;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionDetailResponse;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionSummaryResponse;
import com.kcic.connect.distribution.application.DistributionService;
import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DistributionController {

    private final DistributionService distributionService;

    public DistributionController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @PostMapping("/api/v1/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    public DistributionDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody CreateDistributionRequest payload) {
        return distributionService.create(UserPrincipal.require(principal).userId(), payload);
    }

    @GetMapping("/api/v1/distributions")
    public PageResponse<DistributionSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(defaultValue = "received") String box,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return distributionService.list(UserPrincipal.require(principal).userId(), box, status, keyword, page,
                size);
    }

    @GetMapping("/api/v1/distributions/{distributionId}")
    public DistributionDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID distributionId) {
        return distributionService.detail(UserPrincipal.require(principal).userId(), distributionId);
    }

    @PostMapping("/api/v1/distributions/{distributionId}/revoke")
    public DistributionDetailResponse revoke(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID distributionId) {
        return distributionService.revoke(UserPrincipal.require(principal).userId(), distributionId);
    }

    @PostMapping("/api/v1/distributions/{distributionId}/recipients/{recipientId}/revoke")
    public DistributionDetailResponse revokeRecipient(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable UUID distributionId,
                                                      @PathVariable UUID recipientId) {
        return distributionService.revokeRecipient(UserPrincipal.require(principal).userId(), distributionId,
                recipientId);
    }
}
