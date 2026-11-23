package com.kcic.connect.employee.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeDetailResponse;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeSummaryResponse;
import com.kcic.connect.employee.api.EmployeeDtos.LinkedAccountView;
import com.kcic.connect.employee.api.EmployeeDtos.PlacementView;
import com.kcic.connect.employee.api.EmployeeDtos.TransferPlacementRequest;
import com.kcic.connect.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.application.AuthenticationService;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.masterdata.domain.Position;
import com.kcic.connect.masterdata.infrastructure.PositionRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private static final int EXPORT_MAX_ROWS = 10_000;

    private final EmployeeRepository employeeRepository;
    private final EmployeePlacementRepository placementRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employeeRepository,
                           EmployeePlacementRepository placementRepository,
                           OrganizationUnitRepository organizationUnitRepository,
                           PositionRepository positionRepository,
                           UserRepository userRepository,
                           AuthorizationService authorizationService,
                           AuthenticationService authenticationService,
                           AuditService auditService,
                           Clock clock) {
        this.employeeRepository = employeeRepository;
        this.placementRepository = placementRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryResponse> search(UUID actor, String keyword, EmploymentStatus status,
                                                        Boolean linked, int page, int size) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_VIEW);
        if (page < 0) {
            throw ApiException.validation("page", "Nomor halaman tidak valid.");
        }
        if (size < 1 || size > 100) {
            throw ApiException.validation("size", "Ukuran halaman harus 1-100.");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<Employee> employees = employeeRepository.search(normalizedKeyword, status, linked, pageRequest);
        return PageResponse.of(summarize(employees.getContent()), page, size, employees.getTotalElements());
    }

    /** Unpaged export for Excel reporting; capped and audited like other data egress. */
    @Transactional
    public List<EmployeeSummaryResponse> exportRows(UUID actor, String keyword, EmploymentStatus status,
                                                    Boolean linked) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_VIEW);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<Employee> employees = employeeRepository.search(normalizedKeyword, status, linked,
                PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.ASC, "fullName")));
        List<EmployeeSummaryResponse> rows = summarize(employees.getContent());
        auditService.record(actor, "EMPLOYEE_EXPORTED", "EMPLOYEE", null, "SUCCESS",
                Map.of("rowCount", rows.size(),
                        "truncated", employees.getTotalElements() > EXPORT_MAX_ROWS));
        return rows;
    }

    private List<EmployeeSummaryResponse> summarize(List<Employee> content) {
        Map<UUID, EmployeePlacement> activePlacements = placementRepository
                .findByEmployeeIdInAndIsActiveTrue(content.stream().map(Employee::getId).toList())
                .stream()
                .collect(Collectors.toMap(EmployeePlacement::getEmployeeId, Function.identity()));
        Map<UUID, UnitContext> contexts = resolveContexts(activePlacements.values().stream()
                .map(EmployeePlacement::getDivisionUnitId).toList());
        Set<UUID> linkedEmployeeIds = new HashSet<>(userRepository.findLinkedEmployeeIds(
                content.stream().map(Employee::getId).toList()));
        Map<UUID, Position> positions = loadPositions(content);
        return content.stream()
                .map(employee -> toSummary(employee, activePlacements.get(employee.getId()), contexts,
                        linkedEmployeeIds.contains(employee.getId()), positions))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDetailResponse detail(UUID actor, UUID employeeId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_VIEW);
        return buildDetail(requireEmployee(employeeId));
    }

    /** Own HR profile only; the employee id is derived from the principal, never from input. */
    @Transactional(readOnly = true)
    public EmployeeDetailResponse selfDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User tidak ditemukan."));
        if (user.getEmployeeId() == null) {
            throw ApiException.notFound("Akun belum terhubung ke data employee.");
        }
        return buildDetail(requireEmployee(user.getEmployeeId()));
    }

    @Transactional
    public EmployeeDetailResponse create(UUID actor, CreateEmployeeRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
        String nip = request.nip().trim();
        String email = request.email().trim();
        if (employeeRepository.existsByNipIgnoreCase(nip)) {
            throw ApiException.conflict("NIP sudah digunakan.");
        }
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email sudah digunakan.");
        }
        OrganizationUnit division = requireActiveDivision(request.divisionUnitId());
        Position position = resolvePosition(request.positionId(), null);
        Instant now = Instant.now(clock);
        Employee employee = new Employee(UUID.randomUUID(), nip, request.fullName().trim(), email,
                request.employmentStatus(), position == null ? null : position.getId(), actor, now);
        employeeRepository.save(employee);
        placementRepository.save(new EmployeePlacement(UUID.randomUUID(), employee.getId(), division.getId(),
                now, actor));
        Map<String, Object> createdMetadata = new HashMap<>();
        createdMetadata.put("divisionCode", division.getUnitCode());
        createdMetadata.put("employmentStatus", request.employmentStatus().name());
        if (position != null) {
            createdMetadata.put("positionCode", position.getPositionCode());
        }
        auditService.record(actor, "EMPLOYEE_CREATED", "EMPLOYEE", employee.getId(), "SUCCESS",
                createdMetadata);
        auditService.record(actor, "EMPLOYEE_PLACED", "EMPLOYEE", employee.getId(), "SUCCESS",
                Map.of("divisionCode", division.getUnitCode()));
        return buildDetail(employee);
    }

    @Transactional
    public EmployeeDetailResponse update(UUID actor, UUID employeeId, UpdateEmployeeRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
        Employee employee = requireEmployee(employeeId);
        String nip = request.nip().trim();
        String email = request.email().trim();
        if (employeeRepository.existsByNipIgnoreCaseAndIdNot(nip, employeeId)) {
            throw ApiException.conflict("NIP sudah digunakan.");
        }
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, employeeId)) {
            throw ApiException.conflict("Email sudah digunakan.");
        }
        EmploymentStatus previousStatus = employee.getEmploymentStatus();
        Position position = resolvePosition(request.positionId(), employee.getPositionId());
        UUID positionId = position == null ? null : position.getId();
        List<String> changedFields = new ArrayList<>();
        if (!nip.equals(employee.getNip())) {
            changedFields.add("nip");
        }
        if (!request.fullName().trim().equals(employee.getFullName())) {
            changedFields.add("fullName");
        }
        if (!email.equalsIgnoreCase(employee.getEmail())) {
            changedFields.add("email");
        }
        if (previousStatus != request.employmentStatus()) {
            changedFields.add("employmentStatus");
        }
        if (!Objects.equals(positionId, employee.getPositionId())) {
            changedFields.add("positionId");
        }
        employee.update(nip, request.fullName().trim(), email, request.employmentStatus(), positionId, actor,
                Instant.now(clock));
        employeeRepository.save(employee);
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("employmentStatus", request.employmentStatus().name());
        auditMetadata.put("changedFields", changedFields);
        auditService.record(actor, "EMPLOYEE_UPDATED", "EMPLOYEE", employeeId, "SUCCESS",
                auditMetadata);
        if (previousStatus == EmploymentStatus.ACTIVE
                && request.employmentStatus() != EmploymentStatus.ACTIVE) {
            userRepository.findByEmployeeId(employeeId).ifPresent(account -> {
                authenticationService.invalidateSessionsFor(account.getUsername());
                auditService.record(actor, "EMPLOYEE_ACCESS_SUSPENDED", "EMPLOYEE", employeeId, "SUCCESS",
                        Map.of("employmentStatus", request.employmentStatus().name(),
                                "account", account.getUsername()));
            });
        }
        return buildDetail(employee);
    }

    @Transactional
    public EmployeeDetailResponse transfer(UUID actor, UUID employeeId, TransferPlacementRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee tidak ditemukan."));
        if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw ApiException.conflict("Employee berstatus TERMINATED tidak dapat ditempatkan.");
        }
        OrganizationUnit division = requireActiveDivision(request.divisionUnitId());
        EmployeePlacement active = placementRepository.findFirstByEmployeeIdAndIsActiveTrue(employeeId)
                .orElseThrow(() -> ApiException.conflict(
                        "Employee tidak memiliki placement aktif. Muat ulang data employee."));
        if (request.expectedPlacementId() != null
                && !request.expectedPlacementId().equals(active.getId())) {
            throw ApiException.conflict("Placement employee telah berubah. Muat ulang data employee.");
        }
        if (active.getDivisionUnitId().equals(division.getId())) {
            throw ApiException.conflict("Employee sudah ditempatkan pada Biro/Division tersebut.");
        }
        UUID previousDivisionId = active.getDivisionUnitId();
        Instant now = Instant.now(clock);
        active.close(now);
        try {
            placementRepository.saveAndFlush(active);
            placementRepository.saveAndFlush(new EmployeePlacement(UUID.randomUUID(), employeeId,
                    division.getId(), now, actor));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Placement employee telah berubah. Muat ulang data employee.");
        }
        Map<UUID, UnitContext> contexts = resolveContexts(List.of(previousDivisionId, division.getId()));
        String fromCode = codeOf(contexts, previousDivisionId);
        auditService.record(actor, "EMPLOYEE_TRANSFERRED", "EMPLOYEE", employeeId, "SUCCESS",
                Map.of("fromDivisionCode", fromCode, "toDivisionCode", division.getUnitCode()));
        return buildDetail(employee);
    }

    private Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> ApiException.notFound("Employee tidak ditemukan."));
    }

    private OrganizationUnit requireActiveDivision(UUID divisionUnitId) {
        OrganizationUnit unit = organizationUnitRepository.findById(divisionUnitId)
                .orElseThrow(() -> ApiException.validation("divisionUnitId",
                        "Biro/Division tidak ditemukan."));
        if (unit.getUnitType() != UnitType.DIVISION || unit.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("divisionUnitId",
                    "Employee harus ditempatkan pada Biro/Division bertipe DIVISION dan berstatus aktif.");
        }
        return unit;
    }

    private EmployeeDetailResponse buildDetail(Employee employee) {
        List<EmployeePlacement> placements =
                placementRepository.findByEmployeeIdOrderByValidFromDesc(employee.getId());
        Map<UUID, UnitContext> contexts = resolveContexts(placements.stream()
                .map(EmployeePlacement::getDivisionUnitId).toList());
        EmployeePlacement active = placements.stream()
                .filter(EmployeePlacement::isActive)
                .findFirst()
                .orElse(null);
        User account = userRepository.findByEmployeeId(employee.getId()).orElse(null);
        EmployeeSummaryResponse summary = toSummary(employee, active, contexts, account != null,
                loadPositions(List.of(employee)));
        LinkedAccountView linkedAccount = account == null ? null : new LinkedAccountView(account.getId(),
                account.getUsername(), account.getAccountStatus(), account.isMustChangePassword(),
                account.getLastLoginAt());
        List<PlacementView> views = placements.stream().map(placement -> {
            UnitContext context = contexts.get(placement.getDivisionUnitId());
            return new PlacementView(placement.getId(), placement.getDivisionUnitId(),
                    context == null ? null : context.division().getUnitCode(),
                    context == null ? null : context.division().getUnitName(),
                    context == null || context.department() == null ? null : context.department().getUnitName(),
                    context == null || context.directorate() == null ? null : context.directorate().getUnitName(),
                    placement.getValidFrom(), placement.getValidUntil(), placement.isActive(),
                    placement.getCreatedAt());
        }).toList();
        return new EmployeeDetailResponse(summary, views, linkedAccount);
    }

    private EmployeeSummaryResponse toSummary(Employee employee, EmployeePlacement active,
                                              Map<UUID, UnitContext> contexts, boolean hasAccount,
                                              Map<UUID, Position> positions) {
        UnitContext context = active == null ? null : contexts.get(active.getDivisionUnitId());
        Position position = employee.getPositionId() == null ? null : positions.get(employee.getPositionId());
        return new EmployeeSummaryResponse(employee.getId(), employee.getNip(), employee.getFullName(),
                employee.getEmail(), employee.getEmploymentStatus(),
                active == null ? null : active.getId(),
                context == null ? null : context.division().getId(),
                context == null ? null : context.division().getUnitCode(),
                context == null ? null : context.division().getUnitName(),
                context == null || context.department() == null ? null : context.department().getId(),
                context == null || context.department() == null ? null : context.department().getUnitName(),
                context == null || context.directorate() == null ? null : context.directorate().getId(),
                context == null || context.directorate() == null ? null : context.directorate().getUnitName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getPositionCode(),
                position == null ? null : position.getPositionName(),
                hasAccount, employee.getCreatedAt(), employee.getUpdatedAt());
    }

    /** Keeping the currently assigned jabatan is allowed even after it is deactivated;
     *  only new assignments require an active catalog entry. */
    private Position resolvePosition(UUID requestedId, UUID currentId) {
        if (requestedId == null) {
            return null;
        }
        Position position = positionRepository.findById(requestedId)
                .orElseThrow(() -> ApiException.validation("positionId", "Jabatan tidak ditemukan."));
        if (!position.isActive() && !requestedId.equals(currentId)) {
            throw ApiException.validation("positionId", "Jabatan harus berstatus aktif untuk dipilih.");
        }
        return position;
    }

    private Map<UUID, Position> loadPositions(Collection<Employee> employees) {
        Set<UUID> ids = employees.stream()
                .map(Employee::getPositionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return positionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Position::getId, Function.identity()));
    }

    private Map<UUID, UnitContext> resolveContexts(Collection<UUID> divisionIds) {
        Set<UUID> distinctIds = new HashSet<>(divisionIds);
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> lookupIds = new HashSet<>(organizationUnitRepository.findAncestorIds(distinctIds));
        lookupIds.addAll(distinctIds);
        Map<UUID, OrganizationUnit> units = new HashMap<>();
        organizationUnitRepository.findAllById(lookupIds)
                .forEach(unit -> units.put(unit.getId(), unit));
        Map<UUID, UnitContext> contexts = new HashMap<>();
        for (UUID divisionId : distinctIds) {
            OrganizationUnit division = units.get(divisionId);
            if (division == null) {
                continue;
            }
            OrganizationUnit department = null;
            OrganizationUnit directorate = null;
            OrganizationUnit current = units.get(division.getParentId());
            int depth = 0;
            while (current != null && depth < 10) {
                if (current.getUnitType() == UnitType.DEPARTMENT && department == null) {
                    department = current;
                } else if (current.getUnitType() == UnitType.DIRECTORATE && directorate == null) {
                    directorate = current;
                }
                current = units.get(current.getParentId());
                depth++;
            }
            contexts.put(divisionId, new UnitContext(division, department, directorate));
        }
        return contexts;
    }

    private String codeOf(Map<UUID, UnitContext> contexts, UUID divisionId) {
        return Optional.ofNullable(contexts.get(divisionId))
                .map(context -> context.division().getUnitCode())
                .orElse("UNKNOWN");
    }

    private record UnitContext(OrganizationUnit division,
                               OrganizationUnit department,
                               OrganizationUnit directorate) {
    }
}
