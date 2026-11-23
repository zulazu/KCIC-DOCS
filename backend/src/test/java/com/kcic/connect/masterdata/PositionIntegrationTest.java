package com.kcic.connect.masterdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
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
import org.springframework.http.MediaType;

/**
 * Position/Jabatan catalog gates: master-data permission boundary, duplicate
 * codes, and employee assignment rules (active-only for new assignments,
 * keeping an already assigned jabatan after deactivation).
 */
class PositionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private UUID adminUserId;
    private Client auditor;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("posadmin");
        adminUserId = createUser(username, "PosAdmin!Pass2026", false, "SYSTEM_ADMIN").getId();
        deactivateActiveCompanies(adminUserId);
        admin = newClient();
        admin.login(username, "PosAdmin!Pass2026");
        String auditorName = uniqueName("posauditor");
        createUser(auditorName, "PosAuditor!Pass2026", false, "AUDITOR");
        auditor = newClient();
        auditor.login(auditorName, "PosAuditor!Pass2026");
    }

    @Test
    void catalogCrudIsPermissionGatedAndAudited() throws Exception {
        String employeeName = uniqueName("posemployee");
        createUser(employeeName, "PosEmployee!Pass2026", false, "EMPLOYEE");
        Client employee = newClient();
        employee.login(employeeName, "PosEmployee!Pass2026");
        employee.perform(get("/api/v1/admin/master-data/positions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
        employee.perform(post("/api/v1/admin/master-data/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"STAFF2\",\"positionName\":\"Staff\"}"))
                .andExpect(status().isForbidden());

        String code = uniqueName("JAB");
        String positionId = createPosition(code, "Kepala Unit " + code);
        auditor.perform(get("/api/v1/audit-events").param("action", "position_created"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].safeMetadata.positionCode").value(code));

        admin.perform(put("/api/v1/admin/master-data/positions/" + positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionName\":\"Nama Baru\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionName").value("Nama Baru"))
                .andExpect(jsonPath("$.active").value(false));
        auditor.perform(get("/api/v1/audit-events").param("action", "position_updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].safeMetadata.active").value(false));

        // Lookup endpoint only exposes active entries; deactivated one disappears.
        String lookup = employee.perform(get("/api/v1/master-data/positions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(lookup).doesNotContain(code);
    }

    @Test
    void duplicatePositionCodeIsRejectedCaseInsensitively() throws Exception {
        String code = uniqueName("JAB");
        createPosition(code, "Asli");
        admin.perform(post("/api/v1/admin/master-data/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"" + code.toLowerCase() + "\",\"positionName\":\"Duplikat\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void employeeAssignmentValidatesPositionAndAllowsKeepingDeactivatedOne() throws Exception {
        String divisionId = createHierarchy();
        String code = uniqueName("JAB");
        String positionId = createPosition(code, "Jabatan " + code);

        // Unknown position id is rejected.
        Map<String, Object> payload = employeePayload(divisionId);
        payload.put("positionId", UUID.randomUUID().toString());
        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("positionId"));

        // Valid assignment succeeds and shows up on the detail.
        payload.put("positionId", positionId);
        String employeeId = objectMapper.readTree(admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.positionCode").value(code))
                .andReturn().getResponse().getContentAsString())
                .get("employee").get("id").asText();

        // Deactivate the catalog entry; a new assignment to it must fail...
        admin.perform(put("/api/v1/admin/master-data/positions/" + positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionName\":\"Jabatan " + code + "\",\"active\":false}"))
                .andExpect(status().isOk());
        Map<String, Object> other = employeePayload(divisionId);
        other.put("positionId", positionId);
        admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("positionId"));

        // ...but editing the employee while keeping the same jabatan still works.
        Map<String, Object> update = new HashMap<>();
        update.put("nip", payload.get("nip"));
        update.put("fullName", "Nama Diperbarui");
        update.put("email", payload.get("email"));
        update.put("employmentStatus", "ACTIVE");
        update.put("positionId", positionId);
        admin.perform(put("/api/v1/admin/employees/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.positionCode").value(code));

        // Clearing the jabatan is allowed.
        update.put("positionId", null);
        admin.perform(put("/api/v1/admin/employees/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.positionId").value(org.hamcrest.Matchers.nullValue()));
    }

    // ------------------------------------------------------------------ helpers

    private String createPosition(String code, String name) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("positionCode", code);
        payload.put("positionName", name);
        String body = admin.perform(post("/api/v1/admin/master-data/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Map<String, Object> employeePayload(String divisionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", uniqueName("NIP"));
        payload.put("fullName", "Employee " + uniqueName("X"));
        payload.put("email", uniqueName("mail") + "@kcic.test");
        payload.put("employmentStatus", "ACTIVE");
        payload.put("divisionUnitId", divisionId);
        return payload;
    }

    private String createHierarchy() throws Exception {
        String company = createUnit("COMPANY", null);
        String directorate = createUnit("DIRECTORATE", company);
        String department = createUnit("DEPARTMENT", directorate);
        return createUnit("DIVISION", department);
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