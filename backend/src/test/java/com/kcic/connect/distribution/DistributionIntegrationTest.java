package com.kcic.connect.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.organization.domain.AssignmentType;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Phase 7 gate: recipient snapshot, delegated grants, READ tracking, revoke isolation,
 * expiry enforcement, and cross-user visibility.
 */
class DistributionIntegrationTest extends IntegrationTestSupport {

    private static final String PDF_CONTENT = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private UserOrganizationAssignmentRepository organizationAssignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeePlacementRepository employeePlacementRepository;

    @Test
    void distributionSkipsTerminatedEmployeesAndRejectsExplicitRecipient() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Lifecycle Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Dokumen Lifecycle");
        uploadVersion(manager.client(), documentId);

        OrganizationUnit division = organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("DIV").substring(0, 10), "Biro/Division", null, UnitType.DIVISION, (UUID) null,
                Instant.now()));
        Actor activeMember = employee(division);

        String terminatedUsername = uniqueName("terminated");
        UUID terminatedUserId = createUser(terminatedUsername, "Terminated!Pass2026", false, null).getId();
        UUID terminatedEmployeeId = UUID.randomUUID();
        employeeRepository.save(new Employee(terminatedEmployeeId, uniqueName("NIP"), "Terminated Employee",
                uniqueName("mail") + "@kcic.test", EmploymentStatus.TERMINATED, manager.userId(), Instant.now()));
        employeePlacementRepository.save(new EmployeePlacement(UUID.randomUUID(), terminatedEmployeeId,
                division.getId(), Instant.now(), manager.userId()));
        User terminatedAccount = userRepository.findById(terminatedUserId).orElseThrow();
        terminatedAccount.linkEmployee(terminatedEmployeeId, manager.userId(), Instant.now());
        userRepository.save(terminatedAccount);

        String body = manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Distribusi lifecycle",
                                List.of(), List.of(division.getId().toString()),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.recipientCount").value(1))
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + activeMember.userId() + "')]").isNotEmpty())
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + terminatedUserId + "')]").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(activeMember.userId().toString());

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Distribusi ditolak",
                                List.of(terminatedUserId.toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("recipientUserIds"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(
                        "Penerima \"Test " + terminatedUsername + "\" tidak aktif secara kepegawaian."));
    }

    @Test
    void unitRecipientsIncludeEmployeePlacementsAndSnapshotSurvivesTransfer() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Placement Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Dokumen Placement");
        uploadVersion(manager.client(), documentId);

        OrganizationUnit division = organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("DIV").substring(0, 10), "Biro/Division", null, UnitType.DIVISION, (UUID) null,
                Instant.now()));
        OrganizationUnit otherDivision = organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("DIV").substring(0, 10), "Biro/Division Lain", null, UnitType.DIVISION, (UUID) null,
                Instant.now()));

        String username = uniqueName("placed");
        UUID userId = createUser(username, "Placed!Pass2026", false, null).getId();
        UUID employeeId = UUID.randomUUID();
        employeeRepository.save(new Employee(employeeId, uniqueName("NIP"), "Placed Employee",
                uniqueName("mail") + "@kcic.test", EmploymentStatus.ACTIVE, manager.userId(), Instant.now()));
        EmployeePlacement placement = employeePlacementRepository.save(new EmployeePlacement(UUID.randomUUID(),
                employeeId, division.getId(), Instant.now(), manager.userId()));
        User user = userRepository.findById(userId).orElseThrow();
        user.linkEmployee(employeeId, manager.userId(), Instant.now());
        userRepository.save(user);

        String body = manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Distribusi placement",
                                List.of(), List.of(division.getId().toString()),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.recipientCount").value(1))
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + userId + "')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String distributionId = objectMapper.readTree(body).get("summary").get("id").asText();

        Client recipient = newClient();
        recipient.login(username, "Placed!Pass2026");
        recipient.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        placement.close(Instant.now());
        employeePlacementRepository.saveAndFlush(placement);
        employeePlacementRepository.saveAndFlush(new EmployeePlacement(UUID.randomUUID(), employeeId,
                otherDivision.getId(), Instant.now(), manager.userId()));

        manager.client().perform(get("/api/v1/distributions/" + distributionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + userId + "')]").isNotEmpty());
        recipient.perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());
    }

    @Test
    void sendSnapshotsUnitMembersGrantsAccessAndTracksRead() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Distribution Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Kebijakan Distribusi");
        String versionId = uploadVersion(manager.client(), documentId);

        Actor recipient = employee(organizationUnit("Distribution Recipient"));
        Actor teammate = employee(ownerUnit);
        Actor inactiveMember = inactiveUser(ownerUnit);
        Actor outsider = employee(organizationUnit("Distribution Outsider"));

        recipient.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Distribusi kebijakan baru");
        payload.put("message", "Mohon dipelajari sebelum rapat.");
        payload.put("expiresAt", Instant.now().plusSeconds(3600).toString());
        payload.put("recipientUserIds", List.of(recipient.userId().toString()));
        payload.put("recipientOrganizationUnitIds", List.of(ownerUnit.getId().toString()));
        payload.put("items", List.of(Map.of("documentId", documentId, "accessLevel", "DOWNLOAD")));

        String body = manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("SENT"))
                .andExpect(jsonPath("$.summary.itemCount").value(1))
                .andExpect(jsonPath("$.summary.recipientCount").value(2))
                .andExpect(jsonPath("$.summary.readCount").value(0))
                .andExpect(jsonPath("$.summary.canRevoke").value(true))
                .andExpect(jsonPath("$.items[0].documentId").value(documentId))
                .andExpect(jsonPath("$.items[0].accessLevel").value("DOWNLOAD"))
                .andExpect(jsonPath("$.items[0].versionNumber").value(1))
                .andExpect(jsonPath("$.recipients.length()").value(2))
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + inactiveMember.userId() + "')]").isEmpty())
                .andExpect(jsonPath("$.events[?(@.eventType == 'SENT')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String distributionId = objectMapper.readTree(body).get("summary").get("id").asText();

        recipient.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());
        recipient.client().perform(get("/api/v1/documents/" + documentId + "/versions/" + versionId + "/content")
                        .param("download", "true"))
                .andExpect(status().isOk());
        teammate.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        outsider.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
        outsider.client().perform(get("/api/v1/distributions/" + distributionId))
                .andExpect(status().isNotFound());
        outsider.client().perform(get("/api/v1/distributions").param("box", "received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + distributionId + "')]").isEmpty());

        manager.client().perform(get("/api/v1/distributions").param("box", "sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + distributionId + "')]").isNotEmpty());

        recipient.client().perform(get("/api/v1/distributions").param("box", "received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + distributionId + "')].recipientStatus",
                        org.hamcrest.Matchers.contains("SENT")));

        recipient.client().perform(get("/api/v1/distributions/" + distributionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.recipientStatus").value("READ"))
                .andExpect(jsonPath("$.message").value("Mohon dipelajari sebelum rapat."));

        manager.client().perform(get("/api/v1/distributions/" + distributionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.readCount").value(1))
                .andExpect(jsonPath("$.events[?(@.eventType == 'READ')]").isNotEmpty());

        String notifications = recipient.client().perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(notifications).contains("DISTRIBUTION_RECEIVED").contains(distributionId);

        newClient().attemptLogin(inactiveMember.username(), "Inactive!Pass2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void delegationLimitsAndShareCapabilityAreEnforced() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Delegation Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Dokumen Delegasi");
        uploadVersion(manager.client(), documentId);

        Actor outsiderContributor = contributor(organizationUnit("Delegation Contributor"));
        Actor plainEmployee = employee(organizationUnit("Delegation Employee"));
        Actor inactiveRecipient = inactiveUser(organizationUnit("Delegation Inactive"));

        plainEmployee.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Tanpa izin",
                                List.of(plainEmployee.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isForbidden());

        outsiderContributor.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Luas",
                                List.of(plainEmployee.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isForbidden());

        authorizationService.grant(manager.userId(), ResourceType.DOCUMENT, UUID.fromString(documentId),
                PrincipalType.USER, manager.userId(), PermissionCodes.DOCUMENT_DOWNLOAD, AclEffect.DENY,
                GrantSource.MANUAL, null);

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Unduh",
                                List.of(plainEmployee.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "DOWNLOAD"))))))
                .andExpect(status().isForbidden());

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Lihat",
                                List.of(plainEmployee.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].accessLevel").value("VIEW"));

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Nonaktif",
                                List.of(inactiveRecipient.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Diri sendiri",
                                List.of(manager.userId().toString()), List.of(),
                                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))))))
                .andExpect(status().isBadRequest());

        manager.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(distributionPayload("Kosong",
                                List.of(), List.of(), List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeEndsOnlyDistributionGrantsAndSupportsRecipientRevoke() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Revoke Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Dokumen Revoke");
        uploadVersion(manager.client(), documentId);

        Actor first = employee(organizationUnit("Revoke First"));
        Actor second = employee(organizationUnit("Revoke Second"));

        String firstDistributionId = send(manager, distributionPayload("Distribusi pertama",
                List.of(first.userId().toString()), List.of(),
                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))));
        String secondDistributionId = send(manager, distributionPayload("Distribusi kedua",
                List.of(first.userId().toString()), List.of(),
                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))));
        ResourcePermission manualGrant = authorizationService.grant(manager.userId(), ResourceType.DOCUMENT,
                UUID.fromString(documentId), PrincipalType.USER, first.userId(), PermissionCodes.DOCUMENT_VIEW,
                AclEffect.ALLOW, GrantSource.MANUAL, null);

        manager.client().perform(post("/api/v1/distributions/" + firstDistributionId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REVOKED"))
                .andExpect(jsonPath("$.summary.canRevoke").value(false))
                .andExpect(jsonPath("$.recipients[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.events[?(@.eventType == 'REVOKED')]").isNotEmpty());

        authorizationService.revokeGrant(manager.userId(), manualGrant.getId());

        first.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        manager.client().perform(post("/api/v1/distributions/" + secondDistributionId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REVOKED"));

        first.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        manager.client().perform(post("/api/v1/distributions/" + secondDistributionId + "/revoke"))
                .andExpect(status().isConflict());

        String partialId = send(manager, distributionPayload("Distribusi sebagian",
                List.of(first.userId().toString(), second.userId().toString()), List.of(),
                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW"))));
        String detail = manager.client().perform(get("/api/v1/distributions/" + partialId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstRecipientId = null;
        for (var node : objectMapper.readTree(detail).get("recipients")) {
            if (node.get("userId").asText().equals(first.userId().toString())) {
                firstRecipientId = node.get("id").asText();
            }
        }
        assertThat(firstRecipientId).isNotNull();

        first.client().perform(post("/api/v1/distributions/" + partialId + "/revoke"))
                .andExpect(status().isNotFound());

        manager.client().perform(post("/api/v1/distributions/" + partialId + "/recipients/"
                        + firstRecipientId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PARTIALLY_REVOKED"))
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + first.userId() + "')].status",
                        org.hamcrest.Matchers.contains("REVOKED")))
                .andExpect(jsonPath("$.recipients[?(@.userId == '" + second.userId() + "')].status",
                        org.hamcrest.Matchers.contains("SENT")));

        first.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
        second.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        String remainingRecipientId = null;
        String refreshed = manager.client().perform(get("/api/v1/distributions/" + partialId))
                .andReturn().getResponse().getContentAsString();
        for (var node : objectMapper.readTree(refreshed).get("recipients")) {
            if (node.get("userId").asText().equals(second.userId().toString())) {
                remainingRecipientId = node.get("id").asText();
            }
        }
        assertThat(remainingRecipientId).isNotNull();
        manager.client().perform(post("/api/v1/distributions/" + partialId + "/recipients/"
                        + remainingRecipientId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REVOKED"));
        second.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiryDeniesAccessBeforeAnyCleanupAndUpdatesStatus() throws Exception {
        OrganizationUnit ownerUnit = organizationUnit("Expiry Owner");
        Actor manager = unitManager(ownerUnit, "UNIT_MANAGER");
        String workspaceId = createWorkspace(manager.client(), ownerUnit);
        String documentId = createDocument(manager.client(), workspaceId, "Dokumen Expiry");
        uploadVersion(manager.client(), documentId);
        Actor recipient = employee(organizationUnit("Expiry Recipient"));

        Map<String, Object> payload = distributionPayload("Distribusi kedaluwarsa",
                List.of(recipient.userId().toString()), List.of(),
                List.of(Map.of("documentId", documentId, "accessLevel", "VIEW")));
        payload.put("expiresAt", Instant.now().plusSeconds(3600).toString());
        String distributionId = send(manager, payload);

        recipient.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isOk());

        jdbcTemplate.update("update \"authorization\".resource_permissions set granted_at = now() - interval "
                + "'2 minutes', expires_at = now() - interval '1 minute' where resource_type = 'DOCUMENT' "
                + "and resource_id = ? and grant_source = 'DISTRIBUTION' and revoked_at is null",
                UUID.fromString(documentId));
        jdbcTemplate.update("update distribution.distributions set sent_at = now() - interval '2 minutes', "
                + "expires_at = now() - interval '1 minute' where id = ?", UUID.fromString(distributionId));
        jdbcTemplate.update("update distribution.distribution_recipients set sent_at = now() - interval "
                + "'2 minutes', expires_at = now() - interval '1 minute' where distribution_id = ?",
                UUID.fromString(distributionId));

        recipient.client().perform(get("/api/v1/documents/" + documentId))
                .andExpect(status().isForbidden());

        recipient.client().perform(get("/api/v1/distributions/" + distributionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("EXPIRED"))
                .andExpect(jsonPath("$.summary.recipientStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.summary.canRevoke").value(false));

        manager.client().perform(post("/api/v1/distributions/" + distributionId + "/revoke"))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------------- helpers

    private record Actor(Client client, UUID userId, String username) {
    }

    private Actor unitManager(OrganizationUnit unit, String roleCode) throws Exception {
        String username = uniqueName("distmanager");
        UUID userId = createUser(username, "DistManager!Pass2026", false, null).getId();
        authorizationService.assignRole(userId, userId, roleCode, ScopeType.ORGANIZATION_UNIT,
                unit.getId(), null, null);
        Client client = newClient();
        client.login(username, "DistManager!Pass2026");
        return new Actor(client, userId, username);
    }

    private Actor employee(OrganizationUnit unit) throws Exception {
        String username = uniqueName("distemployee");
        UUID userId = createUser(username, "DistEmployee!Pass2026", false, null).getId();
        authorizationService.assignRole(userId, userId, "EMPLOYEE", ScopeType.ORGANIZATION_UNIT,
                unit.getId(), null, null);
        joinUnit(userId, unit.getId());
        Client client = newClient();
        client.login(username, "DistEmployee!Pass2026");
        return new Actor(client, userId, username);
    }

    private Actor contributor(OrganizationUnit unit) throws Exception {
        String username = uniqueName("distcontrib");
        UUID userId = createUser(username, "DistContrib!Pass2026", false, null).getId();
        authorizationService.assignRole(userId, userId, "CONTRIBUTOR", ScopeType.ORGANIZATION_UNIT,
                unit.getId(), null, null);
        joinUnit(userId, unit.getId());
        Client client = newClient();
        client.login(username, "DistContrib!Pass2026");
        return new Actor(client, userId, username);
    }

    private Actor inactiveUser(OrganizationUnit unit) throws Exception {
        String username = uniqueName("distinactive");
        User user = createUser(username, "Inactive!Pass2026", false, "EMPLOYEE");
        joinUnit(user.getId(), unit.getId());
        user.changeStatus(AccountStatus.INACTIVE, null, Instant.now());
        userRepository.save(user);
        return new Actor(newClient(), user.getId(), username);
    }

    private void joinUnit(UUID userId, UUID unitId) {
        organizationAssignmentRepository.save(new UserOrganizationAssignment(UUID.randomUUID(), userId, unitId,
                AssignmentType.PRIMARY, Instant.now(), null, userId));
    }

    private OrganizationUnit organizationUnit(String name) {
        return organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("DU").substring(0, 10), name, null, null, Instant.now()));
    }

    private String createWorkspace(Client client, OrganizationUnit unit) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceCode", uniqueName("DWS").toUpperCase());
        payload.put("workspaceName", "Workspace " + uniqueName("Dist"));
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
        payload.put("description", "Dokumen uji distribusi");
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

    private String uploadVersion(Client client, String documentId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "distribusi.pdf", "application/pdf",
                PDF_CONTENT.getBytes(StandardCharsets.UTF_8));
        String body = client.perform(multipart("/api/v1/documents/" + documentId + "/versions")
                        .file(file)
                        .param("changeSummary", "Versi distribusi")
                        .param("versionLabel", "v1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Map<String, Object> distributionPayload(String title, List<String> userIds, List<String> unitIds,
                                                    List<Map<String, Object>> items) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("message", "Pesan distribusi uji.");
        payload.put("recipientUserIds", userIds);
        payload.put("recipientOrganizationUnitIds", unitIds);
        payload.put("items", items);
        return payload;
    }

    private String send(Actor sender, Map<String, Object> payload) throws Exception {
        String body = sender.client().perform(post("/api/v1/distributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("summary").get("id").asText();
    }
}
