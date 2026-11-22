package com.kcic.connect.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.authorization.domain.ScopeType;
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
 * Phase 6 gate: request creation and routing, approval/rejection guards, access
 * fulfillment through the ACL, document linking, HTTPS external links, notification
 * delivery/read isolation, and visibility scoping.
 */
class RequestWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Test
    void accessRequestIsRoutedApprovedFulfilledAndNotifiesParticipants() throws Exception {
        Actor admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("Workflow Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        String documentId = createDocument(admin.client(), workspaceId, "Dokumen Workflow");
        Actor employee = employee(organizationUnit("Workflow Employee Unit"));

        String requestId = createRequest(employee.client(), Map.of(
                "requestType", "ACCESS",
                "title", "Akses dokumen workflow",
                "reason", "Dibutuhkan untuk pekerjaan unit.",
                "targetDocumentId", documentId));

        employee.client().perform(get("/api/v1/requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.summary.assignedToUserId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.summary.requesterUserId").value(employee.userId().toString()))
                .andExpect(jsonPath("$.approvals.length()").value(1))
                .andExpect(jsonPath("$.approvals[0].decision").value("PENDING"));

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        admin.client().perform(get("/api/v1/requests").param("scope", "assigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + requestId + "')]").isNotEmpty());

        admin.client().perform(post("/api/v1/requests/" + requestId + "/start-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Sedang ditinjau."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("UNDER_REVIEW"));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Disetujui."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("APPROVED"));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fulfillmentType", "ACCESS_GRANTED",
                                "note", "Akses diberikan."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("FULFILLED"))
                .andExpect(jsonPath("$.fulfillments.length()").value(1))
                .andExpect(jsonPath("$.fulfillments[0].type").value("ACCESS_GRANTED"))
                .andExpect(jsonPath("$.fulfillments[0].items[0].documentId").value(documentId));

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(documentId));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Ulangi."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        admin.client().perform(get("/api/v1/requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[?(@.toStatus == 'APPROVED')]").isNotEmpty())
                .andExpect(jsonPath("$.history[?(@.toStatus == 'FULFILLED')]").isNotEmpty());

        employee.client().perform(post("/api/v1/requests/" + requestId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Selesai."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("CLOSED"));

        String requesterNotifications = employee.client().perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(requesterNotifications)
                .contains("REQUEST_SUBMITTED").contains("REQUEST_APPROVED").contains("REQUEST_FULFILLED");

        String adminNotificationId = admin.client().perform(get("/api/v1/notifications")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("REQUEST_ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        String notificationId = objectMapper.readTree(adminNotificationId)
                .get("items").get(0).get("id").asText();

        employee.client().perform(post("/api/v1/notifications/" + notificationId + "/read"))
                .andExpect(status().isNotFound());

        admin.client().perform(post("/api/v1/notifications/" + notificationId + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        admin.client().perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void rejectRequiresReasonAndUnauthorizedUsersCannotDecide() throws Exception {
        Actor admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("Reject Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        String documentId = createDocument(admin.client(), workspaceId, "Dokumen Reject");
        Actor employee = employee(organizationUnit("Reject Employee Unit"));
        Actor outsider = employee(organizationUnit("Reject Outsider Unit"));

        String requestId = createRequest(employee.client(), Map.of(
                "requestType", "ACCESS",
                "title", "Akses dokumen reject",
                "reason", "Kebutuhan audit.",
                "targetDocumentId", documentId));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));

        outsider.client().perform(post("/api/v1/requests/" + requestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Bukan wewenang."))))
                .andExpect(status().isForbidden());

        outsider.client().perform(get("/api/v1/requests/" + requestId))
                .andExpect(status().isForbidden());

        outsider.client().perform(get("/api/v1/requests").param("scope", "assigned"))
                .andExpect(status().isForbidden());

        admin.client().perform(post("/api/v1/requests/" + requestId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Data belum lengkap."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REJECTED"))
                .andExpect(jsonPath("$.approvals[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$.approvals[0].decisionReason").value("Data belum lengkap."));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fulfillmentType", "ACCESS_GRANTED"))))
                .andExpect(status().isConflict());

        employee.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        employee.client().perform(post("/api/v1/requests/" + requestId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isConflict());
    }

    @Test
    void deliveryRequestLinksDocumentsAndValidatesExternalHttpsLink() throws Exception {
        Actor admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("Delivery Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        String documentId = createDocument(admin.client(), workspaceId, "Dokumen Delivery");
        Actor employee = employee(organizationUnit("Delivery Employee Unit"));

        String requestId = createRequest(employee.client(), Map.of(
                "requestType", "DOCUMENT_DELIVERY",
                "title", "Butuh dokumen delivery",
                "reason", "Untuk rapat koordinasi.",
                "targetOrganizationUnitId", unit.getId().toString()));

        admin.client().perform(post("/api/v1/requests/" + requestId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk());

        admin.client().perform(post("/api/v1/requests/" + requestId + "/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fulfillmentType", "DOCUMENT_LINKED",
                                "documentIds", java.util.List.of(documentId),
                                "note", "Dokumen dilampirkan."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("FULFILLED"))
                .andExpect(jsonPath("$.fulfillments[0].items[0].documentTitle").value("Dokumen Delivery"));

        String secondRequest = createRequest(employee.client(), Map.of(
                "requestType", "DOCUMENT_DELIVERY",
                "title", "Butuh tautan eksternal",
                "reason", "Dokumen berada di penyimpanan eksternal.",
                "targetOrganizationUnitId", unit.getId().toString()));

        admin.client().perform(post("/api/v1/requests/" + secondRequest + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk());

        admin.client().perform(post("/api/v1/requests/" + secondRequest + "/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fulfillmentType", "EXTERNAL_LINK_REGISTERED",
                                "externalUrl", "http://example.com/doc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        admin.client().perform(post("/api/v1/requests/" + secondRequest + "/fulfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fulfillmentType", "EXTERNAL_LINK_REGISTERED",
                                "externalUrl", "https://example.com/doc",
                                "linkTitle", "Dokumen eksternal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillments[0].externalUrl").value("https://example.com/doc"))
                .andExpect(jsonPath("$.fulfillments[0].externalLinkTitle").value("Dokumen eksternal"));
    }

    @Test
    void ownAndAssignedScopesAreSeparated() throws Exception {
        Actor admin = documentAdmin();
        OrganizationUnit unit = organizationUnit("Scope Unit");
        String workspaceId = createWorkspace(admin.client(), unit);
        String documentId = createDocument(admin.client(), workspaceId, "Dokumen Scope");
        Actor employee = employee(organizationUnit("Scope Employee Unit"));
        Actor other = employee(organizationUnit("Scope Other Unit"));

        String requestId = createRequest(employee.client(), Map.of(
                "requestType", "ACCESS",
                "title", "Akses dokumen scope",
                "reason", "Kebutuhan unit.",
                "targetDocumentId", documentId));

        createRequest(other.client(), Map.of(
                "requestType", "DOCUMENT_DELIVERY",
                "title", "Request orang lain",
                "reason", "Kebutuhan lain.",
                "targetOrganizationUnitId", unit.getId().toString()));

        employee.client().perform(get("/api/v1/requests").param("scope", "own"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + requestId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.totalItems").value(1));

        admin.client().perform(get("/api/v1/requests").param("scope", "assigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + requestId + "')]").isNotEmpty());

        admin.client().perform(get("/api/v1/requests")
                        .param("scope", "assigned")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());

        admin.client().perform(get("/api/v1/requests")
                        .param("scope", "assigned")
                        .param("status", "TIDAK_ADAP"))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- helpers

    private record Actor(Client client, UUID userId, String username) {
    }

    private Actor documentAdmin() throws Exception {
        String username = uniqueName("reqadmin");
        UUID userId = createUser(username, "ReqAdmin!Pass2026", false, "DOCUMENT_ADMIN").getId();
        Client client = newClient();
        client.login(username, "ReqAdmin!Pass2026");
        return new Actor(client, userId, username);
    }

    private Actor employee(OrganizationUnit scopeUnit) throws Exception {
        String username = uniqueName("reqemployee");
        UUID userId = createUser(username, "ReqEmployee!Pass2026", false, null).getId();
        authorizationService.assignRole(userId, userId, "EMPLOYEE", ScopeType.ORGANIZATION_UNIT,
                scopeUnit.getId(), null, null);
        Client client = newClient();
        client.login(username, "ReqEmployee!Pass2026");
        return new Actor(client, userId, username);
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("RU").substring(0, 10), name, null, null, Instant.now()));
    }

    private String createWorkspace(Client client, OrganizationUnit unit) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("WS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Req"));
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
        payload.put("description", "Dokumen uji workflow");
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

    private String createRequest(Client client, Map<String, Object> payload) throws Exception {
        String body = client.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }
}
