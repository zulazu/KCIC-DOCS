package com.kcic.connect.employee.api;

import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeDetailResponse;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeSummaryResponse;
import com.kcic.connect.employee.api.EmployeeDtos.TransferPlacementRequest;
import com.kcic.connect.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import com.kcic.connect.employee.application.EmployeeService;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/admin/employees")
public class EmployeeAdminController {

    private final EmployeeService employeeService;

    public EmployeeAdminController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public PageResponse<EmployeeSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) EmploymentStatus status,
                                                      @RequestParam(required = false) Boolean linked,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return employeeService.search(UserPrincipal.require(principal).userId(), keyword, status, linked,
                page, size);
    }

    @GetMapping("/{employeeId}")
    public EmployeeDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID employeeId) {
        return employeeService.detail(UserPrincipal.require(principal).userId(), employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/{employeeId}")
    public EmployeeDetailResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID employeeId,
                                         @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(UserPrincipal.require(principal).userId(), employeeId, request);
    }

    @PostMapping("/{employeeId}/placements")
    public EmployeeDetailResponse transfer(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID employeeId,
                                           @Valid @RequestBody TransferPlacementRequest request) {
        return employeeService.transfer(UserPrincipal.require(principal).userId(), employeeId, request);
    }
}
