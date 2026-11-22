package com.kcic.connect.employee.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeAccountRequest;
import com.kcic.connect.employee.api.EmployeeDtos.LinkedAccountView;
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.api.UserDtos.UserSummaryResponse;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.shared.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee–account linkage spans the employee and identity modules. It lives
 * here so identity does not depend on the employee module; both mutations are
 * admin-only and audited.
 */
@Service
public class EmployeeAccountService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    public EmployeeAccountService(EmployeeRepository employeeRepository,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  AuthorizationService authorizationService,
                                  AuditService auditService,
                                  AppProperties properties,
                                  Clock clock) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public LinkedAccountView createAccount(UUID actor, UUID employeeId, CreateEmployeeAccountRequest request) {
        requireLinkCapabilities(actor);
        Employee employee = requireActiveEmployee(employeeId);
        if (userRepository.existsByEmployeeId(employeeId)) {
            throw ApiException.conflict("Employee sudah memiliki akun terhubung.");
        }
        String username = request.username().trim();
        validatePassword(request.temporaryPassword());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username sudah digunakan.");
        }
        if (userRepository.existsByEmailIgnoreCase(employee.getEmail())) {
            throw ApiException.conflict("Email employee sudah digunakan akun lain.");
        }
        Instant now = Instant.now(clock);
        User user = new User(UUID.randomUUID(), username, employee.getEmail(), employee.getFullName(),
                passwordEncoder.encode(request.temporaryPassword()), true, now, actor);
        user.linkEmployee(employeeId, actor, now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Employee sudah memiliki akun terhubung.");
        }
        auditService.record(actor, "EMPLOYEE_ACCOUNT_CREATED", "USER", user.getId(), "SUCCESS",
                Map.of("employeeId", employeeId.toString(), "username", username));
        return toView(user);
    }

    @Transactional
    public LinkedAccountView linkAccount(UUID actor, UUID userId, UUID employeeId) {
        requireLinkCapabilities(actor);
        Employee employee = requireEmployee(employeeId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Akun tidak ditemukan."));
        if (user.getAccountStatus() == AccountStatus.ACTIVE
                && employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw ApiException.validation("employeeId",
                    "Akun ACTIVE tidak dapat ditautkan ke employee non-ACTIVE. Nonaktifkan akun terlebih dahulu.");
        }
        if (user.getEmployeeId() != null) {
            throw ApiException.conflict("Akun sudah terhubung ke employee lain.");
        }
        if (userRepository.existsByEmployeeId(employeeId)) {
            throw ApiException.conflict("Employee sudah memiliki akun terhubung.");
        }
        user.linkEmployee(employeeId, actor, Instant.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Employee sudah memiliki akun terhubung.");
        }
        auditService.record(actor, "EMPLOYEE_ACCOUNT_LINKED", "USER", user.getId(), "SUCCESS",
                Map.of("employeeId", employeeId.toString(), "username", user.getUsername()));
        return toView(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> unlinkedAccounts(UUID actor, String keyword, int page, int size) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        if (page < 0) {
            throw ApiException.validation("page", "Nomor halaman tidak valid.");
        }
        if (size < 1 || size > 100) {
            throw ApiException.validation("size", "Ukuran halaman harus 1-100.");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));
        Page<User> users = userRepository.searchUnlinked(normalizedKeyword, pageRequest);
        return PageResponse.of(users.map(user -> new UserSummaryResponse(user.getId(), user.getUsername(),
                user.getEmail(), user.getDisplayName(), user.getAccountStatus(), user.isMustChangePassword(),
                user.getEmployeeId(), user.getLastLoginAt(), user.getCreatedAt())));
    }

    private void requireLinkCapabilities(UUID actor) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
    }

    private Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee tidak ditemukan."));
    }

    private Employee requireActiveEmployee(UUID employeeId) {
        Employee employee = requireEmployee(employeeId);
        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw ApiException.validation("employeeId",
                    "Akun hanya dapat dibuat untuk employee berstatus ACTIVE.");
        }
        return employee;
    }

    private void validatePassword(String password) {
        int minLength = properties.security().passwordMinLength();
        if (password == null || password.length() < minLength) {
            throw ApiException.validation("temporaryPassword",
                    "Password minimal " + minLength + " karakter.");
        }
    }

    private LinkedAccountView toView(User user) {
        return new LinkedAccountView(user.getId(), user.getUsername(), user.getAccountStatus(),
                user.isMustChangePassword(), user.getLastLoginAt());
    }
}
