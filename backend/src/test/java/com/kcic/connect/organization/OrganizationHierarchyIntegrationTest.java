package com.kcic.connect.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcic.connect.IntegrationTestSupport;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OrganizationHierarchyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    private Client admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        String username = uniqueName("orgadmin");
        User adminUser = createUser(username, "OrgAdmin!Pass2026", false, "SYSTEM_ADMIN");
        deactivateActiveCompanies(adminUser.getId());
        admin = newClient();
        admin.login(username, "OrgAdmin!Pass2026");
    }

    private void deactivateActiveCompanies(java.util.UUID actor) {
        Instant now = Instant.now();
        organizationUnitRepository.findAllByUnitTypeAndUnitStatus(UnitType.COMPANY, UnitStatus.ACTIVE)
                .forEach(unit -> {
                    unit.update(unit.getParentId(), unit.getUnitName(), unit.getDescription(),
                            UnitType.COMPANY, UnitStatus.INACTIVE, actor, now);
                    organizationUnitRepository.saveAndFlush(unit);
                });
    }

    @Test
    void typedHierarchyAcceptsCompanyDirectorateDepartmentDivision() throws Exception {
        String company = createUnit("COMPANY", null, "Perusahaan");
        String directorate = createUnit("DIRECTORATE", company, "Direktorat");
        String department = createUnit("DEPARTMENT", directorate, "Departemen");
        String division = createUnit("DIVISION", department, "Biro/Division");

        admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unitPayload("COMPANY", null, "Perusahaan Kedua"))))
                .andExpect(status().isConflict());

        admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unitPayload("DIRECTORATE", null, "Direktorat Akar"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unitPayload("DIVISION", directorate, "Division Salah Induk"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentId"));

        assertThat(company).isNotBlank();
        assertThat(directorate).isNotBlank();
        assertThat(department).isNotBlank();
        assertThat(division).isNotBlank();
    }

    @Test
    void typedUnitRequiresTypedParentAndLegacyUnitsRemainAllowed() throws Exception {
        String legacyParent = createLegacyUnit(null);
        admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unitPayload("DEPARTMENT", legacyParent, "Departemen di Unit Lama"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Unit induk belum memiliki tipe organisasi."));

        createLegacyUnit(legacyParent);
    }

    @Test
    void typeChangeIsRejectedWhenChildrenDoNotMatchHierarchy() throws Exception {
        String company = createUnit("COMPANY", null, "Perusahaan");
        String directorate = createUnit("DIRECTORATE", company, "Direktorat");
        String department = createUnit("DEPARTMENT", directorate, "Departemen");
        createUnit("DIVISION", department, "Biro/Division");

        Map<String, Object> update = new HashMap<>();
        update.put("unitName", "Departemen");
        update.put("unitType", "DIRECTORATE");
        update.put("parentId", company);
        update.put("status", "ACTIVE");
        admin.perform(put("/api/v1/admin/organization-units/" + department)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isConflict());
    }

    @Test
    void legacyUnitWithoutTypeCanBeMappedToTypedCompany() throws Exception {
        String legacy = createLegacyUnit(null);
        Map<String, Object> update = new HashMap<>();
        update.put("unitName", "Unit Lama Menjadi Perusahaan");
        update.put("unitType", "COMPANY");
        update.put("status", "ACTIVE");
        admin.perform(put("/api/v1/admin/organization-units/" + legacy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitType").value("COMPANY"));
    }

    private String createUnit(String type, String parentId, String name) throws Exception {
        String body = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unitPayload(type, parentId, name))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitType").value(type))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createLegacyUnit(String parentId) throws Exception {
        Map<String, Object> payload = unitPayload(null, parentId, "Unit Lama");
        String body = admin.perform(post("/api/v1/admin/organization-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitType").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Map<String, Object> unitPayload(String type, String parentId, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("unitCode", uniqueName("UNIT"));
        payload.put("unitName", name);
        if (type != null) {
            payload.put("unitType", type);
        }
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        return payload;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
