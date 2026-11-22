package com.kcic.connect.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.organization.domain.AssignmentType;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Phase 8 gate: template lifecycle, publish permission, same-document version
 * reference, catalog visibility, version history per permission, usage events,
 * and audit trail.
 */
class TemplateIntegrationTest extends IntegrationTestSupport {

    private static final String PDF_CONTENT = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";
    private static final String MANAGER_PASSWORD = "TplManager!Pass2026";
    private static final String CONTRIBUTOR_PASSWORD = "TplContrib!Pass2026";
    private static final String EMPLOYEE_PASSWORD = "TplEmployee!Pass2026";
    private static final String ADMIN_PASSWORD = "TplAdmin!Pass2026";

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private UserOrganizationAssignmentRepository organizationAssignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lifecyclePublishesCurrentVersionAndKeepsDraftsOutOfCommonCatalog() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Template Owner");
        Actor manager = actor(ownerUnit, "UNIT_MANAGER", "tplmanager", MANAGER_PASSWORD);
        Actor contributor = actor(ownerUnit, "CONTRIBUTOR", "tplcontrib", CONTRIBUTOR_PASSWORD);
        Actor outsider = actor(organizationUnit("Template Outsider"), "EMPLOYEE", "tplemployee",
                EMPLOYEE_PASSWORD);
        Actor admin = globalActor("tpladmin", ADMIN_PASSWORD, "DOCUMENT_ADMIN");

        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Template Kebijakan");
        String firstVersionId = uploadVersion(manager.client(), documentId, "v1");
        String currentVersionId = uploadVersion(manager.client(), documentId, "v2");

        String templateCode = uniqueName("TPL_KEBIJAKAN").toUpperCase();
        String body = contributor.client().perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templatePayload(documentId, templateCode,
                                "Template Kebijakan Perjalanan"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.summary.templateCode").value(templateCode))
                .andExpect(jsonPath("$.summary.currentPublishedVersionId").doesNotExist())
                .andExpect(jsonPath("$.permissions.canPublish").value(false))
                .andExpect(jsonPath("$.permissions.canEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        String templateId = objectMapper.readTree(body).get("summary").get("id").asText();

        contributor.client().perform(put("/api/v1/templates/" + templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "templateCode", templateCode,
                                "templateName", "Template Kebijakan Perjalanan Dinas",
                                "description", "Diperbarui sebelum publikasi."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.templateName").value("Template Kebijakan Perjalanan Dinas"));

        manager.client().perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')]").isEmpty());
        outsider.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isNotFound());

        contributor.client().perform(post("/api/v1/templates/" + templateId + "/publish"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));

        String published = admin.client().perform(post("/api/v1/templates/" + templateId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("versionId", currentVersionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.summary.currentPublishedVersionId").value(currentVersionId))
                .andExpect(jsonPath("$.summary.currentPublishedVersionNumber").value(2))
                .andExpect(jsonPath("$.summary.publishedByName").isNotEmpty())
                .andExpect(jsonPath("$.versionHistoryVisible").value(true))
                .andExpect(jsonPath("$.versionHistory.length()").value(2))
                .andExpect(jsonPath("$.versionHistory[?(@.id == '" + currentVersionId + "')].status",
                        org.hamcrest.Matchers.contains("CURRENT")))
                .andExpect(jsonPath("$.versionHistory[?(@.id == '" + firstVersionId + "')].status",
                        org.hamcrest.Matchers.contains("SUPERSEDED")))
                .andExpect(jsonPath("$.versionHistory[?(@.id == '" + currentVersionId + "')].currentlyPublished",
                        org.hamcrest.Matchers.contains(true)))
                .andReturn().getResponse().getContentAsString();
        admin.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.currentPublishedVersionId").value(currentVersionId));

        admin.client().perform(post("/api/v1/templates/" + templateId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("versionId", firstVersionId))))
                .andExpect(status().isConflict());
        admin.client().perform(post("/api/v1/templates/" + templateId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("versionId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());

        manager.client().perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')].currentPublishedVersionId",
                        org.hamcrest.Matchers.contains(currentVersionId)));
        outsider.client().perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')]").isEmpty());
        outsider.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isNotFound());

        ResourcePermission temporaryView = authorizationService.grant(admin.userId(), ResourceType.DOCUMENT,
                UUID.fromString(documentId), PrincipalType.USER, outsider.userId(), PermissionCodes.DOCUMENT_VIEW,
                AclEffect.ALLOW, GrantSource.MANUAL, null);
        outsider.client().perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')]").isNotEmpty());
        outsider.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionHistoryVisible").value(true))
                .andExpect(jsonPath("$.versionHistory.length()").value(2))
                .andExpect(jsonPath("$.permissions.canViewDocument").value(true))
                .andExpect(jsonPath("$.permissions.canPublish").value(false));
        authorizationService.revokeGrant(admin.userId(), temporaryView.getId());
        outsider.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isNotFound());

        outsider.client().perform(get("/api/v1/templates").param("status", "ARCHIVED"))
                .andExpect(status().isForbidden());

        contributor.client().perform(post("/api/v1/templates/" + templateId + "/archive"))
                .andExpect(status().isForbidden());
        admin.client().perform(post("/api/v1/templates/" + templateId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.summary.currentPublishedVersionId").value(currentVersionId));
        admin.client().perform(post("/api/v1/templates/" + templateId + "/archive"))
                .andExpect(status().isConflict());
        admin.client().perform(post("/api/v1/templates/" + templateId + "/publish"))
                .andExpect(status().isConflict());

        manager.client().perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')]").isEmpty());
        admin.client().perform(get("/api/v1/templates").param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + templateId + "')]").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject("select count(*) from \"audit\".audit_events "
                + "where action_code = 'TEMPLATE_CREATED' and resource_id = ?", Long.class,
                UUID.fromString(templateId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from \"audit\".audit_events "
                + "where action_code = 'TEMPLATE_UPDATED' and resource_id = ?", Long.class,
                UUID.fromString(templateId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from \"audit\".audit_events "
                + "where action_code = 'TEMPLATE_PUBLISHED' and resource_id = ?", Long.class,
                UUID.fromString(templateId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from \"audit\".audit_events "
                + "where action_code = 'TEMPLATE_ARCHIVED' and resource_id = ?", Long.class,
                UUID.fromString(templateId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from repository.document_versions "
                + "where document_id = ?", Long.class, UUID.fromString(documentId))).isEqualTo(2);
    }

    @Test
    void createRejectsDuplicateDocumentAndCodeAndUsageRespectsPermissions() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Template Usage Owner");
        Actor manager = actor(ownerUnit, "UNIT_MANAGER", "tplusmanager", MANAGER_PASSWORD);
        Actor contributor = actor(ownerUnit, "CONTRIBUTOR", "tpluscontrib", CONTRIBUTOR_PASSWORD);
        Actor admin = globalActor("tplusadmin", ADMIN_PASSWORD, "DOCUMENT_ADMIN");
        Actor recipient = actor(organizationUnit("Template Usage Recipient"), "EMPLOYEE", "tplusemployee",
                EMPLOYEE_PASSWORD);
        Actor stranger = actor(organizationUnit("Template Usage Stranger"), "EMPLOYEE", "tplusstranger",
                EMPLOYEE_PASSWORD);

        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Template Perjalanan");
        String otherDocumentId = createDocument(manager.client(), workspaceId, "Dokumen Lain");
        uploadVersion(manager.client(), documentId, "v1");
        uploadVersion(manager.client(), otherDocumentId, "v1");

        String code = uniqueName("TPL_USAGE").toUpperCase();
        String templateId = createTemplate(contributor, documentId, code);

        contributor.client().perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templatePayload(documentId,
                                uniqueName("TPL_DUP").toUpperCase(), "Duplikat dokumen"))))
                .andExpect(status().isConflict());
        contributor.client().perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templatePayload(otherDocumentId, code,
                                "Duplikat kode"))))
                .andExpect(status().isConflict());
        stranger.client().perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templatePayload(otherDocumentId,
                                uniqueName("TPL_FORBID").toUpperCase(), "Tanpa izin"))))
                .andExpect(status().isForbidden());

        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "VIEW"))))
                .andExpect(status().isNotFound());

        admin.client().perform(post("/api/v1/templates/" + templateId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.summary.currentPublishedVersionNumber").value(1))
                .andExpect(jsonPath("$.summary.currentPublishedVersionId").isNotEmpty());

        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "VIEW"))))
                .andExpect(status().isNotFound());

        ResourcePermission documentView = authorizationService.grant(admin.userId(), ResourceType.DOCUMENT,
                UUID.fromString(documentId), PrincipalType.USER, recipient.userId(), PermissionCodes.DOCUMENT_VIEW,
                AclEffect.ALLOW, GrantSource.MANUAL, null);

        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "VIEW"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageType").value("VIEW"))
                .andExpect(jsonPath("$.versionNumber").value(1));
        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "COPY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageType").value("COPY"));
        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "DOWNLOAD"))))
                .andExpect(status().isForbidden());
        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "UNKNOWN"))))
                .andExpect(status().isBadRequest());

        admin.client().perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usageCount").value(2))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.downloadCount").value(0))
                .andExpect(jsonPath("$.copyCount").value(1));

        assertThat(jdbcTemplate.queryForObject("select count(*) from \"template\".template_usage_events "
                + "where template_id = ?", Long.class, UUID.fromString(templateId))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from \"audit\".audit_events "
                + "where action_code = 'TEMPLATE_USED' and resource_id = ?", Long.class,
                UUID.fromString(templateId))).isEqualTo(2);

        authorizationService.grant(admin.userId(), ResourceType.DOCUMENT, UUID.fromString(documentId),
                PrincipalType.USER, recipient.userId(), PermissionCodes.DOCUMENT_DOWNLOAD, AclEffect.ALLOW,
                GrantSource.MANUAL, null);
        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "DOWNLOAD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageType").value("DOWNLOAD"));

        authorizationService.revokeGrant(admin.userId(), documentView.getId());
        recipient.client().perform(post("/api/v1/templates/" + templateId + "/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("usageType", "VIEW"))))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- helpers

    private record Actor(Client client, UUID userId, String username) {
    }

    private Actor actor(OrganizationUnit unit, String roleCode, String prefix, String password) throws Exception {
        String username = uniqueName(prefix);
        UUID userId = createUser(username, password, false, null).getId();
        authorizationService.assignRole(userId, userId, roleCode, ScopeType.ORGANIZATION_UNIT, unit.getId(), null,
                null);
        joinUnit(userId, unit.getId());
        Client client = newClient();
        client.login(username, password);
        return new Actor(client, userId, username);
    }

    private Actor globalActor(String prefix, String password, String roleCode) throws Exception {
        String username = uniqueName(prefix);
        UUID userId = createUser(username, password, false, roleCode).getId();
        Client client = newClient();
        client.login(username, password);
        return new Actor(client, userId, username);
    }

    private void joinUnit(UUID userId, UUID unitId) {
        organizationAssignmentRepository.save(new UserOrganizationAssignment(UUID.randomUUID(), userId, unitId,
                AssignmentType.PRIMARY, Instant.now(), null, userId));
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("DU").substring(0, 10), name, null, null, Instant.now()));
    }

    private Map<String, Object> templatePayload(String documentId, String code, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", documentId);
        payload.put("templateCode", code);
        payload.put("templateName", name);
        payload.put("description", "Template uji fase 8.");
        return payload;
    }

    private String createTemplate(Actor actor, String documentId, String code) throws Exception {
        String body = actor.client().perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templatePayload(documentId, code, "Template Uji"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }

    private String createWorkspace(Client client, OrganizationUnit unit) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("TWS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Template"));
        payload.put("ownerOrganizationUnitId", unit.getId().toString());
        String body = client.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createDocument(Client client, String workspaceId, String title) throws Exception {
        String categories = client.perform(get("/api/v1/master-data/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String types = client.perform(get("/api/v1/master-data/document-types"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String classifications = client.perform(get("/api/v1/master-data/classifications"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("description", "Dokumen sumber template");
        payload.put("categoryId", objectMapper.readTree(categories).get(0).get("id").asText());
        payload.put("documentTypeId", objectMapper.readTree(types).get(0).get("id").asText());
        payload.put("classificationId", objectMapper.readTree(classifications).get(0).get("id").asText());
        String body = client.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }

    private String uploadVersion(Client client, String documentId, String label) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "template.pdf", "application/pdf",
                PDF_CONTENT.getBytes(StandardCharsets.UTF_8));
        String body = client.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(file)
                        .param("changeSummary", "Versi template " + label)
                        .param("versionLabel", label))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
