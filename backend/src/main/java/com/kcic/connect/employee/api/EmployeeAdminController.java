package com.kcic.connect.employee.api;

import com.kcic.connect.employee.api.EmployeeDtos.CreateEmployeeRequest;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeDetailResponse;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeImportResponse;
import com.kcic.connect.employee.api.EmployeeDtos.EmployeeSummaryResponse;
import com.kcic.connect.employee.api.EmployeeDtos.TransferPlacementRequest;
import com.kcic.connect.employee.api.EmployeeDtos.UpdateEmployeeRequest;
import com.kcic.connect.employee.application.EmployeeImportService;
import com.kcic.connect.employee.application.EmployeeService;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.shared.excel.ExcelWriter;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/employees")
public class EmployeeAdminController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final List<String> EXPORT_HEADERS = List.of(
            "nip", "full_name", "email", "employment_status", "division_unit_code",
            "division_name", "department_name", "directorate_name", "position_code", "position_name",
            "has_account", "created_at");

    private final EmployeeService employeeService;
    private final EmployeeImportService employeeImportService;

    public EmployeeAdminController(EmployeeService employeeService,
                                   EmployeeImportService employeeImportService) {
        this.employeeService = employeeService;
        this.employeeImportService = employeeImportService;
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

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] workbook = employeeImportService.template(UserPrincipal.require(principal).userId());
        return xlsxResponse(workbook, "kcic-docs-employee-import-template.xlsx");
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeImportResponse importEmployees(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestPart("file") MultipartFile file) {
        return employeeImportService.importEmployees(UserPrincipal.require(principal).userId(), file);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) EmploymentStatus status,
                                         @RequestParam(required = false) Boolean linked) {
        List<EmployeeSummaryResponse> rows = employeeService.exportRows(
                UserPrincipal.require(principal).userId(), keyword, status, linked);
        List<List<String>> values = rows.stream().map(row -> List.of(
                row.nip(),
                row.fullName(),
                row.email(),
                row.employmentStatus().name(),
                text(row.divisionCode()),
                text(row.divisionName()),
                text(row.departmentName()),
                text(row.directorateName()),
                text(row.positionCode()),
                text(row.positionName()),
                row.hasAccount() ? "YA" : "TIDAK",
                row.createdAt().toString())).toList();
        byte[] workbook = ExcelWriter.write(List.of(new ExcelWriter.SheetData("Employee", EXPORT_HEADERS, values)));
        return xlsxResponse(workbook, "kcic-docs-employees-" + LocalDate.now(ZoneOffset.UTC) + ".xlsx");
    }

    private static ResponseEntity<byte[]> xlsxResponse(byte[] workbook, String filename) {
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .contentLength(workbook.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(workbook);
    }

    private static String text(String value) {
        return value == null ? "" : value;
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
