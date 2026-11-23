package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.application.WorkspaceLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only workspace facts needed by authorization. Kept as JDBC so the
 * authorization module does not depend on repository entities.
 */
@Component
public class JdbcWorkspaceLookup implements WorkspaceLookup {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean workspaceExists(UUID workspaceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists (select 1 from repository.workspaces where id = ?)",
                Boolean.class, workspaceId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<UUID> workspaceOwnerUnit(UUID workspaceId) {
        return jdbcTemplate.query("select owner_organization_unit_id from repository.workspaces where id = ?",
                        (rs, rowNum) -> rs.getObject("owner_organization_unit_id", UUID.class), workspaceId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> workspaceName(UUID workspaceId) {
        return jdbcTemplate.query("select workspace_name from repository.workspaces where id = ?",
                        (rs, rowNum) -> rs.getString("workspace_name"), workspaceId)
                .stream().findFirst();
    }
}
