package com.kcic.connect.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.identity.domain.User;
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
 * E4 gate: descendant unit scope is opt-in, applies through employee placement
 * ancestry, never widens legacy exact grants, and explicit deny keeps precedence.
 */
class DescendantScopeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private User adminUser;
    private Hierarchy hierarchy;
    private String documentId;

    @BeforeEach
    void setUp() throws Exception {
        String username = uniqueName("descadmin");
        adminUser = createUser(username, "DescAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "DescAdmin!Pass2026");
        hierarchy = createHierarchy();
        String workspaceId = createWorkspace(hierarchy.companyId());
        documentId = createDocument(workspaceId);
    }

    @Test
    void legacyUnitGrantDoesNotWidenToDescendantPlacement() throws Exception {
        Actor employee = placedActor(hierarchy.divisionId());
        grant(documentAccess(), "ORGANIZATION_UNIT", hierarchy.departmentId(), "document.view", "ALLOW", false);

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        admin.perform(get(documentAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.active[0].includeDescendants").value(false));
    }

    @Test
    void descendantUnitGrantAllowsPlacementBelowGrantedUnit() throws Exception {
        Actor employee = placedActor(hierarchy.divisionId());
        grant(documentAccess(), "ORGANIZATION_UNIT", hierarchy.departmentId(), "document.view", "ALLOW", true);

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(documentId));

        admin.perform(get(documentAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[0].includeDescendants").value(true));
    }

    @Test
    void explicitDenyAtChildUnitBeatsDescendantAllow() throws Exception {
        Actor employee = placedActor(hierarchy.divisionId());
        grant(documentAccess(), "ORGANIZATION_UNIT", hierarchy.departmentId(), "document.view", "ALLOW", true);
        grant(documentAccess(), "ORGANIZATION_UNIT", hierarchy.divisionId(), "document.view", "DENY", false);

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void descendantRoleAssignmentCoversDescendantOwnedWorkspace() throws Exception {
        String divisionWorkspace = createWorkspace(hierarchy.divisionId());
        String divisionDocument = createDocument(divisionWorkspace);
        Actor ancestorLead = linkedActor(hierarchy.divisionId());
        Actor exactLead = linkedActor(hierarchy.divisionId());

        assignRole(ancestorLead.userId(), hierarchy.departmentId(), true);
        assignRole(exactLead.userId(), hierarchy.departmentId(), false);

        ancestorLead.client().perform(get("/api/v1/documents/" + divisionDocument))
                .andExpect(status().isOk());
        exactLead.client().perform(get("/api/v1/documents/" + divisionDocument))
                .andExpect(status().isForbidden());

        admin.perform(get("/api/v1/admin/users/" + ancestorLead.userId() + "/role-assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].includeDescendants").value(true));
    }

    @Test
    void descendantFlagIsRejectedForNonUnitGrantAndNonUnitScope() throws Exception {
        Actor employee = linkedActor(hierarchy.divisionId());
        Map<String, Object> grantPayload = new HashMap<>();
        grantPayload.put("principalType", "USER");
        grantPayload.put("principalId", employee.userId().toString());
        grantPayload.put("permissionCode", "document.view");
        grantPayload.put("effect", "ALLOW");
        grantPayload.put("includeDescendants", true);
        admin.perform(post(documentAccess())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("includeDescendants"));

        admin.perform(post("/api/v1/admin/users/" + employee.userId() + "/role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleCode", "EMPLOYEE",
                                "scopeType", "GLOBAL",
                                "includeDescendants", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("includeDescendants"));
    }

    // ---------------------------------------------------------------- helpers

    private record Actor(Client client, UUID userId) {
    }

    private record Hierarchy(String companyId, String directorateId, String departmentId, String divisionId) {
    }

    private String documentAccess() {
        return "/api/v1/documents/" + documentId + "/access";
    }

    private Actor placedActor(String divisionUnitId) throws Exception {
        return linkedActor(divisionUnitId);
    }

    /**
     * Links a real user to a real employee placed in the given Biro/Division; the
     * placement (not legacy user_organization_assignments) is the membership source.
     */
    private Actor linkedActor(String divisionUnitId) throws Exception {
        String employeeId = createEmployee(divisionUnitId);
        String username = uniqueName("descuser");
        User user = createUser(username, "DescUser!Pass2026", false, null);
        admin.perform(post("/api/v1/admin/users/" + user.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk());
        Client client = newClient();
        client.login(username, "DescUser!Pass2026");
        return new Actor(client, user.getId());
    }

    private String createEmployee(String divisionUnitId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", uniqueName("NIP"));
        payload.put("fullName", "Employee " + uniqueName("Desc"));
        payload.put("email", uniqueName("desc") + "@kcic.test");
        payload.put("employmentStatus", "ACTIVE");
        payload.put("divisionUnitId", divisionUnitId);
        String body = admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("employee").get("id").asText();
    }

    private void assignRole(UUID userId, String organizationUnitId, boolean includeDescendants) throws Exception {
        admin.perform(post("/api/v1/admin/users/" + userId + "/role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleCode", "EMPLOYEE",
                                "scopeType", "ORGANIZATION_UNIT",
                                "organizationUnitId", organizationUnitId,
                                "includeDescendants", includeDescendants))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.includeDescendants").value(includeDescendants));
    }

    private String grant(String path, String principalType, String principalId, String permissionCode,
                         String effect, boolean includeDescendants) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("principalType", principalType);
        payload.put("principalId", principalId);
        payload.put("permissionCode", permissionCode);
        payload.put("effect", effect);
        payload.put("includeDescendants", includeDescendants);
        String body = admin.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Hierarchy createHierarchy() throws Exception {
        String company = createUnit("COMPANY", null);
        String directorate = createUnit("DIRECTORATE", company);
        String department = createUnit("DEPARTMENT", directorate);
        String division = createUnit("DIVISION", department);
        return new Hierarchy(company, directorate, department, division);
    }

    private String createUnit(String type, String parentId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitCode", uniqueName("UNIT"));
        payload.put("unitName", "Unit " + uniqueName(type));
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

    private String createWorkspace(String ownerUnitId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("WS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Desc"));
        payload.put("ownerOrganizationUnitId", ownerUnitId);
        String body = admin.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createDocument(String workspaceId) throws Exception {
        JsonNode categories = read(admin.perform(get("/api/v1/master-data/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode types = read(admin.perform(get("/api/v1/master-data/document-types"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode classifications = read(admin.perform(get("/api/v1/master-data/classifications"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Dokumen " + uniqueName("Desc"));
        payload.put("description", "Dokumen uji cakupan turunan");
        payload.put("categoryId", categories.get(0).get("id").asText());
        payload.put("documentTypeId", types.get(0).get("id").asText());
        payload.put("classificationId", classifications.get(0).get("id").asText());
        String body = admin.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }

    private JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
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
