package com.fishing.platform;

import com.fishing.platform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("demo")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_demo_migration;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
class DemoMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Test
    void demoStartupUsesSchemaAndDemoMigrations() {
        assertEquals(List.of("1", "2", "3", "4", "5", "7", "8", "9", "10"), jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class));
        assertNotNull(userMapper.findByUsername("admin"));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fishing_zone", Integer.class));
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class));
    }
}
