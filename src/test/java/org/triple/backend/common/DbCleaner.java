package org.triple.backend.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ActiveProfiles("test")
public class DbCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<String> tableNames;

    @PostConstruct
    void init() {
        tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class
        );
    }

    @Transactional
    public void clean() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        for (String tableName : tableNames) {
            jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
