package com.kcic.connect.identity.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.identity.api.UserDtos.CreateUserRequest;
import com.kcic.connect.identity.api.UserDtos.ResetPasswordRequest;
import com.kcic.connect.identity.api.UserDtos.RoleAssignmentView;
import com.kcic.connect.identity.api.UserDtos.UnitMembershipView;
import com.kcic.connect.identity.api.UserDtos.UpdateUserRequest;
import com.kcic.connect.identity.api.UserDtos.UserDetailResponse;
import com.kcic.connect.identity.api.UserDtos.UserSummaryResponse;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.shared.config.AppProperties;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserOrganizationAssignmentRepository organizationAssignmentRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    public UserAdminService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            AuthorizationService authorizationService,
                            OrganizationUnitRepository organizationUnitRepository,
                            UserOrganizationAssignmentRepository organizationAssignmentRepository,
                            AuthenticationService authenticationService,
                            AuditService auditService,
                            AppProperties properties,
                            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.organizationUnitRepository = organizationUnitRepository;
        this.organizationAssignmentRepository = organizationAssignmentRepository;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> search(UUID actor, String keyword, AccountStatus status,
                                                    int page, int size) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        if (page < 0) {
            throw ApiException.validation("page", "Nomor halaman tidak valid.");
        }
        if (size < 1 || size > 100) {
            throw ApiException.validation("size", "Ukuran halaman harus 1-100.");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));
        Page<User> result = status == null
                ? userRepository.searchAll(normalizedKeyword, pageRequest)
                : userRepository.searchByStatus(normalizedKeyword, status, pageRequest);
        return PageResponse.of(result.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse detail(UUID actor, UUID userId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        User user = requireUser(userId);
        List<UserRoleAssignment> assignments = authorizationService.userAssignments(userId);
        Map<UUID, Role> roles = authorizationService.rolesByIds(assignments.stream()
                .map(UserRoleAssignment::getRoleId).collect(Collectors.toSet()));
        List<RoleAssignmentView> roleViews = assignments.stream().map(assignment -> {
            Role role = roles.get(assignment.getRoleId());
            return new RoleAssignmentView(assignment.getId(), assignment.getRoleId(),
                    role == null ? "UNKNOWN" : role.getRoleCode(),
                    role == null ? "Role tidak ditemukan" : role.getRoleName(),
                    assignment.getScopeType().name(), assignment.getOrganizationUnitId(),
                    assignment.getWorkspaceId(), assignment.isIncludeDescendants(), assignment.getValidFrom(),
                    assignment.getValidUntil(), assignment.getAssignmentStatus().name(), assignment.getCreatedAt());
        }).toList();

        List<UserOrganizationAssignment> memberships =
                organizationAssignmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, OrganizationUnit> units = new HashMap<>();
        organizationUnitRepository.findAllById(memberships.stream()
                        .map(UserOrganizationAssignment::getOrganizationUnitId).toList())
                .forEach(unit -> units.put(unit.getId(), unit));
        List<UnitMembershipView> unitViews = new ArrayList<>();
        for (UserOrganizationAssignment membership : memberships) {
            OrganizationUnit unit = units.get(membership.getOrganizationUnitId());
            unitViews.add(new UnitMembershipView(membership.getId(), membership.getOrganizationUnitId(),
                    unit == null ? "UNKNOWN" : unit.getUnitCode(),
                    unit == null ? "Unit tidak ditemukan" : unit.getUnitName(),
                    membership.getAssignmentType(), membership.isActive(), membership.getValidFrom(),
                    membership.getValidUntil()));
        }
        return new UserDetailResponse(toSummary(user), roleViews, unitViews);
    }

    @Transactional
    public UserDetailResponse create(UUID actor, CreateUserRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        String username = request.username().trim();
        String email = blankToNull(request.email());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username sudah digunakan.");
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email sudah digunakan.");
        }
        validatePassword(request.temporaryPassword());
        Instant now = Instant.now(clock);
        User user = new User(UUID.randomUUID(), username, email, request.displayName().trim(),
                passwordEncoder.encode(request.temporaryPassword()), true, now, actor);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof SQLException sql
                    && "23502".equals(sql.getSQLState())) {
                throw ApiException.conflict("Akun tidak dapat dibuat tanpa tautan Employee. "
                        + "Setelah cutover V18, buat akun dari halaman Employee.");
            }
            throw ApiException.conflict("Username atau email sudah digunakan.");
        }
        auditService.record(actor, "USER_CREATED", "USER", user.getId(), "SUCCESS",
                Map.of("username", username));
        return detail(actor, user.getId());
    }

    @Transactional
    public UserDetailResponse update(UUID actor, UUID userId, UpdateUserRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        User user = requireUser(userId);
        String email = blankToNull(request.email());
        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw ApiException.conflict("Email sudah digunakan.");
        }
        user.updateProfile(request.displayName().trim(), email, actor, Instant.now(clock));
        userRepository.save(user);
        auditService.record(actor, "USER_UPDATED", "USER", userId, "SUCCESS", Map.of());
        return detail(actor, userId);
    }

    @Transactional
    public UserSummaryResponse changeStatus(UUID actor, UUID userId, AccountStatus status) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        if (status != AccountStatus.ACTIVE && status != AccountStatus.INACTIVE) {
            throw ApiException.validation("status", "Gunakan ACTIVE atau INACTIVE untuk perubahan status manual.");
        }
        if (actor.equals(userId)) {
            throw ApiException.validation("status", "Anda tidak dapat mengubah status akun sendiri.");
        }
        User user = requireUser(userId);
        if (status == AccountStatus.ACTIVE) {
            authorizationService.requireActiveEmployment(user, "status");
        }
        user.changeStatus(status, actor, Instant.now(clock));
        userRepository.save(user);
        if (status == AccountStatus.INACTIVE) {
            authenticationService.invalidateSessionsFor(user.getUsername());
        }
        auditService.record(actor, "USER_STATUS_CHANGED", "USER", userId, "SUCCESS",
                Map.of("status", status.name()));
        return toSummary(user);
    }

    @Transactional
    public void resetPassword(UUID actor, UUID userId, ResetPasswordRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        if (actor.equals(userId)) {
            throw ApiException.validation("temporaryPassword",
                    "Gunakan fitur ganti password untuk akun sendiri.");
        }
        User user = requireUser(userId);
        validatePassword(request.temporaryPassword());
        user.changePassword(passwordEncoder.encode(request.temporaryPassword()), true, actor,
                Instant.now(clock));
        userRepository.save(user);
        authenticationService.invalidateSessionsFor(user.getUsername());
        auditService.record(actor, "USER_PASSWORD_RESET", "USER", userId, "SUCCESS", Map.of());
    }

    @Transactional
    public UserSummaryResponse unlock(UUID actor, UUID userId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        User user = requireUser(userId);
        if (user.getAccountStatus() != AccountStatus.LOCKED) {
            throw ApiException.conflict("Akun tidak dalam status terkunci.");
        }
        authorizationService.requireActiveEmployment(user, "status");
        user.unlock(Instant.now(clock));
        userRepository.save(user);
        auditService.record(actor, "USER_UNLOCKED", "USER", userId, "SUCCESS", Map.of());
        return toSummary(user);
    }

    @Transactional(readOnly = true)
    public Set<String> capabilitiesOf(UUID userId) {
        return authorizationService.capabilities(userId);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User tidak ditemukan."));
    }

    private void validatePassword(String password) {
        int minLength = properties.security().passwordMinLength();
        if (password == null || password.length() < minLength) {
            throw ApiException.validation("temporaryPassword",
                    "Password minimal " + minLength + " karakter.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getAccountStatus(), user.isMustChangePassword(),
                user.getEmployeeId(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
