package com.kcic.connect.identity.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.identity.api.AuthDtos.ChangePasswordRequest;
import com.kcic.connect.identity.api.AuthDtos.LoginRequest;
import com.kcic.connect.identity.api.AuthDtos.MeResponse;
import com.kcic.connect.identity.api.AuthDtos.UnitRef;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import com.kcic.connect.shared.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserOrganizationAssignmentRepository organizationAssignmentRepository;
    private final EmployeePlacementRepository employeePlacementRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final SecurityContextRepository securityContextRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final AppProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthorizationService authorizationService,
                                 OrganizationUnitRepository organizationUnitRepository,
                                 UserOrganizationAssignmentRepository organizationAssignmentRepository,
                                 EmployeePlacementRepository employeePlacementRepository,
                                 EmployeeRepository employeeRepository,
                                 AuditService auditService,
                                 SecurityContextRepository securityContextRepository,
                                 FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                                 AppProperties properties,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.organizationUnitRepository = organizationUnitRepository;
        this.organizationAssignmentRepository = organizationAssignmentRepository;
        this.employeePlacementRepository = employeePlacementRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.securityContextRepository = securityContextRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("invalid-login-placeholder");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MeResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.username() == null ? "" : request.username().trim();
        User user = userRepository.findByLoginIdentifier(identifier).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Username atau password tidak sesuai.");
        }

        Instant now = Instant.now(clock);
        if (user.isLockExpired(now)) {
            user.unlock(now);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(properties.security().loginMaxFailures(),
                    properties.security().loginLockMinutes(), now);
            userRepository.save(user);
            boolean locked = user.getAccountStatus() == AccountStatus.LOCKED;
            auditService.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), "FAILURE",
                    Map.of("reason", locked ? "ACCOUNT_LOCKED" : "INVALID_PASSWORD"));
            if (locked) {
                throw new ApiException(ErrorCode.ACCOUNT_LOCKED, lockMessage(user.getLockedUntil(), now));
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Username atau password tidak sesuai.");
        }

        if (user.getAccountStatus() == AccountStatus.INACTIVE) {
            auditService.record(user.getId(), "LOGIN_DENIED", "USER", user.getId(), "DENIED",
                    Map.of("reason", "ACCOUNT_INACTIVE"));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "Akun tidak aktif. Hubungi administrator.");
        }
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            auditService.record(user.getId(), "LOGIN_DENIED", "USER", user.getId(), "DENIED",
                    Map.of("reason", "ACCOUNT_LOCKED"));
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, lockMessage(user.getLockedUntil(), now));
        }
        if (!hasActiveEmployment(user)) {
            auditService.record(user.getId(), "LOGIN_DENIED", "USER", user.getId(), "DENIED",
                    Map.of("reason", "EMPLOYMENT_INACTIVE"));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED,
                    "Akun tidak dapat digunakan karena status kepegawaian tidak aktif. Hubungi administrator.");
        }

        user.recordSuccessfulLogin(now);
        userRepository.save(user);

        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        httpRequest.getSession(true);

        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        auditService.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), "SUCCESS", Map.of());
        return toMe(user);
    }

    public void logout(HttpServletRequest request, UserPrincipal principal) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (principal != null) {
            auditService.record(principal.userId(), "LOGOUT", "USER", principal.userId(), "SUCCESS", Map.of());
        }
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.validation("currentPassword", "Password saat ini tidak sesuai.");
        }
        String newPassword = request.newPassword() == null ? "" : request.newPassword();
        if (!newPassword.equals(request.confirmPassword())) {
            throw ApiException.validation("confirmPassword", "Konfirmasi password tidak sama.");
        }
        int minLength = properties.security().passwordMinLength();
        if (newPassword.length() < minLength) {
            throw ApiException.validation("newPassword",
                    "Password minimal " + minLength + " karakter.");
        }
        if (newPassword.length() > 200) {
            throw ApiException.validation("newPassword", "Password terlalu panjang.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.validation("newPassword", "Password baru harus berbeda dari password saat ini.");
        }
        user.changePassword(passwordEncoder.encode(newPassword), false, user.getId(), Instant.now(clock));
        userRepository.save(user);
        HttpSession current = httpRequest.getSession(false);
        invalidateOtherSessions(user.getUsername(), current == null ? null : current.getId());
        auditService.record(user.getId(), "PASSWORD_CHANGED", "USER", user.getId(), "SUCCESS", Map.of());
    }

    @Transactional(readOnly = true)
    public MeResponse me(UserPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid."));
        return toMe(user);
    }

    public void invalidateSessionsFor(String username) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
    }

    private void invalidateOtherSessions(String username, String currentSessionId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().stream()
                .filter(sessionId -> !sessionId.equals(currentSessionId))
                .forEach(sessionRepository::deleteById);
    }

    private MeResponse toMe(User user) {
        List<UnitRef> units = new ArrayList<>();
        List<UserOrganizationAssignment> assignments =
                organizationAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId());
        Map<UUID, OrganizationUnit> unitMap = new HashMap<>();
        organizationUnitRepository.findAllById(assignments.stream()
                        .map(UserOrganizationAssignment::getOrganizationUnitId).toList())
                .forEach(unit -> unitMap.put(unit.getId(), unit));
        for (UserOrganizationAssignment assignment : assignments) {
            OrganizationUnit unit = unitMap.get(assignment.getOrganizationUnitId());
            if (unit != null) {
                units.add(new UnitRef(unit.getId(), unit.getUnitCode(), unit.getUnitName(),
                        assignment.getAssignmentType().name()));
            }
        }
        if (user.getEmployeeId() != null) {
            employeePlacementRepository.findFirstByEmployeeIdAndIsActiveTrue(user.getEmployeeId())
                    .flatMap(placement -> organizationUnitRepository.findById(placement.getDivisionUnitId()))
                    .filter(unit -> units.stream().noneMatch(ref -> ref.id().equals(unit.getId())))
                    .ifPresent(unit -> units.add(new UnitRef(unit.getId(), unit.getUnitCode(),
                            unit.getUnitName(), "PRIMARY")));
        }
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.isMustChangePassword(), authorizationService.roleCodes(user.getId()),
                authorizationService.capabilities(user.getId()).stream().sorted().toList(),
                units, user.getLastLoginAt());
    }

    private boolean hasActiveEmployment(User user) {
        if (user.getEmployeeId() == null) {
            return true;
        }
        return employeeRepository.findById(user.getEmployeeId())
                .map(employee -> employee.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                .orElse(false);
    }

    private String lockMessage(Instant lockedUntil, Instant now) {
        if (lockedUntil == null) {
            return "Akun terkunci sementara. Hubungi administrator.";
        }
        long minutes = Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
        return "Akun terkunci sementara. Coba lagi dalam " + minutes + " menit.";
    }
}
