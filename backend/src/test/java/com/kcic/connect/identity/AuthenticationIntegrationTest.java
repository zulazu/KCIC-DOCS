package com.kcic.connect.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.identity.domain.User;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthenticationIntegrationTest extends IntegrationTestSupport {

    @Test
    void loginLocksAccountAfterConfiguredFailures() throws Exception {
        String username = uniqueName("lock");
        createUser(username, "Valid!Pass2026", false, null);
        Client client = newClient();

        for (int attempt = 1; attempt <= 4; attempt++) {
            client.attemptLogin(username, "Wrong!Pass2026")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        client.attemptLogin(username, "Wrong!Pass2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        client.attemptLogin(username, "Valid!Pass2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void mustChangePasswordBlocksOtherEndpointsUntilChanged() throws Exception {
        String username = uniqueName("mustchange");
        createUser(username, "Temp!Pass2026", true, null);
        Client client = newClient();
        client.login(username, "Temp!Pass2026");

        client.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        client.changePassword("Temp!Pass2026", "Fresh!Pass2026");

        client.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        client.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_FORBIDDEN"));
    }

    @Test
    void adminCanManageUsersAndDeactivationInvalidatesTargetSession() throws Exception {
        String adminName = uniqueName("admin");
        createUser(adminName, "Admin!Pass2026", false, "SYSTEM_ADMIN");
        Client admin = newClient();
        admin.login(adminName, "Admin!Pass2026");

        String targetName = uniqueName("target");
        String createResponse = admin.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", targetName,
                                "displayName", "Target User",
                                "temporaryPassword", "Target!Temp2026"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        String targetId = created.get("user").get("id").asText();

        Client target = newClient();
        target.login(targetName, "Target!Temp2026");
        target.changePassword("Target!Temp2026", "Target!Pass2026");
        target.perform(get("/api/v1/auth/me")).andExpect(status().isOk());

        admin.perform(post("/api/v1/admin/users/" + targetId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        target.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        admin.perform(post("/api/v1/admin/users/" + targetId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());

        admin.perform(post("/api/v1/admin/users/" + targetId + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "temporaryPassword", "Reset!Temp2026"))))
                .andExpect(status().isNoContent());

        Client relogin = newClient();
        relogin.attemptLogin(targetName, "Reset!Temp2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void adminUserListSupportsOptionalStatusAndKeywordFilters() throws Exception {
        String adminName = uniqueName("listadmin");
        createUser(adminName, "Admin!Pass2026", false, "SYSTEM_ADMIN");
        String targetName = uniqueName("listtarget");
        createUser(targetName, "Target!Pass2026", false, null);

        Client admin = newClient();
        admin.login(adminName, "Admin!Pass2026");

        admin.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").isNumber());

        admin.perform(get("/api/v1/admin/users")
                        .param("status", "ACTIVE")
                        .param("keyword", targetName)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value(targetName));

        admin.perform(get("/api/v1/admin/users")
                        .param("status", "LOCKED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void selfPasswordChangeInvalidatesOtherSessionsButKeepsCurrent() throws Exception {
        String username = uniqueName("selfchange");
        createUser(username, "Old!Pass2026", false, null);
        Client first = newClient();
        first.login(username, "Old!Pass2026");
        Client second = newClient();
        second.login(username, "Old!Pass2026");

        first.changePassword("Old!Pass2026", "New!Pass2026");

        first.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
        second.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        Client relogin = newClient();
        relogin.attemptLogin(username, "Old!Pass2026")
                .andExpect(status().isUnauthorized());
        relogin.login(username, "New!Pass2026");
    }

    @Test
    void requestWithoutCsrfTokenIsRejected() throws Exception {
        String username = uniqueName("csrf");
        createUser(username, "Csrf!Pass2026", false, null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "Csrf!Pass2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
    }
}
