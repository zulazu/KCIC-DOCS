package com.kcic.connect.identity.api;

import com.kcic.connect.identity.api.UserDtos.AssignUnitRequest;
import com.kcic.connect.identity.api.UserDtos.ChangeStatusRequest;
import com.kcic.connect.identity.api.UserDtos.CreateUserRequest;
import com.kcic.connect.identity.api.UserDtos.ResetPasswordRequest;
import com.kcic.connect.identity.api.UserDtos.UpdateUserRequest;
import com.kcic.connect.identity.api.UserDtos.UserDetailResponse;
import com.kcic.connect.identity.api.UserDtos.UserSummaryResponse;
import com.kcic.connect.identity.application.UserAdminService;
import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.organization.api.OrganizationDtos.OrganizationUnitResponse;
import com.kcic.connect.organization.api.OrganizationDtos.UnitMemberResponse;
import com.kcic.connect.organization.application.OrganizationService;
import com.kcic.connect.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final OrganizationService organizationService;

    public UserAdminController(UserAdminService userAdminService, OrganizationService organizationService) {
        this.userAdminService = userAdminService;
        this.organizationService = organizationService;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) AccountStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return userAdminService.search(UserPrincipal.require(principal).userId(), keyword, status, page, size);
    }

    @GetMapping("/{userId}")
    public UserDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID userId) {
        return userAdminService.detail(UserPrincipal.require(principal).userId(), userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody CreateUserRequest request) {
        return userAdminService.create(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/{userId}")
    public UserDetailResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID userId,
                                     @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.update(UserPrincipal.require(principal).userId(), userId, request);
    }

    @PostMapping("/{userId}/status")
    public UserSummaryResponse changeStatus(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID userId,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return userAdminService.changeStatus(UserPrincipal.require(principal).userId(), userId,
                request.status());
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal UserPrincipal principal,
                              @PathVariable UUID userId,
                              @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(UserPrincipal.require(principal).userId(), userId, request);
    }

    @PostMapping("/{userId}/unlock")
    public UserSummaryResponse unlock(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID userId) {
        return userAdminService.unlock(UserPrincipal.require(principal).userId(), userId);
    }

    @GetMapping("/{userId}/units")
    public List<OrganizationUnitResponse> units(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID userId) {
        UUID actor = UserPrincipal.require(principal).userId();
        return organizationService.unitsOfUser(actor, userId);
    }

    @PostMapping("/{userId}/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitMemberResponse assignUnit(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID userId,
                                         @Valid @RequestBody AssignUnitRequest request) {
        return organizationService.assignUser(UserPrincipal.require(principal).userId(), userId, request);
    }

    @DeleteMapping("/{userId}/units/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endUnitAssignment(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID userId,
                                  @PathVariable UUID assignmentId) {
        organizationService.endAssignment(UserPrincipal.require(principal).userId(), userId, assignmentId);
    }
}
