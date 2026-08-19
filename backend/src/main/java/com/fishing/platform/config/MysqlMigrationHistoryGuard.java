package com.fishing.platform.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.extensibility.MigrationType;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Keeps the temporary Flyway missing-migration allowance scoped to the two
 * demo-only migrations that were historically applied to MySQL installations.
 */
@Component("mysqlMigrationHistoryGuard")
@Profile("mysql")
public class MysqlMigrationHistoryGuard implements FlywayMigrationStrategy {
    private static final Map<String, LegacyDemoMigration> LEGACY_DEMO_MIGRATIONS = Map.of(
            "2", new LegacyDemoMigration(
                    "V2__seed_demo_data.sql", "seed demo data", -819248013),
            "4", new LegacyDemoMigration(
                    "V4__simplify_demo_admin_password.sql", "simplify demo admin password", -1236123812));

    @Override
    public void migrate(Flyway flyway) {
        verifyMigrationHistory(flyway);
        flyway.migrate();
        verifyMigrationHistory(flyway);
    }

    private void verifyMigrationHistory(Flyway flyway) {
        for (MigrationInfo migration : flyway.info().all()) {
            MigrationState state = migration.getState();
            String version = migration.getVersion() == null ? null : migration.getVersion().toString();
            if (isRecognizedLegacyDemoMigration(migration, version, state)) {
                continue;
            }
            if (state == MigrationState.MISSING_SUCCESS
                    || state == MigrationState.MISSING_FAILED
                    || state == MigrationState.FUTURE_SUCCESS
                    || state == MigrationState.FUTURE_FAILED
                    || state == MigrationState.FAILED) {
                throw new IllegalStateException("MySQL Flyway 迁移历史不兼容："
                        + migration.getScript() + " (版本 " + version + ", 状态 " + state + ")");
            }
        }
    }

    private boolean isRecognizedLegacyDemoMigration(MigrationInfo migration,
                                                     String version,
                                                     MigrationState state) {
        LegacyDemoMigration expected = LEGACY_DEMO_MIGRATIONS.get(version);
        MigrationType type = migration.getType();
        return state == MigrationState.MISSING_SUCCESS
                && expected != null
                && expected.script().equals(migration.getScript())
                && expected.description().equals(migration.getDescription())
                && Integer.valueOf(expected.checksum()).equals(migration.getChecksum())
                && type != null
                && "SQL".equals(type.name());
    }

    private record LegacyDemoMigration(String script, String description, int checksum) {
    }
}
