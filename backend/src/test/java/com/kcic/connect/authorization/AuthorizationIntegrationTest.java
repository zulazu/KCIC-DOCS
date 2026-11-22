package com.kcic.connect.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AuthorizationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Test
    void employeeCannotAccessAdministrationApis() throws Exception {
        String username = uniqueName("employee");
        createUser(username, "Employee!Pass2026", false, "EMPLOYEE");
        Client client = newClient();
        client.login(username, "Employee!Pass2026");

        client.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
        client.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "unitCode", uniqueName("UNIT").substring(0, 12),
                                "unitName", "Unauthorized Unit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
    }

    @Test
    void unitScopedRoleIsNotAGlobalCapability() {
        String username = uniqueName("scoped");
        User user = createUser(username, "Scoped!Pass2026", false, null);
        OrganizationUnit unit = organizationUnitRepository.save(new OrganizationUnit(UUID.randomUUID(), null,
                uniqueName("U").substring(0, 10), "Scoped Unit", null, null, Instant.now()));
        UUID otherUnit = UUID.randomUUID();
        authorizationService.assignRole(user.getId(), user.getId(), "EMPLOYEE", ScopeType.ORGANIZATION_UNIT,
                unit.getId(), null, null);

        assertThat(authorizationService.hasCapability(user.getId(), PermissionCodes.DOCUMENT_VIEW)).isTrue();
        assertThat(authorizationService.hasGlobalCapability(user.getId(), PermissionCodes.DOCUMENT_VIEW)).isFalse();
        assertThat(authorizationService.allowsResourceCapability(user.getId(), PermissionCodes.DOCUMENT_VIEW,
                UUID.randomUUID(), Set.of(unit.getId()), false)).isTrue();
        assertThat(authorizationService.allowsResourceCapability(user.getId(), PermissionCodes.DOCUMENT_VIEW,
                UUID.randomUUID(), Set.of(otherUnit), false)).isFalse();
    }

    @Test
    void explicitDenyOverridesRoleAllowAndRevokeRestoresAccess() {
        String username = uniqueName("acl");
        User user = createUser(username, "Acl!Pass2026", false, null);
        authorizationService.assignRole(user.getId(), user.getId(), "EMPLOYEE", ScopeType.GLOBAL, null, null, null);
        UserRoleAssignment assignment = authorizationService.userAssignments(user.getId()).get(0);
        Role role = authorizationService.getRole(assignment.getRoleId());
        UUID resourceId = UUID.randomUUID();

        ResourcePermission allow = authorizationService.grant(user.getId(), ResourceType.DOCUMENT, resourceId,
                PrincipalType.USER, user.getId(), PermissionCodes.DOCUMENT_DOWNLOAD, AclEffect.ALLOW,
                GrantSource.MANUAL, null);
        assertThat(authorizationService.hasEffectiveAcl(
                authorizationService.activeResourcePermissions(ResourceType.DOCUMENT, resourceId),
                user.getId(), Set.of(role.getId()), Set.of(), PermissionCodes.DOCUMENT_DOWNLOAD, Instant.now()))
                .isTrue();

        ResourcePermission deny = authorizationService.grant(user.getId(), ResourceType.DOCUMENT, resourceId,
                PrincipalType.ROLE, role.getId(), PermissionCodes.DOCUMENT_DOWNLOAD, AclEffect.DENY,
                GrantSource.MANUAL, null);
        assertThat(authorizationService.hasEffectiveAcl(
                authorizationService.activeResourcePermissions(ResourceType.DOCUMENT, resourceId),
                user.getId(), Set.of(role.getId()), Set.of(), PermissionCodes.DOCUMENT_DOWNLOAD, Instant.now()))
                .isFalse();

        authorizationService.revokeGrant(user.getId(), deny.getId());
        assertThat(authorizationService.hasEffectiveAcl(
                authorizationService.activeResourcePermissions(ResourceType.DOCUMENT, resourceId),
                user.getId(), Set.of(role.getId()), Set.of(), PermissionCodes.DOCUMENT_DOWNLOAD, Instant.now()))
                .isTrue();
        assertThat(allow.getId()).isNotNull();
    }

    @Test
    void organizationHierarchyRejectsCyclesAndDuplicateCodes() throws Exception {
        String adminName = uniqueName("orgadmin");
        createUser(adminName, "OrgAdmin!Pass2026", false, "SYSTEM_ADMIN");
        Client admin = newClient();
        admin.login(adminName, "OrgAdmin!Pass2026");

        String codeA = "A" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String createA = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "unitCode", codeA, "unitName", "Unit A"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idA = objectMapper.readTree(createA).get("id").asText();

        String codeB = "B" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String createB = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "unitCode", codeB, "unitName", "Unit B", "parentId", idA))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idB = objectMapper.readTree(createB).get("id").asText();

        admin.perform(put("/api/v1/admin/organization-units/" + idA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "unitName", "Unit A", "status", "ACTIVE", "parentId", idB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "unitCode", codeA.toLowerCase(), "unitName", "Duplicate A"))))
                .andExpect(status().isConflict());
    }
}
