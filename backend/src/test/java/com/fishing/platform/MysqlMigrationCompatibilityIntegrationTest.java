package com.fishing.platform;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.config.MysqlMigrationHistoryGuard;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.mapper.UserMapper;
import com.fishing.platform.service.BookingService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("mysql")
@SpringBootTest(
        properties = {
                "spring.datasource.url=" + MysqlMigrationCompatibilityIntegrationTest.LEGACY_DATABASE_URL,
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.hikari.connection-init-sql=SET TIME ZONE '+08:00'",
                "fishing.bootstrap-admin.username=admin",
                "fishing.bootstrap-admin.display-name=安全管理员",
                "fishing.bootstrap-admin.password=SecureBootstrap1234"
        })
@ContextConfiguration(initializers = MysqlMigrationCompatibilityIntegrationTest.LegacyDemoHistoryInitializer.class)
class MysqlMigrationCompatibilityIntegrationTest {
    static final String LEGACY_DATABASE_URL = "jdbc:h2:mem:fishing_mysql_legacy_upgrade;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FlywayMigrationStrategy flywayMigrationStrategy;

    @Autowired
    private BookingService bookingService;

    @Test
    void mysqlStartupAcceptsOnlyLegacyDemoHistoryAndRunsTheMysqlForwardMigration() {
        assertTrue(flywayMigrationStrategy instanceof MysqlMigrationHistoryGuard,
                "mysql profile 必须自动装配 MysqlMigrationHistoryGuard 为 FlywayMigrationStrategy");
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"), jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class));
        assertEquals(List.of("2", "4"), List.of(flyway.info().all()).stream()
                .filter(migration -> migration.getState() == MigrationState.MISSING_SUCCESS)
                .map(migration -> migration.getVersion().toString())
                .toList());

        var admin = userMapper.findByUsername("admin");
        assertTrue(admin.enabled());
        assertTrue(passwordEncoder.matches("SecureBootstrap1234", admin.passwordHash()));
    }

    @Test
    void v13KeepsLegacySamePhoneBookingsAndStillRejectsDuplicatesAfterTheKeeperIsCancelled() {
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking WHERE booking_no LIKE 'LEGACY-PHONE-%'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM booking
                WHERE booking_no LIKE 'LEGACY-PHONE-%'
                  AND active_contact_key = 'P:13900009999'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM booking
                WHERE booking_no LIKE 'LEGACY-PHONE-%'
                  AND active_contact_key IS NULL
                """, Integer.class));

        Long keeperId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM booking
                WHERE booking_no LIKE 'LEGACY-PHONE-%'
                  AND active_contact_key = 'P:13900009999'
                """, Long.class);
        jdbcTemplate.update("""
                UPDATE booking
                SET status = 'CANCELLED', active_customer_key = NULL, active_contact_key = NULL
                WHERE id = ?
                """, keeperId);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("legacy_operator", "not-used", List.of()));
        try {
            BusinessException duplicate = assertThrows(BusinessException.class, () -> bookingService.create(
                    new BookingRequest(null, "新散客", "13900009999", 1L,
                            java.time.LocalDate.of(2026, 8, 30), "MORNING", 1, null, null)));
            assertEquals("该联系电话在所选日期和时段已有有效预订", duplicate.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking WHERE booking_no LIKE 'LEGACY-PHONE-%'",
                Integer.class));
    }

    @Test
    void mysqlContextFailsBeforePendingMigrationForUnexpectedMissingHistory() throws SQLException {
        String databaseUrl = "jdbc:h2:mem:fishing_mysql_strategy_startup_failure;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
        createLegacyDemoHistoryWithUnexpectedMigration(databaseUrl);

        Exception startupFailure = assertThrows(Exception.class,
                () -> startMysqlContext(databaseUrl, "classpath:db/schema,classpath:db/mysql,classpath:db/test-migration"));

        assertTrue(causeMessageContains(startupFailure, "MySQL Flyway 迁移历史不兼容"),
                "失败必须来自自动装配的 MySQL Flyway 历史守卫");
        assertFalse(tableExists(databaseUrl, "mysql_strategy_pending_marker"),
                "守卫失败前不得执行 V15 pending migration");
    }

    private static ConfigurableApplicationContext startMysqlContext(String databaseUrl, String flywayLocations) {
        return new SpringApplicationBuilder(FishingPlatformApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("mysql")
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + databaseUrl,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.hikari.connection-init-sql=SET TIME ZONE '+08:00'",
                        "--spring.flyway.locations=" + flywayLocations,
                        "--fishing.bootstrap-admin.username=strategy_admin",
                        "--fishing.bootstrap-admin.display-name=安全管理员",
                        "--fishing.bootstrap-admin.password=SecureBootstrap1234");
    }

    private static void createLegacyDemoHistoryWithUnexpectedMigration(String databaseUrl) throws SQLException {
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/schema", "classpath:db/demo")
                .target("5")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
             var maxRank = connection.prepareStatement("SELECT MAX(installed_rank) FROM flyway_schema_history")) {
            try (var resultSet = maxRank.executeQuery()) {
                resultSet.next();
                try (var insert = connection.prepareStatement("""
                        INSERT INTO flyway_schema_history
                            (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                        VALUES (?, '70', 'unexpected historical migration', 'SQL', 'V70__unexpected_history.sql', 0, 'sa', 0, TRUE)
                        """)) {
                    insert.setInt(1, resultSet.getInt(1) + 1);
                    insert.executeUpdate();
                }
            }
        }
    }

    private static boolean causeMessageContains(Throwable throwable, String expectedFragment) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expectedFragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tableExists(String databaseUrl, String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
             var resultSet = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    public static class LegacyDemoHistoryInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Flyway legacyFlyway = Flyway.configure()
                    .dataSource(LEGACY_DATABASE_URL, "sa", "")
                    .locations("classpath:db/schema", "classpath:db/demo", "classpath:db/mysql")
                    .target("12")
                    .load();
            legacyFlyway.migrate();

            JdbcTemplate legacyDatabase = new JdbcTemplate(
                    new org.springframework.jdbc.datasource.DriverManagerDataSource(LEGACY_DATABASE_URL, "sa", ""));
            legacyDatabase.update("""
                    INSERT INTO app_user (username, password_hash, display_name, role)
                    VALUES ('legacy_operator', 'not-used', '历史操作员', 'OPERATOR')
                    """);
            legacyDatabase.update("""
                    INSERT INTO member (member_no, name, phone)
                    VALUES ('LEGACY-PHONE-A', '历史会员甲', '139-0000-9999'),
                           ('LEGACY-PHONE-B', '历史会员乙', '139 0000 9999')
                    """);
            legacyDatabase.update("""
                    INSERT INTO booking (
                        booking_no, member_id, spot_id, user_id, fishing_date, time_slot,
                        guests, amount, status, contact_name, contact_phone, active_customer_key
                    )
                    SELECT CONCAT('LEGACY-PHONE-', m.id), m.id,
                           (SELECT MIN(id) FROM fishing_spot),
                           (SELECT MIN(id) FROM app_user),
                           DATE '2026-08-30', 'MORNING', 1, 0, 'CONFIRMED',
                           m.name, m.phone, CONCAT('M:', m.id)
                    FROM member m
                    WHERE m.member_no IN ('LEGACY-PHONE-A', 'LEGACY-PHONE-B')
                    """);
        }
    }
}
