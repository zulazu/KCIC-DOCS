package com.kcic.connect.workflow.application;

import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Request codes use a database sequence so concurrent submissions cannot collide
 * even before the unique constraint is checked.
 */
@Component
public class RequestCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public RequestCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextCode() {
        Long sequence = jdbcTemplate.queryForObject("select nextval('workflow.request_code_seq')", Long.class);
        return "REQ-" + Year.now() + "-" + String.format("%05d", sequence == null ? 0 : sequence);
    }
}
