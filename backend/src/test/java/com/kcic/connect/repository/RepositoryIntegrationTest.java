package com.kcic.connect.repository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RepositoryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Test
    void masterDataLookupIsPublicButAdministrationRequiresCapability() throws Exception {
        String employeeName = uniqueName("mdemployee");
        createUser(employeeName, "Employee!Pass2026", false, "EMPLOYEE");
        Client employee = newClient();
        employee.login(employeeName, "Employee!Pass2026");

        employee.perform(get("/api/v1/master-data/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryCode").exists());

        employee.perform(get("/api/v1/admin/master-data/categories"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
        employee.perform(post("/api/v1/admin/master-data/classifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "classificationCode", uniqueName("CLS"),
                                "classificationName", "Unauthorized",
                                "sensitivityRank", 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
    }

    @Test
    void adminCreatesAndDeactivatesMasterData() throws Exception {
        Client admin = adminClient();

        String code = uniqueName("CAT").toUpperCase();
        String created = admin.perform(post("/api/v1/admin/master-data/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryCode", code,
                                "categoryName", "Kategori Uji",
                                "description", "Dibuat oleh integration test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode category = objectMapper.readTree(created);
        String categoryId = category.get("id").asText();

        admin.perform(post("/api/v1/admin/master-data/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryCode", code.toLowerCase(),
                                "categoryName", "Duplikat"))))
                .andExpect(status().isConflict());

        admin.perform(put("/api/v1/admin/master-data/categories/" + categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryName", "Kategori Uji Nonaktif",
                                "description", "Dinonaktifkan",
                                "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        admin.perform(get("/api/v1/admin/master-data/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryCode == '" + code + "')].active").value(false));

        admin.perform(get("/api/v1/master-data/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryCode == '" + code + "')]").isEmpty());

        admin.perform(put("/api/v1/admin/master-data/file-policies/" + defaultPolicyId(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "allowedExtensions", java.util.List.of(),
                                "allowedMimeTypes", java.util.List.of("application/pdf"),
                                "maxFileSizeBytes", 1024))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void employeeCannotCreateWorkspaceOrFolder() throws Exception {
        OrganizationUnit unit = organizationUnit("Employee View Unit");
        String employeeName = uniqueName("wsemployee");
        createUser(employeeName, "Employee!Pass2026", false, "EMPLOYEE");
        Client employee = newClient();
        employee.login(employeeName, "Employee!Pass2026");

        employee.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        employee.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workspacePayload(unit.getId(), "WS"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        String workspaceId = createWorkspace(adminClient(), unit);
        employee.perform(post("/api/v1/workspaces/" + workspaceId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("folderName", "Tidak Boleh"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
    }

    @Test
    void adminManagesWorkspaceLifecycleWithAuditedStatusTransitions() throws Exception {
        Client admin = adminClient();
        OrganizationUnit unit = organizationUnit("Workspace Owner Unit");
        String code = uniqueName("WS").toUpperCase();

        Map<String, Object> payload = workspacePayload(unit.getId(), code);
        payload.put("ownerUserId", null);
        String body = admin.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerOrganizationUnitName").value("Workspace Owner Unit"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode workspace = objectMapper.readTree(body);
        String workspaceId = workspace.get("id").asText();
        String workspaceCode = workspace.get("workspaceCode").asText();

        admin.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workspacePayload(unit.getId(),
                                workspaceCode.toLowerCase()))))
                .andExpect(status().isConflict());

        admin.perform(put("/api/v1/workspaces/" + workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceName", "Workspace Arsip",
                                "description", "Diarsipkan",
                                "ownerOrganizationUnitId", unit.getId().toString(),
                                "status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        admin.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workspaceId + "')]").isEmpty());

        admin.perform(get("/api/v1/workspaces")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workspaceId + "')].status").value("ARCHIVED"));

        admin.perform(put("/api/v1/workspaces/" + workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceName", "Workspace Arsip",
                                "ownerOrganizationUnitId", unit.getId().toString(),
                                "status", "UNKNOWN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void folderRulesPreventCyclesDuplicatesCrossWorkspaceAndOrphaning() throws Exception {
        Client admin = adminClient();
        OrganizationUnit unit = organizationUnit("Folder Rules Unit");
        String workspaceId = createWorkspace(admin, unit);
        String otherWorkspaceId = createWorkspace(admin, unit);

        String rootA = createFolder(admin, workspaceId, null, "Kebijakan");
        String childB = createFolder(admin, workspaceId, rootA, "Kebijakan 2026");

        admin.perform(post("/api/v1/workspaces/" + workspaceId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentFolderId", rootA,
                                "folderName", "kebijakan 2026"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("folderName"));

        admin.perform(put("/api/v1/workspaces/" + workspaceId + "/folders/" + rootA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderName", "Kebijakan",
                                "parentFolderId", childB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentFolderId"));

        admin.perform(post("/api/v1/workspaces/" + otherWorkspaceId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentFolderId", rootA,
                                "folderName", "Cross Workspace"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        admin.perform(post("/api/v1/workspaces/" + workspaceId + "/folders/" + rootA + "/archive"))
                .andExpect(status().isConflict());

        admin.perform(post("/api/v1/workspaces/" + workspaceId + "/folders/" + childB + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        admin.perform(post("/api/v1/workspaces/" + workspaceId + "/folders/" + rootA + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        admin.perform(get("/api/v1/workspaces/" + workspaceId + "/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        admin.perform(get("/api/v1/workspaces/" + workspaceId + "/folders")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------------------------------------------------------- helpers

    private Client adminClient() throws Exception {
        String adminName = uniqueName("repoadmin");
        createUser(adminName, "Admin!Pass2026", false, "SYSTEM_ADMIN");
        Client admin = newClient();
        admin.login(adminName, "Admin!Pass2026");
        return admin;
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("U").substring(0, 10), name, null, null, Instant.now()));
    }

    private Map<String, Object> workspacePayload(UUID unitId, String code) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", code);
        payload.put("workspaceName", "Workspace " + code);
        payload.put("description", "Workspace integration test");
        payload.put("ownerOrganizationUnitId", unitId.toString());
        return payload;
    }

    private String createWorkspace(Client admin, OrganizationUnit unit) throws Exception {
        String response = admin.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workspacePayload(unit.getId(),
                                uniqueName("WS").toUpperCase()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createFolder(Client admin, String workspaceId, String parentFolderId, String name)
            throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("folderName", name);
        if (parentFolderId != null) {
            payload.put("parentFolderId", parentFolderId);
        }
        String response = admin.perform(post("/api/v1/workspaces/" + workspaceId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String defaultPolicyId(Client admin) throws Exception {
        String response = admin.perform(get("/api/v1/admin/master-data/file-policies"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get(0).get("id").asText();
    }
}
