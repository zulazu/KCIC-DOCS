package com.kcic.connect.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class DocumentIntegrationTest extends IntegrationTestSupport {

    private static final String PDF_CONTENT = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Test
    void documentLifecycleSearchVersioningContentAndFavorites() throws Exception {
        Client admin = documentAdminClient();
        OrganizationUnit unit = organizationUnit("Document Lifecycle Unit");
        String workspaceId = createWorkspace(admin, unit);
        MasterData masterData = masterData(admin);

        String code = uniqueName("DOC").toUpperCase();
        String documentId = createDocument(admin, workspaceId, masterData, code, "Kebijakan Perjalanan Dinas");
        assertThat(documentId).isNotBlank();

        admin.perform(get("/api/v1/documents")
                        .param("workspaceId", workspaceId)
                        .param("keyword", "perjalanan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(documentId))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].workspaceName").exists());

        admin.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDocumentPayload(admin, workspaceId,
                                masterData, code, "Kode Duplikat"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("documentCode"));

        admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(pdfFile("kebijakan-v1.pdf"))
                        .param("changeSummary", "Versi pertama")
                        .param("versionLabel", "v1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.contentSource").value("STORAGE_OBJECT"))
                .andExpect(jsonPath("$.checksumSha256").isNotEmpty());

        MvcResult firstVersion = admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(pdfFile("kebijakan.pdf"))
                        .param("changeSummary", "Revisi kedua")
                        .param("versionLabel", "v2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andReturn();
        String firstVersionId = objectMapper.readTree(firstVersion.getResponse().getContentAsString())
                .get("id").asText();

        org.assertj.core.api.Assertions.assertThat(firstVersionId).isNotBlank();

        admin.perform(get("/api/v1/documents/" + documentId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));

        admin.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.currentVersionNumber").value(2))
                .andExpect(jsonPath("$.versionCount").value(2))
                .andExpect(jsonPath("$.permissions.canUpload").value(true))
                .andExpect(jsonPath("$.permissions.canDownload").value(true));

        String versionsBody = admin.perform(get("/api/v1/documents/" + documentId + "/versions"))
                .andReturn().getResponse().getContentAsString();
        JsonNode versions = objectMapper.readTree(versionsBody);
        String versionTwoId = versions.get(0).get("id").asText();
        String versionOneId = versions.get(1).get("id").asText();

        admin.perform(post("/api/v1/documents/" + documentId + "/versions/" + versionOneId + "/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionOneId))
                .andExpect(jsonPath("$.status").value("CURRENT"));

        admin.perform(get("/api/v1/documents/" + documentId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[1].status").value("CURRENT"));

        MockHttpServletResponse inline = admin.perform(get("/api/v1/documents/" + documentId + "/versions/"
                        + versionOneId + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")))
                .andReturn().getResponse();
        assertThat(inline.getContentAsByteArray()).isEqualTo(PDF_CONTENT.getBytes(StandardCharsets.UTF_8));

        admin.perform(get("/api/v1/documents/" + documentId + "/versions/" + versionOneId + "/content")
                        .param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));

        admin.perform(put("/api/v1/documents/" + documentId + "/favorite"))
                .andExpect(status().isNoContent());
        admin.perform(get("/api/v1/documents/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + documentId + "')].favorited").value(true));
        admin.perform(delete("/api/v1/documents/" + documentId + "/favorite"))
                .andExpect(status().isNoContent());
        admin.perform(get("/api/v1/documents/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + documentId + "')]").isEmpty());

        admin.perform(post("/api/v1/documents/" + documentId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ACTIVE"));
        admin.perform(post("/api/v1/documents/" + documentId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ARCHIVED"));

        admin.perform(get("/api/v1/documents").param("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + documentId + "')]").isEmpty());
        admin.perform(get("/api/v1/documents")
                        .param("workspaceId", workspaceId)
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + documentId + "')].status").value("ARCHIVED"));

        admin.perform(post("/api/v1/documents/" + documentId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ACTIVE"));

        admin.perform(get("/api/v1/documents/" + documentId + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.actionCode == 'DOCUMENT_VERSION_UPLOADED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.actionCode == 'DOCUMENT_ARCHIVED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.actionCode == 'DOCUMENT_DOWNLOADED')]").isNotEmpty());

        assertThat(versionTwoId).isNotBlank();
    }

    @Test
    void externalLinksRequireHttpsAndCannotBeStreamed() throws Exception {
        Client admin = documentAdminClient();
        String workspaceId = createWorkspace(admin, organizationUnit("External Link Unit"));
        MasterData masterData = masterData(admin);
        String documentId = createDocument(admin, workspaceId, masterData, uniqueName("LINK").toUpperCase(),
                "Tautan Kebijakan");

        admin.perform(post("/api/v1/documents/" + documentId + "/versions/external-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "http://sharepoint.com/sites/kebijakan",
                                "linkTitle", "Kebijakan"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));

        String body = admin.perform(post("/api/v1/documents/" + documentId + "/versions/external-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "https://kcic.sharepoint.com/sites/kebijakan/doc.docx",
                                "linkTitle", "Kebijakan", "versionLabel", "Office"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentSource").value("EXTERNAL_LINK"))
                .andExpect(jsonPath("$.externalHost").value("kcic.sharepoint.com"))
                .andReturn().getResponse().getContentAsString();
        String versionId = objectMapper.readTree(body).get("id").asText();

        admin.perform(get("/api/v1/documents/" + documentId + "/versions/" + versionId + "/external-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://kcic.sharepoint.com/sites/kebijakan/doc.docx"))
                .andExpect(jsonPath("$.warning").isNotEmpty());

        admin.perform(get("/api/v1/documents/" + documentId + "/versions/" + versionId + "/content"))
                .andExpect(status().isConflict());
    }

    @Test
    void uploadValidationEnforcesFilePolicyAndMagicBytes() throws Exception {
        Client admin = documentAdminClient();
        String workspaceId = createWorkspace(admin, organizationUnit("Upload Validation Unit"));
        MasterData masterData = masterData(admin);
        String documentId = createDocument(admin, workspaceId, masterData, uniqueName("UPL").toUpperCase(),
                "Dokumen Unggah");

        admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(new MockMultipartFile("file", "script.exe", "application/octet-stream",
                                new byte[]{0x4D, 0x5A})))
                .andExpect(status().isUnsupportedMediaType());

        admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(new MockMultipartFile("file", "palsu.pdf", "application/pdf",
                                "bukan pdf".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnsupportedMediaType());

        admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(new MockMultipartFile("file", "kosong.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        admin.perform(post("/api/v1/documents/" + documentId + "/archive"))
                .andExpect(status().isOk());

        admin.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(pdfFile("terlambat.pdf")))
                .andExpect(status().isConflict());
    }

    @Test
    void employeeAndSystemAdminCannotReadUnownedDocumentContent() throws Exception {
        Client admin = documentAdminClient();
        OrganizationUnit unit = organizationUnit("Restricted Document Unit");
        String workspaceId = createWorkspace(admin, unit);
        MasterData masterData = masterData(admin);
        String documentId = createDocument(admin, workspaceId, masterData, uniqueName("SEC").toUpperCase(),
                "Dokumen Terbatas");

        String employeeName = uniqueName("docemployee");
        UUID employeeId = createUser(employeeName, "Employee!Pass2026", false, null).getId();
        OrganizationUnit unrelatedUnit = organizationUnit("Unrelated Employee Unit");
        authorizationService.assignRole(employeeId, employeeId, "EMPLOYEE",
                com.kcic.connect.authorization.domain.ScopeType.ORGANIZATION_UNIT,
                unrelatedUnit.getId(), null, null);
        Client employee = newClient();
        employee.login(employeeName, "Employee!Pass2026");

        employee.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));
        employee.perform(get("/api/v1/documents").param("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        employee.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDocumentPayload(employee, workspaceId,
                                masterData, uniqueName("NOACCESS").toUpperCase(), "Tidak Boleh"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        String systemAdminName = uniqueName("sysadmin");
        createUser(systemAdminName, "Admin!Pass2026", false, "SYSTEM_ADMIN");
        Client systemAdmin = newClient();
        systemAdmin.login(systemAdminName, "Admin!Pass2026");
        systemAdmin.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        assertThat(employeeId).isNotNull();
    }

    @Test
    void ownerChangeRequiresReasonAndKeepsHistory() throws Exception {
        Client admin = documentAdminClient();
        OrganizationUnit unit = organizationUnit("Owner History Unit");
        String workspaceId = createWorkspace(admin, unit);
        MasterData masterData = masterData(admin);
        UUID ownerUserId = createUser(uniqueName("owner"), "Owner!Pass2026", false, "EMPLOYEE").getId();
        String documentId = createDocument(admin, workspaceId, masterData, uniqueName("OWN").toUpperCase(),
                "Dokumen Kepemilikan");

        Map<String, Object> withNewOwner = createDocumentPayload(admin, workspaceId, masterData, null,
                "Dokumen Kepemilikan");
        withNewOwner.remove("documentCode");
        withNewOwner.put("ownerOrganizationUnitId", null);
        withNewOwner.put("ownerUserId", ownerUserId.toString());

        admin.perform(put("/api/v1/documents/" + documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withNewOwner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ownerChangeReason"));

        withNewOwner.put("ownerChangeReason", "Rotasi penanggung jawab dokumen");
        admin.perform(put("/api/v1/documents/" + documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withNewOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ownerUserId").value(ownerUserId.toString()))
                .andExpect(jsonPath("$.ownerHistory.length()").value(2))
                .andExpect(jsonPath("$.ownerHistory[0].validUntil").doesNotExist())
                .andExpect(jsonPath("$.ownerHistory[1].validUntil").isNotEmpty());

        admin.perform(get("/api/v1/documents/" + documentId + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.actionCode == 'DOCUMENT_OWNER_CHANGED')]").isNotEmpty());

        // Unit-only ownership has no owner user; catalog and detail mapping must tolerate null user ids.
        withNewOwner.put("ownerUserId", null);
        withNewOwner.put("ownerOrganizationUnitId", unit.getId().toString());
        admin.perform(put("/api/v1/documents/" + documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withNewOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.summary.ownerOrganizationUnitId").value(unit.getId().toString()))
                .andExpect(jsonPath("$.summary.ownerOrganizationUnitName").value(unit.getUnitName()))
                .andExpect(jsonPath("$.ownerHistory.length()").value(3));

        admin.perform(get("/api/v1/documents").param("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + documentId + "')].ownerOrganizationUnitName")
                        .value(unit.getUnitName()));
    }

    // ---------------------------------------------------------------- helpers

    private Client documentAdminClient() throws Exception {
        String adminName = uniqueName("docadmin");
        createUser(adminName, "DocAdmin!Pass2026", false, "DOCUMENT_ADMIN");
        Client admin = newClient();
        admin.login(adminName, "DocAdmin!Pass2026");
        return admin;
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("U").substring(0, 10), name, null, null, Instant.now()));
    }

    private String createWorkspace(Client client, OrganizationUnit unit) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("WS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Dok"));
        payload.put("ownerOrganizationUnitId", unit.getId().toString());
        String body = client.perform(post("/api/v1/workspaces")
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

    private String createDocument(Client client, String workspaceId, MasterData masterData, String code,
                                  String title) throws Exception {
        String body = client.perform(post("/api/v1/workspaces/" + workspaceId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDocumentPayload(client, workspaceId,
                                masterData, code, title))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.title").value(title))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }

    private Map<String, Object> createDocumentPayload(Client client, String workspaceId, MasterData masterData,
                                                      String code, String title) {
        Map<String, Object> payload = new HashMap<>();
        if (code != null) {
            payload.put("documentCode", code);
        }
        payload.put("title", title);
        payload.put("description", "Dokumen uji integrasi");
        payload.put("categoryId", masterData.categoryId());
        payload.put("documentTypeId", masterData.documentTypeId());
        payload.put("classificationId", masterData.classificationId());
        return payload;
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                PDF_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    private record MasterData(String categoryId, String documentTypeId, String classificationId) {
    }
}
