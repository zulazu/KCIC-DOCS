package com.kcic.connect.employee.api;

import com.kcic.connect.employee.api.EmployeeDtos.EmployeeDetailResponse;
import com.kcic.connect.employee.application.EmployeeService;
import com.kcic.connect.identity.application.UserPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class EmployeeSelfController {

    private final EmployeeService employeeService;

    public EmployeeSelfController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employee")
    public EmployeeDetailResponse myEmployee(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = UserPrincipal.require(principal).userId();
        return employeeService.selfDetail(userId);
    }
}