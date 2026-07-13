package dev.querylab.conformance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * One shared PostgreSQL container per test JVM — the classic Testcontainers
 * singleton. Deliberately NOT {@code @Testcontainers}/{@code @Container}, which
 * restart the database for every test class; here each engine module's Surefire
 * fork pays the container cost exactly once. Ryuk reaps the container when the
 * JVM exits.
 *
 * <p>The database is migrated with the very same Flyway migrations that
 * {@code query-api} runs at startup and that jOOQ codegen generates from
 * ({@code query-common:db/migration}), then seeded once with
 * {@link SeedData#insertAll(DataSource)}. All conformance tests are read-only,
 * so a single seeded database is safe to share.
 */
public final class ConformancePostgres {

    /** Single place the Postgres image tag is pinned for tests and benchmarks. */
    public static final String IMAGE = "postgres:16-alpine";

    private static PostgreSQLContainer<?> container;
    private static HikariDataSource dataSource;

    private ConformancePostgres() {
    }

    /** Starts (once), migrates (once), seeds (once), and returns the shared pool. */
    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            container = new PostgreSQLContainer<>(IMAGE);
            container.start();

            Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(container.getJdbcUrl());
            config.setUsername(container.getUsername());
            config.setPassword(container.getPassword());
            config.setMaximumPoolSize(4);
            dataSource = new HikariDataSource(config);

            SeedData.insertAll(dataSource);
        }
        return dataSource;
    }

    public static synchronized String jdbcUrl() {
        dataSource();
        return container.getJdbcUrl();
    }

    public static synchronized String username() {
        dataSource();
        return container.getUsername();
    }

    public static synchronized String password() {
        dataSource();
        return container.getPassword();
    }
}
