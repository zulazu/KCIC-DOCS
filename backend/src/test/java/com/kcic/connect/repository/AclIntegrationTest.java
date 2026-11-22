package com.kcic.connect.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.authorization.infrastructure.PermissionRepository;
import com.kcic.connect.authorization.infrastructure.ResourcePermissionRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Phase 5 gate: default deny, explicit deny precedence, inheritance along the real
 * ancestry, expiry/revocation, independent revoke, cross-unit/IDOR, and access-
 * management capability checks.
 */
class AclIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private ResourcePermissionRepository resourcePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void explicitGrantAllowsAccessAndRevokeRemovesItWithHistory() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Grant Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Grant");
        Actor employee = employee(organizationUnit("ACL Grant Employee Unit"));

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        String grantId = grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "USER",
                employee.userId(), "document.view", "ALLOW");

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(documentId));

        admin.client().perform(get("/api/v1/documents/" + documentId + "/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.active[0].principalName").value("Test " + employee.username()))
                .andExpect(jsonPath("$.active[0].permissionCode").value("document.view"))
                .andExpect(jsonPath("$.active[0].grantSource").value("MANUAL"))
                .andExpect(jsonPath("$.history.length()").value(0));

        admin.client().perform(delete("/api/v1/documents/" + documentId + "/access/" + grantId))
                .andExpect(status().isNoContent());

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        admin.client().perform(get("/api/v1/documents/" + documentId + "/access")
                        .param("includeHistory", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].active").value(false))
                .andExpect(jsonPath("$.history[0].revokedAt").exists());

        admin.client().perform(get("/api/v1/documents/" + documentId + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.actionCode == 'ACCESS_GRANTED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.actionCode == 'ACCESS_REVOKED')]").isNotEmpty());
    }

    @Test
    void explicitDenyBeatsInheritedWorkspaceAllow() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Deny Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Deny");
        Actor employee = employee(organizationUnit("ACL Deny Employee Unit"));

        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/access", "USER", employee.userId(),
                "document.view", "ALLOW");
        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "USER", employee.userId(),
                "document.view", "DENY");
        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
        employee.client().perform(get("/api/v1/documents").param("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        admin.client().perform(get("/api/v1/workspaces/" + workspaceId + "/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[?(@.effect == 'ALLOW')]").isNotEmpty());
        admin.client().perform(get("/api/v1/documents/" + documentId + "/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[?(@.effect == 'DENY')]").isNotEmpty());
    }

    @Test
    void revokingOneGrantKeepsIndependentGrantIntact() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Independent Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Independen");
        Actor employee = employee(organizationUnit("ACL Independent Employee Unit"));

        String workspaceGrantId = grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/access",
                "USER", employee.userId(), "document.view", "ALLOW");
        String documentGrantId = grant(admin.client(), "/api/v1/documents/" + documentId + "/access",
                "USER", employee.userId(), "document.view", "ALLOW");
        employee.client().perform(get("/api/v1/documents/" + documentId)).andExpect(status().isOk());

        admin.client().perform(delete("/api/v1/workspaces/" + workspaceId + "/access/" + workspaceGrantId))
                .andExpect(status().isNoContent());
        employee.client().perform(get("/api/v1/documents/" + documentId)).andExpect(status().isOk());

        admin.client().perform(delete("/api/v1/documents/" + documentId + "/access/" + documentGrantId))
                .andExpect(status().isNoContent());
        employee.client().perform(get("/api/v1/documents/" + documentId)).andExpect(status().isForbidden());
    }

    @Test
    void folderGrantsFollowRealFolderAncestry() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Folder Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String parentFolder = createFolder(admin.client(), workspaceId, "Parent", null);
        String childFolder = createFolder(admin.client(), workspaceId, "Child", parentFolder);
        String siblingFolder = createFolder(admin.client(), workspaceId, "Sibling", null);
        String parentDoc = createDocument(admin.client(), workspaceId, parentFolder, masterData, "Dokumen Parent");
        String childDoc = createDocument(admin.client(), workspaceId, childFolder, masterData, "Dokumen Child");
        String siblingDoc = createDocument(admin.client(), workspaceId, siblingFolder, masterData, "Dokumen Sibling");
        String rootDoc = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Root");
        Actor employee = employee(organizationUnit("ACL Folder Employee Unit"));

        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/folders/" + parentFolder + "/access",
                "USER", employee.userId(), "document.view", "ALLOW");

        employee.client().perform(get("/api/v1/documents/" + parentDoc)).andExpect(status().isOk());
        employee.client().perform(get("/api/v1/documents/" + childDoc)).andExpect(status().isOk());
        employee.client().perform(get("/api/v1/documents/" + siblingDoc)).andExpect(status().isForbidden());
        employee.client().perform(get("/api/v1/documents/" + rootDoc)).andExpect(status().isForbidden());
        employee.client().perform(get("/api/v1/documents").param("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[?(@.id == '" + parentDoc + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + childDoc + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + siblingDoc + "')]").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + rootDoc + "')]").isEmpty());

        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/folders/" + childFolder + "/access",
                "USER", employee.userId(), "document.view", "DENY");
        employee.client().perform(get("/api/v1/documents/" + childDoc)).andExpect(status().isForbidden());
        employee.client().perform(get("/api/v1/documents/" + parentDoc)).andExpect(status().isOk());
    }

    @Test
    void workspaceAclGrantExposesAndDenyHidesWorkspaceOutsideUnitScope() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Workspace Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        Actor employee = employee(organizationUnit("ACL Workspace Employee Unit"));

        employee.client().perform(get("/api/v1/workspaces/" + workspaceId))
                .andExpect(status().isForbidden());
        employee.client().perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workspaceId + "')]").isEmpty());
        employee.client().perform(get("/api/v1/workspaces/" + workspaceId + "/folders"))
                .andExpect(status().isForbidden());

        String allowGrantId = grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/access", "USER",
                employee.userId(), "workspace.view", "ALLOW");

        employee.client().perform(get("/api/v1/workspaces/" + workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId));
        employee.client().perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workspaceId + "')]").isNotEmpty());

        admin.client().perform(delete("/api/v1/workspaces/" + workspaceId + "/access/" + allowGrantId))
                .andExpect(status().isNoContent());
        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/access", "USER", employee.userId(),
                "workspace.view", "DENY");

        employee.client().perform(get("/api/v1/workspaces/" + workspaceId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
        employee.client().perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + workspaceId + "')]").isEmpty());
        employee.client().perform(get("/api/v1/workspaces/" + workspaceId + "/folders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void folderViewGrantAppliesToSubtreeOnlyAndFolderDenyHidesTheFolder() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Folder List Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        String parentFolder = createFolder(admin.client(), workspaceId, "Parent", null);
        String childFolder = createFolder(admin.client(), workspaceId, "Child", parentFolder);
        String siblingFolder = createFolder(admin.client(), workspaceId, "Sibling", null);
        Actor employee = employee(organizationUnit("ACL Folder List Employee Unit"));

        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/access", "USER", employee.userId(),
                "workspace.view", "ALLOW");
        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/folders/" + parentFolder + "/access",
                "USER", employee.userId(), "folder.view", "ALLOW");

        employee.client().perform(get("/api/v1/workspaces/" + workspaceId + "/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + parentFolder + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + childFolder + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + siblingFolder + "')]").isEmpty());

        grant(admin.client(), "/api/v1/workspaces/" + workspaceId + "/folders/" + childFolder + "/access",
                "USER", employee.userId(), "folder.view", "DENY");
        employee.client().perform(get("/api/v1/workspaces/" + workspaceId + "/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == '" + childFolder + "')]").isEmpty());
    }

    @Test
    void expiredGrantNeverAllowsEvenWhileStoredInHistory() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Expiry Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Expired");
        Actor employee = employee(organizationUnit("ACL Expiry Employee Unit"));

        Permission permission = permissionRepository.findByPermissionCode("document.view").orElseThrow();
        Instant now = Instant.now();
        resourcePermissionRepository.save(new ResourcePermission(UUID.randomUUID(), ResourceType.DOCUMENT,
                UUID.fromString(documentId), PrincipalType.USER, employee.userId(), null, null, false,
                permission.getId(), AclEffect.ALLOW, admin.userId(), GrantSource.MANUAL,
                now.minusSeconds(300), now.minusSeconds(60)));

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
        admin.client().perform(get("/api/v1/documents/" + documentId + "/access")
                        .param("includeHistory", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.length()").value(0))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].active").value(false))
                .andExpect(jsonPath("$.history[0].expiresAt").exists());
    }

    @Test
    void revokingOneGrantKeepsIndependentGrants() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Revoke Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Revoke");
        Actor employee = employee(organizationUnit("ACL Revoke Employee Unit"));
        UUID employeeRoleId = authorizationService.effectiveRoleIds(employee.userId()).stream()
                .findFirst().orElseThrow();

        grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "ROLE", employeeRoleId,
                "document.view", "ALLOW");
        String userGrantId = grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "USER",
                employee.userId(), "document.view", "ALLOW");

        employee.client().perform(get("/api/v1/documents/" + documentId)).andExpect(status().isOk());
        admin.client().perform(delete("/api/v1/documents/" + documentId + "/access/" + userGrantId))
                .andExpect(status().isNoContent());
        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        String roleGrantId = firstActiveGrantId(admin.client(), "/api/v1/documents/" + documentId + "/access",
                "ROLE");
        admin.client().perform(delete("/api/v1/documents/" + documentId + "/access/" + roleGrantId))
                .andExpect(status().isNoContent());
        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessManagementRequiresCapabilityAndCorrectTarget() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Manage Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Manage");
        Actor employee = employee(organizationUnit("ACL Manage Employee Unit"));

        employee.client().perform(get("/api/v1/documents/" + documentId + "/access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
        employee.client().perform(post("/api/v1/documents/" + documentId + "/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "principalType", "USER", "principalId", employee.userId().toString(),
                                "permissionCode", "document.view", "effect", "ALLOW"))))
                .andExpect(status().isForbidden());
        employee.client().perform(get("/api/v1/documents/" + documentId + "/access"))
                .andExpect(status().isForbidden());

        // Cross-workspace folder and cross-document grant ids must not resolve.
        String otherWorkspace = createWorkspace(admin.client(), organizationUnit("ACL Manage Other Unit"));
        String otherFolder = createFolder(admin.client(), otherWorkspace, "Other", null);
        admin.client().perform(get("/api/v1/workspaces/" + workspaceId + "/folders/" + otherFolder + "/access"))
                .andExpect(status().isNotFound());
        String grantId = grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "USER",
                employee.userId(), "document.view", "ALLOW");
        String otherDocument = createDocument(admin.client(), otherWorkspace, null, masterData, "Dokumen Lain");
        admin.client().perform(delete("/api/v1/documents/" + otherDocument + "/access/" + grantId))
                .andExpect(status().isNotFound());
        admin.client().perform(delete("/api/v1/documents/" + documentId + "/access/" + grantId))
                .andExpect(status().isNoContent());

        assertThat(otherWorkspace).isNotBlank();
    }

    @Test
    void grantValidationRejectsInapplicablePermissionPastExpiryAndDuplicates() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL Validation Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Validasi");
        Actor employee = employee(organizationUnit("ACL Validation Employee Unit"));

        admin.client().perform(post("/api/v1/documents/" + documentId + "/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "principalType", "USER", "principalId", employee.userId().toString(),
                                "permissionCode", "request.create", "effect", "ALLOW"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("permissionCode"));

        admin.client().perform(post("/api/v1/documents/" + documentId + "/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "principalType", "USER", "principalId", employee.userId().toString(),
                                "permissionCode", "document.view", "effect", "ALLOW",
                                "expiresAt", Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expiresAt"));

        grant(admin.client(), "/api/v1/documents/" + documentId + "/access", "USER", employee.userId(),
                "document.view", "ALLOW");
        admin.client().perform(post("/api/v1/documents/" + documentId + "/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "principalType", "USER", "principalId", employee.userId().toString(),
                                "permissionCode", "document.view", "effect", "ALLOW"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void systemAdminCannotReadUnownedDocumentThroughAclListing() throws Exception {
        Admin admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("ACL System Admin Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        MasterData masterData = masterData(admin.client());
        String documentId = createDocument(admin.client(), workspaceId, null, masterData, "Dokumen Admin");

        String systemAdminName = uniqueName("sysadmin");
        createUser(systemAdminName, "Admin!Pass2026", false, "SYSTEM_ADMIN");
        Client systemAdmin = newClient();
        systemAdmin.login(systemAdminName, "Admin!Pass2026");

        systemAdmin.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
        // Managing an ACL is not the same as reading content; the control is separate.
        systemAdmin.perform(get("/api/v1/documents/" + documentId + "/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(true));
    }

    // ---------------------------------------------------------------- helpers

    private record Admin(Client client, UUID userId) {
    }

    private record Actor(Client client, UUID userId, String username) {
    }

    private record MasterData(String categoryId, String documentTypeId, String classificationId) {
    }

    private Admin documentAdmin() throws Exception {
        String username = uniqueName("docadmin");
        UUID userId = createUser(username, "DocAdmin!Pass2026", false, "DOCUMENT_ADMIN").getId();
        Client client = newClient();
        client.login(username, "DocAdmin!Pass2026");
        return new Admin(client, userId);
    }

    private Actor employee(OrganizationUnit scopeUnit) throws Exception {
        String username = uniqueName("employee");
        UUID userId = createUser(username, "Employee!Pass2026", false, null).getId();
        authorizationService.assignRole(userId, userId, "EMPLOYEE", ScopeType.ORGANIZATION_UNIT,
                scopeUnit.getId(), null, null);
        Client client = newClient();
        client.login(username, "Employee!Pass2026");
        return new Actor(client, userId, username);
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("U").substring(0, 10), name, null, null, Instant.now()));
    }

    private String createWorkspace(Client client, OrganizationUnit unit) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("WS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Acl"));
        payload.put("ownerOrganizationUnitId", unit.getId().toString());
        String body = client.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createFolder(Client client, String workspaceId, String name, String parentFolderId)
            throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("folderName", name + " " + uniqueName("F").substring(0, 6));
        if (parentFolderId != null) {
            payload.put("parentFolderId", parentFolderId);
        }
        String body = client.perform(post("/api/v1/workspaces/" + workspaceId + "/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private MasterData masterData(Client client) throws Exception {
        String categories = client.perform(get("/api/v1/master-data/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String types = client.perform(get("/api/v1/master-data/document-types"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String classifications = client.perform(get("/api/v1/master-data/classifications"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new MasterData(objectMapper.readTree(categories).get(0).get("id").asText(),
                objectMapper.readTree(types).get(0).get("id").asText(),
                objectMapper.readTree(classifications).get(0).get("id").asText());
    }

    private String createDocument(Client client, String workspaceId, String folderId, MasterData masterData,
                                  String title) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("description", "Dokumen uji ACL");
        payload.put("categoryId", masterData.categoryId());
        payload.put("documentTypeId", masterData.documentTypeId());
        payload.put("classificationId", masterData.classificationId());
        if (folderId != null) {
            payload.put("folderId", folderId);
        }
        String body = client.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }

    private String grant(Client client, String path, String principalType, UUID principalId,
                         String permissionCode, String effect) throws Exception {
        String body = client.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "principalType", principalType,
                                "principalId", principalId.toString(),
                                "permissionCode", permissionCode,
                                "effect", effect))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String firstActiveGrantId(Client client, String path, String principalType) throws Exception {
        String body = client.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var grant : objectMapper.readTree(body).get("active")) {
            if (principalType.equals(grant.get("principalType").asText())) {
                return grant.get("id").asText();
            }
        }
        throw new IllegalStateException("Grant " + principalType + " tidak ditemukan.");
    }
}
