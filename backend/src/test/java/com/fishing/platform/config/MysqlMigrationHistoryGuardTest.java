package com.fishing.platform.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.extensibility.MigrationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.DriverManager;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlMigrationHistoryGuardTest {

    @Test
    void allowsOnlyTheTwoLegacyDemoMigrationsToBeMissingSuccessfully() {
        Flyway flyway = flywayFor(
                migration("1", MigrationState.SUCCESS),
                migration("2", MigrationState.MISSING_SUCCESS),
                migration("3", MigrationState.SUCCESS),
                migration("4", MigrationState.MISSING_SUCCESS),
                migration("5", MigrationState.SUCCESS),
                migration("6", MigrationState.SUCCESS),
                migration("7", MigrationState.SUCCESS));

        assertDoesNotThrow(() -> new MysqlMigrationHistoryGuard().migrate(flyway));
        verify(flyway).migrate();
    }

    @ParameterizedTest
    @MethodSource("incompatibleMigrationStates")
    void rejectsUnexpectedMissingFutureAndFailedMigrationHistory(String version, MigrationState state) {
        Flyway flyway = flywayFor(migration(version, state));

        assertThrows(IllegalStateException.class, () -> new MysqlMigrationHistoryGuard().migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void checksHistoryBeforeExecutingAnyPendingMigration() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService migrationInfoService = mock(MigrationInfoService.class);
        MigrationInfo[] migrations = {
                migration("2", MigrationState.MISSING_SUCCESS),
                migration("4", MigrationState.MISSING_SUCCESS),
                migration("15", MigrationState.PENDING)
        };
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(migrations);

        new MysqlMigrationHistoryGuard().migrate(flyway);

        var inOrder = inOrder(flyway, migrationInfoService);
        inOrder.verify(flyway).info();
        inOrder.verify(migrationInfoService).all();
        inOrder.verify(flyway).migrate();
        inOrder.verify(flyway).info();
        inOrder.verify(migrationInfoService).all();
    }

    @Test
    void rejectsUnexpectedMissingHistoryBeforeExecutingAnyPendingMigration() {
        Flyway flyway = flywayFor(
                migration("70", MigrationState.MISSING_SUCCESS),
                migration("15", MigrationState.PENDING));

        assertThrows(IllegalStateException.class, () -> new MysqlMigrationHistoryGuard().migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @ParameterizedTest
    @MethodSource("legacyFingerprintMismatches")
    void rejectsLegacyVersionWhenItsStoredFingerprintDoesNotMatch(String version, String mismatchedField) {
        MigrationInfo migration = migration(version, MigrationState.MISSING_SUCCESS);
        switch (mismatchedField) {
            case "checksum" -> when(migration.getChecksum()).thenReturn(1);
            case "script" -> when(migration.getScript()).thenReturn("V" + version + "__untrusted.sql");
            case "description" -> when(migration.getDescription()).thenReturn("untrusted description");
            case "type" -> when(migration.getType().name()).thenReturn("JDBC");
            default -> throw new IllegalArgumentException("未知历史指纹字段：" + mismatchedField);
        }
        Flyway flyway = flywayFor(migration);

        assertThrows(IllegalStateException.class, () -> new MysqlMigrationHistoryGuard().migrate(flyway));
        verify(flyway, never()).migrate();
    }

    @Test
    void rejectsUnexpectedMissingHistoryEvenWhenFlywayAllowsVersionedMissingMigrations() throws Exception {
        String databaseUrl = "jdbc:h2:mem:mysql_migration_guard_negative;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/schema", "classpath:db/demo")
                .target("5")
                .load()
                .migrate();
        Flyway mysqlFlyway = Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/schema", "classpath:db/mysql")
                .ignoreMigrationPatterns("versioned:missing", "*:future")
                .load();
        mysqlFlyway.migrate();

        try (var connection = DriverManager.getConnection(databaseUrl, "sa", "");
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT MAX(installed_rank) FROM flyway_schema_history")) {
            resultSet.next();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                    VALUES (?, '70', 'unexpected legacy migration', 'SQL', 'V70__unexpected.sql', 0, 'sa', 0, TRUE)
                    """)) {
                insert.setInt(1, resultSet.getInt(1) + 1);
                insert.executeUpdate();
            }
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new MysqlMigrationHistoryGuard().migrate(mysqlFlyway));
        assertTrue(exception.getMessage().contains("版本 70"));
    }

    private static Stream<Arguments> incompatibleMigrationStates() {
        return Stream.of(
                Arguments.of("7", MigrationState.MISSING_SUCCESS),
                Arguments.of("2", MigrationState.MISSING_FAILED),
                Arguments.of("7", MigrationState.FUTURE_SUCCESS),
                Arguments.of("7", MigrationState.FUTURE_FAILED),
                Arguments.of("6", MigrationState.FAILED));
    }

    private static Stream<Arguments> legacyFingerprintMismatches() {
        return Stream.of(
                Arguments.of("2", "checksum"),
                Arguments.of("2", "script"),
                Arguments.of("2", "description"),
                Arguments.of("2", "type"),
                Arguments.of("4", "checksum"),
                Arguments.of("4", "script"),
                Arguments.of("4", "description"),
                Arguments.of("4", "type"));
    }

    private static Flyway flywayFor(MigrationInfo... migrations) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService migrationInfoService = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(migrations);
        return flyway;
    }

    private static MigrationInfo migration(String version, MigrationState state) {
        MigrationInfo migration = mock(MigrationInfo.class);
        MigrationType type = mock(MigrationType.class);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(migration.getState()).thenReturn(state);
        when(type.name()).thenReturn("SQL");
        when(migration.getType()).thenReturn(type);
        if ("2".equals(version)) {
            when(migration.getScript()).thenReturn("V2__seed_demo_data.sql");
            when(migration.getDescription()).thenReturn("seed demo data");
            when(migration.getChecksum()).thenReturn(-819248013);
        } else if ("4".equals(version)) {
            when(migration.getScript()).thenReturn("V4__simplify_demo_admin_password.sql");
            when(migration.getDescription()).thenReturn("simplify demo admin password");
            when(migration.getChecksum()).thenReturn(-1236123812);
        } else {
            when(migration.getScript()).thenReturn("V" + version + "__test.sql");
            when(migration.getDescription()).thenReturn("test");
            when(migration.getChecksum()).thenReturn(0);
        }
        return migration;
    }
}
