package com.kcic.connect.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.employee.application.EmployeeImportService;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Gates for the Excel bulk intake slice: permission-gated template download,
 * atomic validated import with per-row errors, and audited Excel export.
 */
class EmployeeExcelIntegrationTest extends IntegrationTestSupport {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private UUID adminUserId;
    private Client auditor;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("xladmin");
        adminUserId = createUser(username, "XlAdmin!Pass2026", false, "SYSTEM_ADMIN").getId();
        deactivateActiveCompanies(adminUserId);
        admin = newClient();
        admin.login(username, "XlAdmin!Pass2026");
        String auditorName = uniqueName("xlauditor");
        createUser(auditorName, "XlAuditor!Pass2026", false, "AUDITOR");
        auditor = newClient();
        auditor.login(auditorName, "XlAuditor!Pass2026");
    }

    @Test
    void templateIsPermissionGatedAndHasContractHeadersWithoutSampleData() throws Exception {
        String employeeName = uniqueName("xlemployee");
        createUser(employeeName, "XlEmployee!Pass2026", false, "EMPLOYEE");
        Client employee = newClient();
        employee.login(employeeName, "XlEmployee!Pass2026");
        employee.perform(get("/api/v1/admin/employees/import-template"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));

        byte[] body = admin.perform(get("/api/v1/admin/employees/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_MEDIA_TYPE.toString()))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("kcic-docs-employee-import-template.xlsx")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            Sheet employeeSheet = workbook.getSheet("Employee");
            assertThat(employeeSheet).isNotNull();
            assertThat(employeeSheet.getLastRowNum()).isZero();
            assertThat(cells(employeeSheet.getRow(0))).isEqualTo(EmployeeImportService.IMPORT_HEADERS);
            assertThat(workbook.getSheet("Petunjuk")).isNotNull();
        }
    }

    @Test
    void importCreatesAllRowsWithPlacementsAndAudit() throws Exception {
        String divisionCode = createHierarchy();
        String nipA = uniqueName("NIP");
        String nipB = uniqueName("NIP");
        byte[] file = importWorkbook(List.of(
                List.of(nipA, "Budi Santoso", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode),
                List.of(nipB, "Sari Wijaya", uniqueName("mail") + "@kcic.test", "INACTIVE", divisionCode)));

        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2));

        String body = admin.perform(get("/api/v1/admin/employees").param("keyword", nipA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(body).get("items").get(0);
        assertThat(item.get("divisionCode").asText()).isEqualTo(divisionCode);
        String employeeId = item.get("id").asText();
        admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].active").value(true))
                .andExpect(jsonPath("$.employee.employmentStatus").value("ACTIVE"));

        auditor.perform(get("/api/v1/audit-events")
                        .param("action", "employee_import")
                        .param("outcome", "success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].safeMetadata.rowCount").value(2));
        auditor.perform(get("/api/v1/audit-events")
                        .param("action", "employee_created")
                        .param("actor", adminName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].safeMetadata.source").value("excel_import"));
    }

    @Test
    void invalidRowsAbortWholeImportWithPerRowErrors() throws Exception {
        String divisionCode = createHierarchy();
        String existingNip = uniqueName("NIP");
        createEmployeeViaApi(existingNip, divisionCode);
        String goodNip = uniqueName("NIP");
        String duplicateNip = uniqueName("NIP");
        byte[] file = importWorkbook(List.of(
                List.of(goodNip, "Valid Row", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode),
                List.of(duplicateNip, "Bad Status", uniqueName("mail") + "@kcic.test", "PENSION", divisionCode),
                List.of(duplicateNip, "Duplicate Nip", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode),
                List.of(existingNip, "Existing Nip", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode),
                List.of(uniqueName("NIP"), "Unknown Division", uniqueName("mail") + "@kcic.test",
                        "ACTIVE", "NO_SUCH_DIVISION"),
                List.of(uniqueName("NIP"), "Bad Email", "bukan-email", "ACTIVE", divisionCode)));

        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(file)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Tidak ada data yang disimpan")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 3 (employment_status)')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 4 (nip)')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 5 (nip)')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 6 (division_unit_code)')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 7 (email)')]").exists());

        admin.perform(get("/api/v1/admin/employees").param("keyword", goodNip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        auditor.perform(get("/api/v1/audit-events")
                        .param("action", "employee_import")
                        .param("outcome", "failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void malformedFilesAreRejected() throws Exception {
        admin.perform(multipart("/api/v1/admin/employees/import")
                        .file(xlsxPart(importWorkbook(List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));

        byte[] wrongHeader;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Employee");
            sheet.createRow(0).createCell(0).setCellValue("nomor_induk");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            wrongHeader = out.toByteArray();
        }
        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(wrongHeader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(org.hamcrest.Matchers.containsString("Header sheet pertama harus persis")));

        admin.perform(multipart("/api/v1/admin/employees/import")
                        .file(new MockMultipartFile("file", "data.xlsx", XLSX_MEDIA_TYPE.toString(),
                                "bukan excel".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        admin.perform(multipart("/api/v1/admin/employees/import")
                        .file(new MockMultipartFile("file", "empty.xlsx", XLSX_MEDIA_TYPE.toString(),
                                new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importAndExportRequirePermissions() throws Exception {
        String employeeName = uniqueName("xlemployee");
        createUser(employeeName, "XlEmployee!Pass2026", false, "EMPLOYEE");
        Client employee = newClient();
        employee.login(employeeName, "XlEmployee!Pass2026");
        employee.perform(multipart("/api/v1/admin/employees/import")
                        .file(xlsxPart(importWorkbook(List.of()))))
                .andExpect(status().isForbidden());
        employee.perform(get("/api/v1/admin/employees/export"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
    }

    @Test
    void exportHonorsFiltersAndIsAudited() throws Exception {
        String divisionCode = createHierarchy();
        String nip = uniqueName("NIP");
        createEmployeeViaApi(nip, divisionCode);
        createEmployeeViaApi(uniqueName("NIP"), divisionCode);

        byte[] body = admin.perform(get("/api/v1/admin/employees/export").param("keyword", nip))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_MEDIA_TYPE.toString()))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("kcic-docs-employees-")))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(cells(sheet.getRow(0))).containsExactly(
                    "nip", "full_name", "email", "employment_status", "division_unit_code",
                    "division_name", "department_name", "directorate_name", "position_code", "position_name",
                    "has_account", "created_at");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(nip);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo(divisionCode);
        }

        auditor.perform(get("/api/v1/audit-events").param("action", "employee_exported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].safeMetadata.rowCount").value(1));
    }

    @Test
    void importAssignsPositionsAndLegacyFormatStillWorks() throws Exception {
        String divisionCode = createHierarchy();
        String positionCode = uniqueName("JAB");
        createPosition(positionCode);
        String nipA = uniqueName("NIP");
        String nipB = uniqueName("NIP");
        byte[] file = importWorkbook(List.of(
                List.of(nipA, "With Position", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode,
                        positionCode),
                List.of(nipB, "Without Position", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode,
                        "")));

        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2));

        String body = admin.perform(get("/api/v1/admin/employees").param("keyword", nipA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(body).get("items").get(0);
        assertThat(item.get("positionCode").asText()).isEqualTo(positionCode);
        assertThat(item.get("positionName").asText()).isEqualTo("Jabatan " + positionCode);

        String legacyNip = uniqueName("NIP");
        byte[] legacyFile = importWorkbook(EmployeeImportService.LEGACY_IMPORT_HEADERS, List.of(
                List.of(legacyNip, "Legacy Format", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode)));
        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(legacyFile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1));
        admin.perform(get("/api/v1/admin/employees").param("keyword", legacyNip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].positionId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unknownOrInactivePositionAbortsImport() throws Exception {
        String divisionCode = createHierarchy();
        String inactiveCode = uniqueName("JAB");
        String inactiveId = createPosition(inactiveCode);
        admin.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/admin/master-data/positions/" + inactiveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionName\":\"Nonaktif\",\"active\":false}"))
                .andExpect(status().isOk());
        String nip = uniqueName("NIP");
        byte[] file = importWorkbook(List.of(
                List.of(nip, "Unknown Position", uniqueName("mail") + "@kcic.test", "ACTIVE", divisionCode,
                        "NO_SUCH_POSITION"),
                List.of(uniqueName("NIP"), "Inactive Position", uniqueName("mail") + "@kcic.test",
                        "ACTIVE", divisionCode, inactiveCode)));

        admin.perform(multipart("/api/v1/admin/employees/import").file(xlsxPart(file)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 2 (position_code)')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'Baris 3 (position_code)')]").exists());
        admin.perform(get("/api/v1/admin/employees").param("keyword", nip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private String adminName() {
        return userRepository.findById(adminUserId).orElseThrow().getUsername();
    }

    private MockMultipartFile xlsxPart(byte[] bytes) {
        return new MockMultipartFile("file", "employees.xlsx", XLSX_MEDIA_TYPE.toString(), bytes);
    }

    private byte[] importWorkbook(List<List<String>> dataRows) throws Exception {
        return importWorkbook(EmployeeImportService.IMPORT_HEADERS, dataRows);
    }

    private byte[] importWorkbook(List<String> headers, List<List<String>> dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Employee");
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
            }
            int rowIndex = 1;
            for (List<String> values : dataRows) {
                Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            values.add(row.getCell(column).getStringCellValue());
        }
        return values;
    }

    private void createEmployeeViaApi(String nip, String divisionCode) throws Exception {
        String divisionId = organizationUnitRepository.findByUnitCodeIgnoreCase(divisionCode)
                .orElseThrow().getId().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", nip);
        payload.put("fullName", "Employee " + uniqueName("X"));
        payload.put("email", uniqueName("mail") + "@kcic.test");
        payload.put("employmentStatus", "ACTIVE");
        payload.put("divisionUnitId", divisionId);
        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    private String createPosition(String code) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("positionCode", code);
        payload.put("positionName", "Jabatan " + code);
        String body = admin.perform(post("/api/v1/admin/master-data/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createHierarchy() throws Exception {
        String company = createUnit("COMPANY", null);
        String directorate = createUnit("DIRECTORATE", company);
        String department = createUnit("DEPARTMENT", directorate);
        String divisionId = createUnit("DIVISION", department);
        return organizationUnitRepository.findById(UUID.fromString(divisionId)).orElseThrow().getUnitCode();
    }

    private String createUnit(String type, String parentId) throws Exception {
        String code = uniqueName("UNIT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitCode", code);
        payload.put("unitName", type + " " + code);
        payload.put("unitType", type);
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        String body = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void deactivateActiveCompanies(UUID actor) {
        Instant now = Instant.now();
        organizationUnitRepository.findAllByUnitTypeAndUnitStatus(UnitType.COMPANY, UnitStatus.ACTIVE)
                .forEach(unit -> {
                    unit.update(unit.getParentId(), unit.getUnitName(), unit.getDescription(),
                            UnitType.COMPANY, UnitStatus.INACTIVE, actor, now);
                    organizationUnitRepository.saveAndFlush(unit);
                });
    }
}