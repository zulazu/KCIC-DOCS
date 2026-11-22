package com.kcic.connect.distribution.application;

import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Distribution codes use a database sequence so concurrent sends cannot collide. */
@Component
public class DistributionCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public DistributionCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextCode() {
        Long sequence = jdbcTemplate.queryForObject("select nextval('distribution.distribution_code_seq')",
                Long.class);
        return "DIST-" + Year.now() + "-" + String.format("%05d", sequence == null ? 0 : sequence);
    }
}
