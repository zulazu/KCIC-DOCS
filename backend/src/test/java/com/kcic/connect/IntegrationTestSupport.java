package com.kcic.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    // Opt-in local PostgreSQL for Docker-less environments: set KCIC_TEST_DB_URL
    // (plus KCIC_TEST_DB_USER/KCIC_TEST_DB_PASSWORD) to a dedicated test database.
    static final String LOCAL_DB_URL = System.getenv("KCIC_TEST_DB_URL");
    static final PostgreSQLContainer<?> POSTGRES =
            LOCAL_DB_URL == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;
    static final String STORAGE_ROOT;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
        try {
            STORAGE_ROOT = java.nio.file.Files.createTempDirectory("kcic-test-storage").toString();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to create test storage directory", ex);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (LOCAL_DB_URL != null) {
            registry.add("spring.datasource.url", () -> LOCAL_DB_URL);
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("KCIC_TEST_DB_USER", "postgres"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("KCIC_TEST_DB_PASSWORD", ""));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        registry.add("kcic.storage.provider", () -> "filesystem");
        registry.add("kcic.storage.local-root", () -> STORAGE_ROOT);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected AuthorizationService authorizationService;

    protected User createUser(String username, String rawPassword, boolean mustChange, String roleCode) {
        User user = new User(UUID.randomUUID(), username, null, "Test " + username,
                passwordEncoder.encode(rawPassword), mustChange, Instant.now(), null);
        userRepository.save(user);
        if (roleCode != null) {
            authorizationService.assignRoleInternal(user.getId(), user.getId(), roleCode);
        }
        return user;
    }

    protected String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Client newClient() {
        return new Client();
    }

    public final class Client {

        private String csrfToken;
        private Cookie csrfCookie;
        private Cookie sessionCookie;

        private Client() {
            try {
                MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
                capture(result.getResponse());
                csrfToken = objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("token").asText();
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize CSRF token", ex);
            }
        }

        public void login(String username, String password) throws Exception {
            attemptLogin(username, password).andExpect(status().isOk());
        }

        public ResultActions attemptLogin(String username, String password) throws Exception {
            return perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "username", username, "password", password))));
        }

        public ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
            if (csrfCookie != null) {
                builder.cookie(csrfCookie);
            }
            if (sessionCookie != null) {
                builder.cookie(sessionCookie);
            }
            if (csrfToken != null) {
                builder.header("X-XSRF-TOKEN", csrfToken);
            }
            return mockMvc.perform(builder).andDo(result -> capture(result.getResponse()));
        }

        public void changePassword(String currentPassword, String newPassword) throws Exception {
            perform(post("/api/v1/auth/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "currentPassword", currentPassword,
                            "newPassword", newPassword,
                            "confirmPassword", newPassword))))
                    .andExpect(status().isNoContent());
        }

        public String csrfToken() {
            return csrfToken;
        }

        private void capture(org.springframework.mock.web.MockHttpServletResponse response) {
            Cookie[] cookies = response.getCookies();
            if (cookies == null) {
                return;
            }
            for (Cookie cookie : cookies) {
                if ("XSRF-TOKEN".equals(cookie.getName())) {
                    csrfCookie = cookie;
                } else if ("SESSION".equals(cookie.getName()) || "KCIC_SESSION".equals(cookie.getName())) {
                    sessionCookie = cookie.getMaxAge() == 0 ? null : cookie;
                }
            }
        }
    }
}
