package com.kcic.connect.employee.api;

import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeAccountRequest;
import com.kcic.connect.employee.api.EmployeeDtos.LinkedAccountView;
import com.kcic.connect.employee.api.EmployeeDtos.LinkEmployeeAccountRequest;
import com.kcic.connect.employee.application.EmployeeAccountService;
import com.kcic.connect.identity.api.UserDtos.UserSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class EmployeeAccountAdminController {

    private final EmployeeAccountService employeeAccountService;

    public EmployeeAccountAdminController(EmployeeAccountService employeeAccountService) {
        this.employeeAccountService = employeeAccountService;
    }

    @GetMapping("/users/unlinked")
    public PageResponse<UserSummaryResponse> unlinked(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return employeeAccountService.unlinkedAccounts(UserPrincipal.require(principal).userId(),
                keyword, page, size);
    }

    @PostMapping("/employees/{employeeId}/account")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkedAccountView createAccount(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID employeeId,
                                           @Valid @RequestBody CreateEmployeeAccountRequest request) {
        return employeeAccountService.createAccount(UserPrincipal.require(principal).userId(), employeeId,
                request);
    }

    @PostMapping("/users/{userId}/employee-link")
    public LinkedAccountView linkAccount(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID userId,
                                         @Valid @RequestBody LinkEmployeeAccountRequest request) {
        return employeeAccountService.linkAccount(UserPrincipal.require(principal).userId(), userId,
                request.employeeId());
    }
}
