package com.kcic.connect.audit;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Phase 9 gate for AUDIT-01: audit search is restricted to audit.view holders
 * and supports actor, action, outcome, and date-range filters with paging.
 */
class AuditIntegrationTest extends IntegrationTestSupport {

    @Test
    void auditSearchIsPermissionGatedAndFilterable() throws Exception {
        createUser("audit_viewer", "Auditor!Pass2026", false, "AUDITOR");
        createUser("audit_employee", "Employee!Pass2026", false, "EMPLOYEE");
        Client auditor = newClient();
        auditor.login("audit_viewer", "Auditor!Pass2026");
        Client employee = newClient();
        employee.login("audit_employee", "Employee!Pass2026");

        employee.perform(get("/api/v1/audit-events"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_FORBIDDEN"));

        auditor.perform(get("/api/v1/audit-events")
                        .param("actor", "audit_viewer")
                        .param("action", "login_success")
                        .param("outcome", "success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].actionCode", everyItem(is("LOGIN_SUCCESS"))))
                .andExpect(jsonPath("$.items[*].actorUsername", everyItem(is("audit_viewer"))))
                .andExpect(jsonPath("$.items[*].outcome", everyItem(is("SUCCESS"))))
                .andExpect(jsonPath("$.items[0].safeMetadata").isMap());

        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        auditor.perform(get("/api/v1/audit-events")
                        .param("from", tomorrow)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        auditor.perform(get("/api/v1/audit-events")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThan(1)));

        auditor.perform(get("/api/v1/audit-events")
                        .param("from", tomorrow)
                        .param("to", LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
    }
}
