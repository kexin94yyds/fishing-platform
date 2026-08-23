package com.fishing.platform;

import com.fishing.platform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("mysql")
@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fishing_mysql_bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.hikari.connection-init-sql=SET TIME ZONE '+08:00'",
                "fishing.bootstrap-admin.username=bootstrap_admin",
                "fishing.bootstrap-admin.display-name=安全管理员",
                "fishing.bootstrap-admin.password=SecureBootstrap1234"
        })
class MysqlBootstrapIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshMysqlStartupUsesSchemaAndMysqlMigrationsWithoutDemoFixtures() {
        var admin = userMapper.findByUsername("bootstrap_admin");
        assertTrue(admin.enabled());
        assertEquals("ADMIN", admin.role());
        assertTrue(passwordEncoder.matches("SecureBootstrap1234", admin.passwordHash()));
        assertFalse(passwordEncoder.matches("admin123", admin.passwordHash()));
        assertEquals(1, userMapper.countEnabledAdmins());
        assertNull(userMapper.findByUsername("admin"), "MySQL 不应创建固定演示管理员");
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class));
        assertIterableEquals(List.of("1", "3", "5", "6", "7", "8", "9", "10", "11", "12", "13"), appliedMigrationVersions());
        assertNoDemoBusinessData();
    }

    private List<String> appliedMigrationVersions() {
        return jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
    }

    private void assertNoDemoBusinessData() {
        for (String tableName : List.of(
                "fishing_zone", "fishing_spot", "member", "fishing_slot_inventory", "booking",
                "catch_record", "product", "sales_order", "sales_order_item", "payment",
                "traffic_daily", "visitor_flow_record")) {
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class),
                    tableName + " 不应包含演示业务数据");
        }
    }
}
