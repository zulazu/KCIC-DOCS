package com.kcic.connect.employee.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeImportResponse;
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.masterdata.domain.Position;
import com.kcic.connect.masterdata.infrastructure.PositionRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import com.kcic.connect.shared.api.FieldErrorItem;
import com.kcic.connect.shared.excel.ExcelSheetReader;
import com.kcic.connect.shared.excel.ExcelWriter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk employee intake from the official HR XLSX format (same column contract
 * as the E6 HR preflight validator). The whole file is validated before any
 * row is persisted: one file = one transaction, no partial imports.
 */
@Service
public class EmployeeImportService {

    public static final List<String> IMPORT_HEADERS =
            List.of("nip", "full_name", "email", "employment_status", "division_unit_code", "position_code");

    /** Pre-position E6 HR format; still accepted so existing files keep working. */
    public static final List<String> LEGACY_IMPORT_HEADERS =
            List.of("nip", "full_name", "email", "employment_status", "division_unit_code");

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 1000;
    private static final int MAX_REPORTED_ERRORS = 100;

    private final EmployeeRepository employeeRepository;
    private final EmployeePlacementRepository placementRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final PositionRepository positionRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Validator validator;
    private final Clock clock;

    public EmployeeImportService(EmployeeRepository employeeRepository,
                                 EmployeePlacementRepository placementRepository,
                                 OrganizationUnitRepository organizationUnitRepository,
                                 PositionRepository positionRepository,
                                 AuthorizationService authorizationService,
                                 AuditService auditService,
                                 Validator validator,
                                 Clock clock) {
        this.employeeRepository = employeeRepository;
        this.placementRepository = placementRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.positionRepository = positionRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.validator = validator;
        this.clock = clock;
    }

    public byte[] template(UUID actor) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
        List<List<String>> guide = List.of(
                List.of("nip", "Ya", "NIP resmi dari sumber HR; maksimal 30 karakter; unik, tidak boleh sudah terdaftar."),
                List.of("full_name", "Ya", "Nama lengkap; maksimal 160 karakter."),
                List.of("email", "Ya", "Email valid dan unik; maksimal 254 karakter."),
                List.of("employment_status", "Ya", "Isi salah satu: ACTIVE, INACTIVE, TERMINATED."),
                List.of("division_unit_code", "Ya",
                        "Kode Biro/Division yang sudah ada di KCIC Docs, bertipe DIVISION, dan berstatus ACTIVE."),
                List.of("position_code", "Tidak",
                        "Kode jabatan dari katalog Jabatan yang berstatus ACTIVE. Boleh dikosongkan."),
                List.of("(aturan file)", "-",
                        "Sheet pertama harus memakai header persis seperti baris judul sheet Employee. "
                                + "Format lama tanpa kolom position_code (5 kolom) tetap diterima. "
                                + "Maksimal 1000 baris data dan 5 MB. Jangan mengubah nama kolom. "
                                + "Sel formula tidak didukung. Seluruh baris divalidasi sebelum disimpan; "
                                + "jika ada satu baris tidak valid, tidak ada data yang disimpan."),
                List.of("(format sel)", "-",
                        "Format kolom nip, division_unit_code, dan position_code sebagai Text "
                                + "agar angka nol di depan tidak hilang."));
        return ExcelWriter.write(List.of(
                new ExcelWriter.SheetData("Employee", IMPORT_HEADERS, List.of()),
                new ExcelWriter.SheetData("Petunjuk", List.of("Kolom", "Wajib", "Keterangan"), guide)));
    }

    @Transactional
    public EmployeeImportResponse importEmployees(UUID actor, MultipartFile file) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.EMPLOYEE_MANAGE);
        if (file.isEmpty()) {
            throw ApiException.validation("file", "File wajib diisi.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, "Ukuran file maksimal 5 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.validation("file", "File tidak dapat dibaca. Coba unggah ulang.");
        }
        ExcelSheetReader.SheetData sheet = ExcelSheetReader.readFirstSheet(bytes, MAX_DATA_ROWS);
        boolean hasPositionColumn = IMPORT_HEADERS.equals(sheet.headers());
        if (!hasPositionColumn && !LEGACY_IMPORT_HEADERS.equals(sheet.headers())) {
            throw ApiException.validation("file",
                    "Header sheet pertama harus persis: " + String.join(", ", IMPORT_HEADERS)
                            + ". Unduh template resmi untuk format yang benar.");
        }
        if (sheet.rows().isEmpty()) {
            throw ApiException.validation("file", "File tidak berisi baris data employee.");
        }

        List<FieldErrorItem> errors = new ArrayList<>();
        List<ValidatedRow> validated = validateRows(sheet.rows(), hasPositionColumn, errors);
        if (!errors.isEmpty()) {
            List<FieldErrorItem> reported = errors.size() > MAX_REPORTED_ERRORS
                    ? errors.subList(0, MAX_REPORTED_ERRORS)
                    : errors;
            auditService.recordStandalone(actor, "EMPLOYEE_IMPORT", "EMPLOYEE", null, "FAILURE",
                    Map.of("rowCount", sheet.rows().size(), "errorCount", errors.size()));
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Import dibatalkan: " + errors.size() + " kesalahan pada " + sheet.rows().size()
                            + " baris. Tidak ada data yang disimpan. Perbaiki file lalu unggah ulang.",
                    List.copyOf(reported));
        }

        Instant now = Instant.now(clock);
        try {
            for (ValidatedRow row : validated) {
                Employee employee = new Employee(UUID.randomUUID(), row.nip(), row.fullName(), row.email(),
                        row.employmentStatus(), row.position() == null ? null : row.position().getId(),
                        actor, now);
                employeeRepository.save(employee);
                placementRepository.save(new EmployeePlacement(UUID.randomUUID(), employee.getId(),
                        row.division().getId(), now, actor));
                Map<String, Object> createdMetadata = new HashMap<>();
                createdMetadata.put("divisionCode", row.division().getUnitCode());
                createdMetadata.put("employmentStatus", row.employmentStatus().name());
                createdMetadata.put("source", "excel_import");
                if (row.position() != null) {
                    createdMetadata.put("positionCode", row.position().getPositionCode());
                }
                auditService.record(actor, "EMPLOYEE_CREATED", "EMPLOYEE", employee.getId(), "SUCCESS",
                        createdMetadata);
                auditService.record(actor, "EMPLOYEE_PLACED", "EMPLOYEE", employee.getId(), "SUCCESS",
                        Map.of("divisionCode", row.division().getUnitCode()));
            }
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(
                    "NIP atau email sudah digunakan (perubahan bersamaan). Muat ulang data lalu coba lagi.");
        }
        auditService.record(actor, "EMPLOYEE_IMPORT", "EMPLOYEE", null, "SUCCESS",
                Map.of("rowCount", validated.size()));
        return new EmployeeImportResponse(validated.size());
    }

    private List<ValidatedRow> validateRows(List<ExcelSheetReader.DataRow> rows, boolean hasPositionColumn,
                                            List<FieldErrorItem> errors) {
        Set<String> codes = new HashSet<>();
        Set<String> nips = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> positionCodes = new HashSet<>();
        for (ExcelSheetReader.DataRow row : rows) {
            String code = row.cells().get(4).toLowerCase(Locale.ROOT);
            if (!code.isEmpty()) {
                codes.add(code);
            }
            String nip = row.cells().get(0).toLowerCase(Locale.ROOT);
            if (!nip.isEmpty()) {
                nips.add(nip);
            }
            String email = row.cells().get(2).toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) {
                emails.add(email);
            }
            if (hasPositionColumn) {
                String positionCode = row.cells().get(5).toLowerCase(Locale.ROOT);
                if (!positionCode.isEmpty()) {
                    positionCodes.add(positionCode);
                }
            }
        }
        Map<String, OrganizationUnit> divisions = new HashMap<>();
        if (!codes.isEmpty()) {
            organizationUnitRepository.findByUnitCodeLowerIn(codes)
                    .forEach(unit -> divisions.put(unit.getUnitCode().toLowerCase(Locale.ROOT), unit));
        }
        Map<String, Position> positions = new HashMap<>();
        if (!positionCodes.isEmpty()) {
            positionRepository.findByPositionCodeLowerIn(positionCodes)
                    .forEach(position -> positions.put(position.getPositionCode().toLowerCase(Locale.ROOT),
                            position));
        }
        Set<String> existingNips = new HashSet<>();
        if (!nips.isEmpty()) {
            employeeRepository.findByNipLowerIn(nips)
                    .forEach(employee -> existingNips.add(employee.getNip().toLowerCase(Locale.ROOT)));
        }
        Set<String> existingEmails = new HashSet<>();
        if (!emails.isEmpty()) {
            employeeRepository.findByEmailLowerIn(emails)
                    .forEach(employee -> existingEmails.add(employee.getEmail().toLowerCase(Locale.ROOT)));
        }

        Map<String, Integer> firstNipRow = new HashMap<>();
        Map<String, Integer> firstEmailRow = new HashMap<>();
        List<ValidatedRow> validated = new ArrayList<>();
        for (ExcelSheetReader.DataRow row : rows) {
            int rowNumber = row.rowNumber();
            String nip = row.cells().get(0);
            String fullName = row.cells().get(1);
            String email = row.cells().get(2);
            String statusText = row.cells().get(3);
            String divisionCode = row.cells().get(4);

            EmploymentStatus status = null;
            if (statusText.isEmpty()) {
                errors.add(rowError(rowNumber, "employment_status", "Status kepegawaian wajib diisi."));
            } else {
                try {
                    status = EmploymentStatus.valueOf(statusText.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    errors.add(rowError(rowNumber, "employment_status",
                            "Status kepegawaian harus ACTIVE, INACTIVE, atau TERMINATED."));
                }
            }

            OrganizationUnit division = null;
            if (divisionCode.isEmpty()) {
                errors.add(rowError(rowNumber, "division_unit_code", "Kode Biro/Division wajib diisi."));
            } else {
                division = divisions.get(divisionCode.toLowerCase(Locale.ROOT));
                if (division == null) {
                    errors.add(rowError(rowNumber, "division_unit_code",
                            "Kode Biro/Division tidak ditemukan: " + divisionCode + "."));
                } else if (division.getUnitType() != UnitType.DIVISION
                        || division.getUnitStatus() != UnitStatus.ACTIVE) {
                    errors.add(rowError(rowNumber, "division_unit_code",
                            "Biro/Division harus bertipe DIVISION dan berstatus ACTIVE."));
                    division = null;
                }
            }

            Position position = null;
            if (hasPositionColumn) {
                String positionCode = row.cells().get(5);
                if (!positionCode.isEmpty()) {
                    position = positions.get(positionCode.toLowerCase(Locale.ROOT));
                    if (position == null) {
                        errors.add(rowError(rowNumber, "position_code",
                                "Kode jabatan tidak ditemukan: " + positionCode + "."));
                    } else if (!position.isActive()) {
                        errors.add(rowError(rowNumber, "position_code",
                                "Jabatan harus berstatus ACTIVE."));
                        position = null;
                    }
                }
            }

            CreateEmployeeRequest request = new CreateEmployeeRequest(nip, fullName, email, status,
                    division == null ? null : division.getId(), position == null ? null : position.getId());
            for (ConstraintViolation<CreateEmployeeRequest> violation : validator.validate(request)) {
                String property = violation.getPropertyPath().toString();
                if (("employmentStatus".equals(property) && status == null)
                        || ("divisionUnitId".equals(property) && division == null)) {
                    continue;
                }
                errors.add(rowError(rowNumber, columnOf(property), violation.getMessage()));
            }

            String nipKey = nip.toLowerCase(Locale.ROOT);
            if (!nipKey.isEmpty()) {
                Integer firstRow = firstNipRow.putIfAbsent(nipKey, rowNumber);
                if (firstRow != null) {
                    errors.add(rowError(rowNumber, "nip", "NIP duplikat dalam file (baris " + firstRow + ")."));
                } else if (existingNips.contains(nipKey)) {
                    errors.add(rowError(rowNumber, "nip", "NIP sudah terdaftar di KCIC Docs."));
                }
            }
            String emailKey = email.toLowerCase(Locale.ROOT);
            if (!emailKey.isEmpty()) {
                Integer firstRow = firstEmailRow.putIfAbsent(emailKey, rowNumber);
                if (firstRow != null) {
                    errors.add(rowError(rowNumber, "email",
                            "Email duplikat dalam file (baris " + firstRow + ")."));
                } else if (existingEmails.contains(emailKey)) {
                    errors.add(rowError(rowNumber, "email", "Email sudah terdaftar di KCIC Docs."));
                }
            }

            if (status != null && division != null) {
                validated.add(new ValidatedRow(nip, fullName, email, status, division, position));
            }
        }
        return validated;
    }

    private static String columnOf(String property) {
        return switch (property) {
            case "nip" -> "nip";
            case "fullName" -> "full_name";
            case "email" -> "email";
            case "employmentStatus" -> "employment_status";
            case "divisionUnitId" -> "division_unit_code";
            default -> property;
        };
    }

    private static FieldErrorItem rowError(int rowNumber, String column, String message) {
        return new FieldErrorItem("Baris " + rowNumber + " (" + column + ")", message);
    }

    private record ValidatedRow(String nip, String fullName, String email,
                                EmploymentStatus employmentStatus, OrganizationUnit division,
                                Position position) {
    }
}