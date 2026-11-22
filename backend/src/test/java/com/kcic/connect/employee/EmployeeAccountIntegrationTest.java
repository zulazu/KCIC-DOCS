package com.kcic.connect.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class EmployeeAccountIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;
    private User adminUser;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("acctadmin");
        adminUser = createUser(username, "AcctAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "AcctAdmin!Pass2026");
    }

    @Test
    void createAccountLinksEmployeeOneToOneAndForcesPasswordChange() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        String username = uniqueName("newacct");

        JsonNode account = createAccount(employeeId, username);

        assertThat(account.get("username").asText()).isEqualTo(username);
        assertThat(account.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(account.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(account.get("userId").isNull()).isFalse();

        User user = userRepository.findById(UUID.fromString(account.get("userId").asText())).orElseThrow();
        assertThat(user.getEmployeeId()).isEqualTo(UUID.fromString(employeeId));
        assertThat(user.getEmail()).isEqualTo(employeeEmail(employeeId));

        admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedAccount.userId").value(account.get("userId").asText()))
                .andExpect(jsonPath("$.linkedAccount.username").value(username));
    }

    @Test
    void employeeDetailShowsNoAccountBeforeLinking() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());

        admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedAccount").value(nullValue()));
    }

    @Test
    void duplicateAccountForSameEmployeeIsRejected() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        createAccount(employeeId, uniqueName("first"));

        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(uniqueName("second"), "TempPass!2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Employee sudah memiliki akun terhubung."));
    }

    @Test
    void usernameAlreadyUsedIsRejected() throws Exception {
        String takenUsername = uniqueName("taken");
        createUser(takenUsername, "Taken!Pass2026", false, null);
        String employeeId = createEmployee(createHierarchy().divisionId());

        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(takenUsername, "TempPass!2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username sudah digunakan."));
    }

    @Test
    void weakTemporaryPasswordIsRejected() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());

        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(uniqueName("short"), "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("temporaryPassword"));
    }

    @Test
    void linkExistingUnlinkedAccountAndRejectDoubleLink() throws Exception {
        String company = createUnit("COMPANY", null, "Perusahaan");
        String firstEmployee = createEmployee(createHierarchy(company).divisionId());
        String secondEmployee = createEmployee(createHierarchy(company).divisionId());
        User firstAccount = createUser(uniqueName("linkme"), "LinkMe!Pass2026", false, null);
        User secondAccount = createUser(uniqueName("linktwo"), "LinkTwo!Pass2026", false, null);

        admin.perform(post("/api/v1/admin/users/" + firstAccount.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", firstEmployee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(firstAccount.getUsername()));

        admin.perform(post("/api/v1/admin/users/" + firstAccount.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", secondEmployee))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Akun sudah terhubung ke employee lain."));

        admin.perform(post("/api/v1/admin/users/" + secondAccount.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", firstEmployee))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Employee sudah memiliki akun terhubung."));
    }

    @Test
    void unlinkedAccountListExcludesLinkedUsers() throws Exception {
        String marker = uniqueName("unlinked");
        User account = createUser(marker, "Unlinked!Pass2026", false, null);
        String employeeId = createEmployee(createHierarchy().divisionId());

        admin.perform(get("/api/v1/admin/users/unlinked").param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(account.getId().toString()));

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk());

        admin.perform(get("/api/v1/admin/users/unlinked").param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void employeeListReportsAndFiltersAccountCoverage() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        String nip = objectMapper.readTree(admin.perform(get("/api/v1/admin/employees/" + employeeId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("employee").get("nip").asText();
        User account = createUser(uniqueName("coverage"), "Coverage!Pass2026", false, null);

        admin.perform(get("/api/v1/admin/employees")
                        .param("linked", "false").param("keyword", nip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(employeeId))
                .andExpect(jsonPath("$.items[0].hasAccount").value(false));

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk());

        admin.perform(get("/api/v1/admin/employees")
                        .param("linked", "true").param("keyword", nip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].hasAccount").value(true));

        admin.perform(get("/api/v1/admin/employees")
                        .param("linked", "false").param("keyword", nip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void accountCannotBeCreatedForNonActiveEmployee() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        setEmployeeStatus(employeeId, "INACTIVE");

        admin.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(uniqueName("inactive"), "TempPass!2026"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("employeeId"));

        admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedAccount").value(nullValue()));
    }

    @Test
    void activeAccountCannotLinkToNonActiveEmployeeButInactiveAccountCan() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        setEmployeeStatus(employeeId, "INACTIVE");
        User account = createUser(uniqueName("suspendlink"), "Suspend!Pass2026", false, null);

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("employeeId"));

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        admin.perform(post("/api/v1/admin/users/" + account.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(account.getUsername()));
    }

    @Test
    void accountCannotBeReactivatedWhileEmployeeIsNonActive() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        JsonNode account = createAccount(employeeId, uniqueName("reactivate"));
        String userId = account.get("userId").asText();
        setEmployeeStatus(employeeId, "INACTIVE");

        admin.perform(post("/api/v1/admin/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        admin.perform(post("/api/v1/admin/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        setEmployeeStatus(employeeId, "ACTIVE");
        admin.perform(post("/api/v1/admin/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());
    }

    @Test
    void accountLinkingRequiresAdminCapabilities() throws Exception {
        String employeeId = createEmployee(createHierarchy().divisionId());
        String username = uniqueName("plainemp");
        createUser(username, "PlainEmp!Pass2026", false, "EMPLOYEE");
        Client plain = newClient();
        plain.login(username, "PlainEmp!Pass2026");

        plain.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(uniqueName("nope"), "TempPass!2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
        plain.perform(get("/api/v1/admin/users/unlinked"))
                .andExpect(status().isForbidden());
        plain.perform(post("/api/v1/admin/users/" + adminUser.getId() + "/employee-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId))))
                .andExpect(status().isForbidden());
    }

    private JsonNode createAccount(String employeeId, String username) throws Exception {
        String body = admin.perform(post("/api/v1/admin/employees/" + employeeId + "/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(accountPayload(username, "TempPass!2026"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Map<String, Object> accountPayload(String username, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("temporaryPassword", password);
        return payload;
    }

    private String employeeEmail(String employeeId) throws Exception {
        String body = admin.perform(get("/api/v1/admin/employees/" + employeeId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("employee").get("email").asText();
    }

    private void setEmployeeStatus(String employeeId, String status) throws Exception {
        JsonNode employee = objectMapper.readTree(admin.perform(get("/api/v1/admin/employees/" + employeeId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("employee");
        Map<String, Object> payload = new HashMap<>();
        payload.put("nip", employee.get("nip").asText());
        payload.put("fullName", employee.get("fullName").asText());
        payload.put("email", employee.get("email").asText());
        payload.put("employmentStatus", status);
        admin.perform(put("/api/v1/admin/employees/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk());
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
        return createHierarchy(createUnit("COMPANY", null, "Perusahaan"));
    }

    private Hierarchy createHierarchy(String company) throws Exception {
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
