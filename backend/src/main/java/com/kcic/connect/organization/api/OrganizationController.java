package com.kcic.connect.organization.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.organization.api.OrganizationDtos.CreateOrganizationUnitRequest;
import com.kcic.connect.organization.api.OrganizationDtos.OrganizationUnitResponse;
import com.kcic.connect.organization.api.OrganizationDtos.UnitMemberResponse;
import com.kcic.connect.organization.api.OrganizationDtos.UpdateOrganizationUnitRequest;
import com.kcic.connect.organization.application.OrganizationService;
import com.kcic.connect.organization.domain.UnitType;
import jakarta.validation.Valid;
import java.util.List;
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
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/api/v1/organization-units")
    public List<OrganizationUnitResponse> activeUnits(@RequestParam(required = false) UnitType unitType) {
        return organizationService.listActive(unitType);
    }

    @GetMapping("/api/v1/admin/organization-units")
    public List<OrganizationUnitResponse> allUnits(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam(required = false) UnitType unitType) {
        return organizationService.listAll(UserPrincipal.require(principal).userId(), unitType);
    }

    @GetMapping("/api/v1/admin/organization-units/{unitId}")
    public OrganizationUnitResponse unit(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID unitId) {
        return organizationService.get(UserPrincipal.require(principal).userId(), unitId);
    }

    @PostMapping("/api/v1/admin/organization-units")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationUnitResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                           @Valid @RequestBody CreateOrganizationUnitRequest request) {
        return organizationService.create(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/admin/organization-units/{unitId}")
    public OrganizationUnitResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID unitId,
                                           @Valid @RequestBody UpdateOrganizationUnitRequest request) {
        return organizationService.update(UserPrincipal.require(principal).userId(), unitId, request);
    }

    @GetMapping("/api/v1/admin/organization-units/{unitId}/members")
    public List<UnitMemberResponse> members(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID unitId) {
        return organizationService.members(UserPrincipal.require(principal).userId(), unitId);
    }
}
