package com.kcic.connect.employee;

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
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EmploymentLifecycleSecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private User adminUser;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("lifeadmin");
        adminUser = createUser(username, "LifeAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "LifeAdmin!Pass2026");
    }

    @Test
    void inactiveEmploymentCannotLogin() throws Exception {
        String employeeId = createEmployee();
        String username = uniqueName("terminated");
        User account = createUser(username, "Terminated!Pass2026", false, null);
        link(account.getId(), employeeId);
        updateEmploymentStatus(employeeId, "INACTIVE");

        newClient().attemptLogin(username, "Terminated!Pass2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.message").value(
                        "Akun tidak dapat digunakan karena status kepegawaian tidak aktif. Hubungi administrator."));
    }

    @Test
    void employmentStatusChangeEndsActiveSessions() throws Exception {
        String employeeId = createEmployee();
        String username = uniqueName("session");
        User account = createUser(username, "Session!Pass2026", false, "EMPLOYEE");
        link(account.getId(), employeeId);

        Client employee = newClient();
        employee.login(username, "Session!Pass2026");
        employee.perform(get("/api/v1/auth/me")).andExpect(status().isOk());

        updateEmploymentStatus(employeeId, "TERMINATED");

        employee.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        newClient().attemptLogin(username, "Session!Pass2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void inactiveEmploymentCannotReceiveNewRoleOrGrant() throws Exception {
        String employeeId = createEmployee();
        User account = createUser(uniqueName("grantee"), "Grantee!Pass2026", false, null);
        link(account.getId(), employeeId);
        updateEmploymentStatus(employeeId, "INACTIVE");

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roleCode", "EMPLOYEE", "scopeType", "GLOBAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("userId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(
                        "User tidak dapat menerima akses baru karena status kepegawaian INACTIVE."));

        assertThatThrownBy(() -> authorizationService.grant(adminUser.getId(), ResourceType.WORKSPACE,
                UUID.randomUUID(), PrincipalType.USER, account.getId(), false, "document.view",
                AclEffect.ALLOW, GrantSource.MANUAL, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).fieldErrors())
                        .anyMatch(error -> error.message().contains("status kepegawaian INACTIVE")));
    }

    @Test
    void employeeEndpointsDenyAccountsWithoutAdminCapability() throws Exception {
        String employeeId = createEmployee();
        String username = uniqueName("plainuser");
        createUser(username, "PlainUser!Pass2026", false, "EMPLOYEE");
        Client plain = newClient();
        plain.login(username, "PlainUser!Pass2026");

        plain.perform(get("/api/v1/admin/employees/" + employeeId)).andExpect(status().isForbidden());
        plain.perform(get("/api/v1/admin/employees/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    private void updateEmploymentStatus(String employeeId, String status) throws Exception {
        String body = admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode employee = objectMapper.readTree(body).get("employee");
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", employee.get("nip").asText());
        payload.put("fullName", employee.get("fullName").asText());
        payload.put("email", employee.get("email").asText());
        payload.put("employmentStatus", status);
        admin.perform(put("/api/v1/admin/employees/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.employmentStatus").value(status));
    }

    private void link(UUID userId, String employeeId) throws Exception {
        admin.perform(post("/api/v1/admin/users/" + userId + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk());
    }

    private String createEmployee() throws Exception {
        String company = createUnit("COMPANY", null, "Perusahaan");
        String directorate = createUnit("DIRECTORATE", company, "Direktorat");
        String department = createUnit("DEPARTMENT", directorate, "Departemen");
        String division = createUnit("DIVISION", department, "Biro/Division");
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", uniqueName("NIP"));
        payload.put("fullName", "Employee " + uniqueName("X"));
        payload.put("email", uniqueName("mail") + "@kcic.test");
        payload.put("employmentStatus", "ACTIVE");
        payload.put("divisionUnitId", division);
        String body = admin.perform(post("/api/v1/admin/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("employee").get("id").asText();
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
}
