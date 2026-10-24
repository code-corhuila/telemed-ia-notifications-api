package com.telemed.notifications;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Prepares the ephemeral E2E database the way production does: the
 * telemed-ia-infra-postgres bootstrap for the notifications domain, then the
 * migrations of telemed-ia-notifications-db applied as notifications_app.
 */
final class OfficialDatabaseSchemaInitializer {

    static final String APP_USER = "notifications_app";
    static final String APP_PASSWORD = "notifications-e2e-password";

    private static final String MIGRATIONS_DIR_ENV =
            "NOTIFICATIONS_DB_MIGRATIONS_DIR";
    private static final String CHANGELOG =
            "changelog/changelog-master.yaml";

    private OfficialDatabaseSchemaInitializer() {
    }

    static void migrate(PostgreSQLContainer<?> postgres) {
        String configuredDir = System.getenv(MIGRATIONS_DIR_ENV);
        if (configuredDir == null || configuredDir.isBlank()) {
            throw new IllegalStateException(
                    MIGRATIONS_DIR_ENV + " is required for official-schema E2E tests."
            );
        }

        Path root = Path.of(configuredDir).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(CHANGELOG))) {
            throw new IllegalStateException("Missing official DB changelog in " + root);
        }

        bootstrapDomainRoles(postgres);

        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(root)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDatabaseChangeLogTableName("databasechangelog_notifications");
            database.setDatabaseChangeLogLockTableName("databasechangeloglock_notifications");
            try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not apply official notifications migrations for E2E tests.",
                    exception
            );
        }
    }

    // Mirrors the notifications block of postgres/init/01-instance.sh.
    private static void bootstrapDomainRoles(PostgreSQLContainer<?> postgres) {
        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + APP_USER
                    + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute("CREATE ROLE notifications_reader NOLOGIN");
            statement.execute("CREATE ROLE notifications_writer NOLOGIN");
            statement.execute("GRANT notifications_reader, notifications_writer TO "
                    + APP_USER + " WITH ADMIN OPTION");
            statement.execute("GRANT CREATE ON DATABASE " + postgres.getDatabaseName()
                    + " TO " + APP_USER);
            statement.execute("CREATE SCHEMA notifications AUTHORIZATION " + APP_USER);
            statement.execute("ALTER ROLE " + APP_USER + " SET search_path = notifications");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not bootstrap the notifications roles for E2E tests.",
                    exception
            );
        }
    }
}
