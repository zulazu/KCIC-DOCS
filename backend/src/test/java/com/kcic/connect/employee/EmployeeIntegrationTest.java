package com.kcic.connect.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

class EmployeeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private EmployeePlacementRepository placementRepository;

    private Client admin;
    private User adminUser;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("empadmin");
        adminUser = createUser(username, "EmpAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "EmpAdmin!Pass2026");
    }

    @Test
    void createEmployeePlacesInDivisionAndDerivesDirectorateAndDepartment() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        JsonNode created = createEmployee(hierarchy.divisionId, "ACTIVE");

        assertThat(created.get("employee").get("divisionUnitId").asText()).isEqualTo(hierarchy.divisionId);
        assertThat(created.get("employee").get("departmentId").asText()).isEqualTo(hierarchy.departmentId);
        assertThat(created.get("employee").get("directorateId").asText()).isEqualTo(hierarchy.directorateId);
        assertThat(created.get("employee").get("activePlacementId").asText())
                .isEqualTo(created.get("placements").get(0).get("id").asText());
    }

    @Test
    void duplicateNipAndEmailAreRejected() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        JsonNode created = createEmployee(hierarchy.divisionId, "ACTIVE");
        String nip = created.get("employee").get("nip").asText();
        String email = created.get("employee").get("email").asText();

        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(nip, uniqueName("other") + "@kcic.test", "ACTIVE",
                                hierarchy.divisionId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("NIP sudah digunakan."));

        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(uniqueName("NIP"), email, "ACTIVE",
                                hierarchy.divisionId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email sudah digunakan."));
    }

    @Test
    void placementRequiresActiveDivision() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        String legacyUnit = createLegacyUnit(null);
        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(uniqueName("NIP"), uniqueName("mail") + "@kcic.test",
                                "ACTIVE", legacyUnit))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("divisionUnitId"));

        setUnitStatus(hierarchy.divisionId, "INACTIVE");
        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(uniqueName("NIP"), uniqueName("mail") + "@kcic.test",
                                "ACTIVE", hierarchy.divisionId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("divisionUnitId"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Employee harus ditempatkan pada Biro/Division bertipe DIVISION dan berstatus aktif."));
    }

    @Test
    void transferClosesPreviousPlacementAndKeepsHistory() throws Exception {
        String company = createCompany();
        Hierarchy first = createHierarchy(company);
        Hierarchy second = createHierarchy(company);
        JsonNode created = createEmployee(first.divisionId, "ACTIVE");
        String employeeId = created.get("employee").get("id").asText();
        String activePlacementId = created.get("employee").get("activePlacementId").asText();

        Map<String, Object> payload = new HashMap<>();
        payload.put("divisionUnitId", second.divisionId);
        payload.put("expectedPlacementId", activePlacementId);
        String body = admin.perform(post("/api/v1/admin/employees/" + employeeId + "/placements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.divisionUnitId").value(second.divisionId))
                .andExpect(jsonPath("$.placements.length()").value(2))
                .andExpect(jsonPath("$.placements[0].active").value(true))
                .andExpect(jsonPath("$.placements[1].active").value(false))
                .andExpect(jsonPath("$.placements[1].validUntil").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode transferred = read(body);

        assertThat(transferred.get("placements").get(1).get("validUntil").asText()).isNotBlank();
    }

    @Test
    void staleTransferIsRejected() throws Exception {
        String company = createCompany();
        Hierarchy first = createHierarchy(company);
        Hierarchy second = createHierarchy(company);
        JsonNode created = createEmployee(first.divisionId, "ACTIVE");
        String employeeId = created.get("employee").get("id").asText();
        String stalePlacementId = created.get("employee").get("activePlacementId").asText();

        Map<String, Object> transfer = new HashMap<>();
        transfer.put("divisionUnitId", second.divisionId);
        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/placements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(transfer)))
                .andExpect(status().isOk());

        transfer.put("expectedPlacementId", stalePlacementId);
        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/placements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(transfer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void databaseRejectsSecondActivePlacement() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        JsonNode created = createEmployee(hierarchy.divisionId, "ACTIVE");
        UUID employeeId = UUID.fromString(created.get("employee").get("id").asText());
        UUID divisionId = UUID.fromString(hierarchy.divisionId);

        assertThatThrownBy(() -> placementRepository.saveAndFlush(
                new EmployeePlacement(UUID.randomUUID(), employeeId, divisionId, Instant.now(),
                        adminUser.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readsRequireEmployeeViewPermission() throws Exception {
        String username = uniqueName("plainuser");
        createUser(username, "PlainUser!Pass2026", false, "EMPLOYEE");
        Client user = newClient();
        user.login(username, "PlainUser!Pass2026");
        user.perform(get("/api/v1/admin/employees"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
    }

    @Test
    void searchAndUpdateEmployeeProfile() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        JsonNode created = createEmployee(hierarchy.divisionId, "ACTIVE");
        String employeeId = created.get("employee").get("id").asText();
        String nip = created.get("employee").get("nip").asText();

        admin.perform(get("/api/v1/admin/employees")
                        .param("keyword", nip)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(employeeId));

        Map<String, Object> update = new HashMap<>();
        update.put("nip", nip);
        update.put("fullName", "Nama Diperbarui");
        update.put("email", uniqueName("updated") + "@kcic.test");
        update.put("employmentStatus", "INACTIVE");
        admin.perform(put("/api/v1/admin/employees/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.fullName").value("Nama Diperbarui"))
                .andExpect(jsonPath("$.employee.employmentStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.placements.length()").value(1));
    }

    private JsonNode createEmployee(String divisionId, String status) throws Exception {
        String body = admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(uniqueName("NIP"), uniqueName("mail") + "@kcic.test",
                                status, divisionId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.employmentStatus").value(status))
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Map<String, Object> employeePayload(String nip, String email, String status, String divisionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", nip);
        payload.put("fullName", "Employee " + uniqueName("X"));
        payload.put("email", email);
        payload.put("employmentStatus", status);
        payload.put("divisionUnitId", divisionId);
        return payload;
    }

    private Hierarchy createHierarchy() throws Exception {
        return createHierarchy(createCompany());
    }

    private Hierarchy createHierarchy(String companyId) throws Exception {
        String directorate = createUnit("DIRECTORATE", companyId, "Direktorat");
        String department = createUnit("DEPARTMENT", directorate, "Departemen");
        String division = createUnit("DIVISION", department, "Biro/Division");
        return new Hierarchy(directorate, department, division);
    }

    private String createCompany() throws Exception {
        return createUnit("COMPANY", null, "Perusahaan");
    }

    private String createUnit(String type, String parentId, String name) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitCode", uniqueName("UNIT"));
        payload.put("unitName", name);
        payload.put("unitType", type);
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        String body = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createLegacyUnit(String parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitCode", uniqueName("LEGACY"));
        payload.put("unitName", "Unit Lama");
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        String body = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void setUnitStatus(String unitId, String status) throws Exception {
        OrganizationUnit unit = organizationUnitRepository.findById(UUID.fromString(unitId)).orElseThrow();
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitName", unit.getUnitName());
        payload.put("unitType", unit.getUnitType() == null ? null : unit.getUnitType().name());
        payload.put("parentId", unit.getParentId());
        payload.put("status", status);
        admin.perform(put("/api/v1/admin/organization-units/" + unitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk());
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

    private JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record Hierarchy(String directorateId, String departmentId, String divisionId) {
    }
}
