package com.fishing.platform;

import com.fishing.platform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("mysql")
@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fishing_mysql_bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.hikari.connection-init-sql=SET TIME ZONE '+08:00'",
                "fishing.bootstrap-admin.username=admin",
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
    void mysqlMigrationRetiresPublicPasswordAndBootstrapsOneSecureAdmin() {
        var admin = userMapper.findByUsername("admin");
        assertTrue(admin.enabled());
        assertEquals("ADMIN", admin.role());
        assertTrue(passwordEncoder.matches("SecureBootstrap1234", admin.passwordHash()));
        assertFalse(passwordEncoder.matches("admin123", admin.passwordHash()));
        assertEquals(1, userMapper.countEnabledAdmins());
        assertEquals("6", jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class));
    }
}
