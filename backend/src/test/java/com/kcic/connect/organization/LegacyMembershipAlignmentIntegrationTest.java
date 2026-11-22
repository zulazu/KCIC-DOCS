package com.kcic.connect.organization;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
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
import org.springframework.http.MediaType;

class LegacyMembershipAlignmentIntegrationTest extends IntegrationTestSupport {

    private static final String FREEZE_MESSAGE = "Akun sudah terhubung ke Employee. "
            + "Kelola penempatan melalui data Employee, bukan assignment unit legacy.";

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private User adminUser;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("legacyadmin");
        adminUser = createUser(username, "LegacyAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "LegacyAdmin!Pass2026");
    }

    @Test
    void legacyUnitAssignmentIsFrozenForLinkedAccount() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        String employeeId = createEmployee(hierarchy.divisionId());
        User account = createUser(uniqueName("linked"), "Linked!Pass2026", false, null);
        link(account.getId(), employeeId);

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(assignmentPayload(hierarchy.divisionId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(FREEZE_MESSAGE));
    }

    @Test
    void unlinkedAccountKeepsLegacyAssignmentCompatibility() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        User account = createUser(uniqueName("unlinked"), "Unlinked!Pass2026", false, null);

        String body = admin.perform(post("/api/v1/admin/users/" + account.getId() + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(assignmentPayload(hierarchy.divisionId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(account.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = objectMapper.readTree(body).get("assignmentId").asText();

        admin.perform(delete("/api/v1/admin/users/" + account.getId() + "/units/" + assignmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    void linkedAccountCanStillEndLegacyAssignments() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        User account = createUser(uniqueName("retire"), "Retire!Pass2026", false, null);
        OrganizationUnit legacyUnit = organizationUnitRepository.save(new OrganizationUnit(
                UUID.randomUUID(), null, uniqueName("LEG"), "Unit Legacy", null, null, Instant.now()));

        String body = admin.perform(post("/api/v1/admin/users/" + account.getId() + "/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(assignmentPayload(legacyUnit.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assignmentId = objectMapper.readTree(body).get("assignmentId").asText();

        String employeeId = createEmployee(hierarchy.divisionId());
        link(account.getId(), employeeId);

        admin.perform(delete("/api/v1/admin/users/" + account.getId() + "/units/" + assignmentId))
                .andExpect(status().isNoContent());

        admin.perform(get("/api/v1/admin/users/" + account.getId() + "/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(legacyUnit.getId().toString()))))
                .andExpect(jsonPath("$[*].id", hasItem(hierarchy.divisionId())));
    }

    @Test
    void unitReadsFollowActivePlacement() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        String employeeId = createEmployee(hierarchy.divisionId());
        String username = uniqueName("placed");
        User account = createUser(username, "Placed!Pass2026", false, "EMPLOYEE");
        link(account.getId(), employeeId);

        admin.perform(get("/api/v1/admin/users/" + account.getId() + "/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(hierarchy.divisionId())));

        admin.perform(get("/api/v1/admin/organization-units/" + hierarchy.divisionId() + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '" + account.getId() + "')].assignmentType")
                        .value(hasItem("PRIMARY")));

        Client employee = newClient();
        employee.login(username, "Placed!Pass2026");
        employee.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationUnits[*].id", hasItem(hierarchy.divisionId())))
                .andExpect(jsonPath("$.organizationUnits[?(@.id == '" + hierarchy.divisionId()
                        + "')].assignmentType").value(hasItem("PRIMARY")));
    }

    private void link(UUID userId, String employeeId) throws Exception {
        admin.perform(post("/api/v1/admin/users/" + userId + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> assignmentPayload(Object unitId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("organizationUnitId", unitId.toString());
        payload.put("assignmentType", "PRIMARY");
        return payload;
    }

    private String createEmployee(String divisionId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", uniqueName("NIP"));
        payload.put("fullName", "Employee " + uniqueName("X"));
        payload.put("email", uniqueName("mail") + "@kcic.test");
        payload.put("employmentStatus", "ACTIVE");
        payload.put("divisionUnitId", divisionId);
        String body = admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("employee").get("id").asText();
    }

    private Hierarchy createHierarchy() throws Exception {
        String company = createUnit("COMPANY", null, "Perusahaan");
        String directorate = createUnit("DIRECTORATE", company, "Direktorat");
        String department = createUnit("DEPARTMENT", directorate, "Departemen");
        String division = createUnit("DIVISION", department, "Biro/Division");
        return new Hierarchy(division);
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

    private void deactivateActiveCompanies(UUID actor) {
        Instant now = Instant.now();
        organizationUnitRepository.findAllByUnitTypeAndUnitStatus(UnitType.COMPANY, UnitStatus.ACTIVE)
                .forEach(unit -> {
                    unit.update(unit.getParentId(), unit.getUnitName(), unit.getDescription(),
                            UnitType.COMPANY, UnitStatus.INACTIVE, actor, now);
                    organizationUnitRepository.saveAndFlush(unit);
                });
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record Hierarchy(String divisionId) {
    }
}
