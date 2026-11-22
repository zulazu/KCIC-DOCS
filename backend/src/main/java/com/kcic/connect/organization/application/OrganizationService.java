package com.kcic.connect.organization.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.identity.api.UserDtos.AssignUnitRequest;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.api.OrganizationDtos.CreateOrganizationUnitRequest;
import com.kcic.connect.organization.api.OrganizationDtos.OrganizationUnitResponse;
import com.kcic.connect.organization.api.OrganizationDtos.UnitMemberResponse;
import com.kcic.connect.organization.api.OrganizationDtos.UpdateOrganizationUnitRequest;
import com.kcic.connect.organization.domain.AssignmentType;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationUnitRepository unitRepository;
    private final UserOrganizationAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final EmployeePlacementRepository placementRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public OrganizationService(OrganizationUnitRepository unitRepository,
                               UserOrganizationAssignmentRepository assignmentRepository,
                               UserRepository userRepository,
                               EmployeePlacementRepository placementRepository,
                               AuthorizationService authorizationService,
                               AuditService auditService,
                               Clock clock) {
        this.unitRepository = unitRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.placementRepository = placementRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> listActive(UnitType unitType) {
        List<OrganizationUnit> units = unitType == null
                ? unitRepository.findAllByOrderByUnitNameAsc()
                : unitRepository.findAllByUnitTypeOrderByUnitNameAsc(unitType);
        return units.stream()
                .filter(unit -> unit.getUnitStatus() == UnitStatus.ACTIVE)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> listAll(UUID actor, UnitType unitType) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        List<OrganizationUnit> units = unitType == null
                ? unitRepository.findAllByOrderByUnitNameAsc()
                : unitRepository.findAllByUnitTypeOrderByUnitNameAsc(unitType);
        return units.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationUnitResponse get(UUID actor, UUID unitId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        return toResponse(requireUnit(unitId));
    }

    @Transactional
    public OrganizationUnitResponse create(UUID actor, CreateOrganizationUnitRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        String code = request.unitCode().trim();
        if (unitRepository.existsByUnitCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode unit sudah digunakan.");
        }
        validateTypeShape(null, request.unitType(), request.parentId());
        Instant now = Instant.now(clock);
        OrganizationUnit unit = unitRepository.save(new OrganizationUnit(UUID.randomUUID(), request.parentId(),
                code, request.unitName().trim(), request.description(), request.unitType(), actor, now));
        auditService.record(actor, "ORGANIZATION_UNIT_CREATED", "ORGANIZATION_UNIT", unit.getId(), "SUCCESS",
                Map.of("unitCode", code, "unitType", request.unitType() == null ? "LEGACY" : request.unitType().name()));
        return toResponse(unit);
    }

    @Transactional
    public OrganizationUnitResponse update(UUID actor, UUID unitId, UpdateOrganizationUnitRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        OrganizationUnit unit = requireUnit(unitId);
        UnitStatus status = parseStatus(request.status());
        UnitType nextType = request.unitType() == null ? unit.getUnitType() : request.unitType();
        validateParent(unitId, request.parentId());
        validateTypeShape(unitId, nextType, request.parentId());
        validateTypeChange(unit, nextType);
        unit.update(request.parentId(), request.unitName().trim(), request.description(), nextType, status, actor,
                Instant.now(clock));
        auditService.record(actor, "ORGANIZATION_UNIT_UPDATED", "ORGANIZATION_UNIT", unitId, "SUCCESS",
                Map.of("status", status.name(),
                        "unitType", nextType == null ? "LEGACY" : nextType.name()));
        return toResponse(unit);
    }

    @Transactional
    public UnitMemberResponse assignUser(UUID actor, UUID userId, AssignUnitRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User tidak ditemukan."));
        rejectLegacyWriteForLinkedUser(user);
        OrganizationUnit unit = requireUnit(request.organizationUnitId());
        if (unit.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("organizationUnitId", "Unit harus aktif.");
        }
        Instant now = Instant.now(clock);
        if (request.validUntil() != null && !request.validUntil().isAfter(now)) {
            throw ApiException.validation("validUntil", "Waktu berakhir harus di masa depan.");
        }
        List<UserOrganizationAssignment> active = assignmentRepository.findByUserIdAndIsActiveTrue(userId);
        boolean duplicate = active.stream().anyMatch(existing ->
                existing.getOrganizationUnitId().equals(unit.getId())
                        && existing.getAssignmentType() == request.assignmentType());
        if (duplicate) {
            throw ApiException.conflict("User sudah memiliki assignment aktif dengan unit dan tipe yang sama.");
        }
        if (request.assignmentType() == AssignmentType.PRIMARY) {
            active.stream()
                    .filter(existing -> existing.getAssignmentType() == AssignmentType.PRIMARY)
                    .forEach(existing -> existing.end(now));
            assignmentRepository.flush();
        }
        UserOrganizationAssignment assignment = assignmentRepository.save(new UserOrganizationAssignment(
                UUID.randomUUID(), user.getId(), unit.getId(), request.assignmentType(), now,
                request.validUntil(), actor));
        auditService.record(actor, "USER_UNIT_ASSIGNED", "USER", userId, "SUCCESS",
                Map.of("unitCode", unit.getUnitCode(), "assignmentType", request.assignmentType().name()));
        return toMember(assignment, user);
    }

    @Transactional
    public void endAssignment(UUID actor, UUID userId, UUID assignmentId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        UserOrganizationAssignment assignment = assignmentRepository.findByIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> ApiException.notFound("Assignment unit tidak ditemukan."));
        if (!assignment.isActive()) {
            throw ApiException.conflict("Assignment unit sudah tidak aktif.");
        }
        assignment.end(Instant.now(clock));
        auditService.record(actor, "USER_UNIT_ASSIGNMENT_ENDED", "USER", userId, "SUCCESS",
                Map.of("assignmentId", assignmentId.toString()));
    }

    @Transactional(readOnly = true)
    public List<UnitMemberResponse> members(UUID actor, UUID unitId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ORGANIZATION_MANAGE);
        requireUnit(unitId);
        List<UserOrganizationAssignment> assignments =
                assignmentRepository.findByOrganizationUnitIdAndIsActiveTrue(unitId);
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(assignments.stream().map(UserOrganizationAssignment::getUserId).toList())
                .forEach(user -> users.put(user.getId(), user));
        List<UnitMemberResponse> result = new ArrayList<>();
        for (UserOrganizationAssignment assignment : assignments) {
            User user = users.get(assignment.getUserId());
            if (user != null) {
                result.add(toMember(assignment, user));
            }
        }
        appendPlacementMembers(unitId, result);
        return result;
    }

    private void appendPlacementMembers(UUID unitId, List<UnitMemberResponse> result) {
        List<EmployeePlacement> placements = placementRepository.findByDivisionUnitIdAndIsActiveTrue(unitId);
        if (placements.isEmpty()) {
            return;
        }
        Map<UUID, User> usersByEmployee = new HashMap<>();
        userRepository.findByEmployeeIdIn(placements.stream().map(EmployeePlacement::getEmployeeId).toList())
                .forEach(user -> usersByEmployee.put(user.getEmployeeId(), user));
        Set<UUID> included = new HashSet<>();
        result.forEach(member -> included.add(member.userId()));
        for (EmployeePlacement placement : placements) {
            User user = usersByEmployee.get(placement.getEmployeeId());
            if (user != null && included.add(user.getId())) {
                result.add(new UnitMemberResponse(placement.getId(), user.getId(), user.getUsername(),
                        user.getDisplayName(), AssignmentType.PRIMARY.name(), true, placement.getValidFrom(),
                        placement.getValidUntil()));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> unitsOfUser(UUID actor, UUID userId) {
        if (!actor.equals(userId)) {
            authorizationService.requireGlobalCapability(actor, PermissionCodes.USER_MANAGE);
        }
        List<UserOrganizationAssignment> assignments = assignmentRepository.findByUserIdAndIsActiveTrue(userId);
        Map<UUID, OrganizationUnit> units = new HashMap<>();
        unitRepository.findAllById(assignments.stream()
                        .map(UserOrganizationAssignment::getOrganizationUnitId).toList())
                .forEach(unit -> units.put(unit.getId(), unit));
        List<OrganizationUnitResponse> result = new ArrayList<>();
        for (UserOrganizationAssignment assignment : assignments) {
            OrganizationUnit unit = units.get(assignment.getOrganizationUnitId());
            if (unit != null) {
                result.add(toResponse(unit));
            }
        }
        userRepository.findById(userId)
                .map(User::getEmployeeId)
                .flatMap(placementRepository::findFirstByEmployeeIdAndIsActiveTrue)
                .flatMap(placement -> unitRepository.findById(placement.getDivisionUnitId()))
                .filter(unit -> units.putIfAbsent(unit.getId(), unit) == null)
                .ifPresent(unit -> result.add(toResponse(unit)));
        return result;
    }

    private void validateTypeShape(UUID unitId, UnitType unitType, UUID parentId) {
        if (unitType == null) {
            // Legacy units remain untyped until the business owner maps them.
            if (parentId != null) {
                OrganizationUnit parent = requireUnit(parentId);
                if (parent.getUnitStatus() != UnitStatus.ACTIVE) {
                    throw ApiException.validation("parentId", "Unit induk harus aktif.");
                }
            }
            return;
        }
        if (unitType == UnitType.COMPANY) {
            if (parentId != null) {
                throw ApiException.validation("parentId", "COMPANY harus menjadi unit akar.");
            }
            boolean another = unitRepository.findAllByUnitTypeAndUnitStatus(UnitType.COMPANY, UnitStatus.ACTIVE)
                    .stream().anyMatch(unit -> !unit.getId().equals(unitId));
            if (another) {
                throw ApiException.conflict("Hanya satu unit COMPANY aktif yang diizinkan.");
            }
            return;
        }
        if (parentId == null) {
            throw ApiException.validation("parentId", "Tipe unit ini wajib memiliki unit induk.");
        }
        OrganizationUnit parent = requireUnit(parentId);
        if (parent.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("parentId", "Unit induk harus aktif.");
        }
        UnitType expected = expectedParent(unitType);
        if (parent.getUnitType() == null) {
            throw ApiException.validation("parentId", "Unit induk belum memiliki tipe organisasi.");
        }
        if (parent.getUnitType() != expected) {
            throw ApiException.validation("parentId",
                    "Unit " + unitType.name() + " harus berada di bawah " + expected.name() + ".");
        }
    }

    private void validateTypeChange(OrganizationUnit unit, UnitType nextType) {
        if (nextType == null || nextType == unit.getUnitType()) {
            return;
        }
        if (!assignmentRepository.findByOrganizationUnitIdAndIsActiveTrue(unit.getId()).isEmpty()) {
            throw ApiException.conflict("Tipe unit tidak dapat diubah setelah memiliki anggota aktif.");
        }
        UnitType expectedChild = expectedChild(nextType);
        for (OrganizationUnit child : unitRepository.findByParentId(unit.getId())) {
            if (child.getUnitType() != null && child.getUnitType() != expectedChild) {
                throw ApiException.conflict("Tipe unit tidak dapat diubah karena tipe unit turunan tidak sesuai.");
            }
        }
        if (nextType == UnitType.COMPANY) {
            boolean another = unitRepository.findAllByUnitTypeAndUnitStatus(UnitType.COMPANY, UnitStatus.ACTIVE)
                    .stream().anyMatch(existing -> !existing.getId().equals(unit.getId()));
            if (another) {
                throw ApiException.conflict("Hanya satu unit COMPANY aktif yang diizinkan.");
            }
        }
    }

    private static UnitType expectedParent(UnitType child) {
        return switch (child) {
            case COMPANY -> null;
            case DIRECTORATE -> UnitType.COMPANY;
            case DEPARTMENT -> UnitType.DIRECTORATE;
            case DIVISION -> UnitType.DEPARTMENT;
        };
    }

    private static UnitType expectedChild(UnitType parent) {
        return switch (parent) {
            case COMPANY -> UnitType.DIRECTORATE;
            case DIRECTORATE -> UnitType.DEPARTMENT;
            case DEPARTMENT -> UnitType.DIVISION;
            case DIVISION -> null;
        };
    }

    private void validateParent(UUID unitId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(unitId)) {
            throw ApiException.validation("parentId", "Unit tidak boleh menjadi induk dirinya sendiri.");
        }
        OrganizationUnit parent = requireUnit(parentId);
        if (parent.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("parentId", "Unit induk harus aktif.");
        }
        Set<UUID> seen = new HashSet<>();
        UUID cursor = parentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(unitId)) {
                throw ApiException.validation("parentId", "Hierarki unit tidak boleh melingkar.");
            }
            if (!seen.add(cursor) || depth++ > 100) {
                throw ApiException.validation("parentId", "Hierarki unit tidak valid.");
            }
            OrganizationUnit current = unitRepository.findById(cursor).orElse(null);
            cursor = current == null ? null : current.getParentId();
        }
    }

    private OrganizationUnit requireUnit(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> ApiException.notFound("Unit organisasi tidak ditemukan."));
    }

    private void rejectLegacyWriteForLinkedUser(User user) {
        if (user.getEmployeeId() != null) {
            throw ApiException.conflict(
                    "Akun sudah terhubung ke Employee. Kelola penempatan melalui data Employee, bukan assignment unit legacy.");
        }
    }

    private UnitStatus parseStatus(String value) {
        try {
            return UnitStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Data tidak valid.",
                    List.of(new com.kcic.connect.shared.api.FieldErrorItem("status", "Status unit tidak valid.")));
        }
    }

    private UnitMemberResponse toMember(UserOrganizationAssignment assignment, User user) {
        return new UnitMemberResponse(assignment.getId(), user.getId(), user.getUsername(),
                user.getDisplayName(), assignment.getAssignmentType().name(), assignment.isActive(),
                assignment.getValidFrom(), assignment.getValidUntil());
    }

    private OrganizationUnitResponse toResponse(OrganizationUnit unit) {
        return new OrganizationUnitResponse(unit.getId(), unit.getParentId(), unit.getUnitCode(),
                unit.getUnitName(), unit.getDescription(), unit.getUnitType(), unit.getUnitStatus().name(),
                unit.getCreatedAt(), unit.getUpdatedAt());
    }
}
