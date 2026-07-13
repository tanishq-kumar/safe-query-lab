package dev.querylab.bench;

import org.flywaydb.core.Flyway;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

/**
 * Orchestrates the whole run from the PARENT process: starts one Postgres
 * container, migrates and seeds it with 100k deterministic rows, then launches
 * JMH. Forked benchmark JVMs cannot see this process's objects, so the
 * container's JDBC coordinates travel via {@code jvmArgsAppend} system
 * properties — the standard answer to "JMH forking versus Testcontainers".
 *
 * <p>Run with: {@code ./mvnw -pl query-benchmarks exec:java}
 */
public final class BenchmarkRunner {

    private static final int ROWS = 100_000;
    private static final Instant BASE = Instant.parse("2026-01-15T12:00:00Z");

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            seed(postgres);

            Files.createDirectories(Path.of("results"));
            Options options = new OptionsBuilder()
                    .include(EndToEndSearchBenchmark.class.getSimpleName())
                    .include(QueryConstructionBenchmark.class.getSimpleName())
                    .forks(1)
                    .warmupIterations(3)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(5)
                    .measurementTime(TimeValue.seconds(2))
                    .jvmArgsAppend(
                            "-Dbench.jdbc.url=" + postgres.getJdbcUrl(),
                            "-Dbench.jdbc.user=" + postgres.getUsername(),
                            "-Dbench.jdbc.password=" + postgres.getPassword())
                    .resultFormat(ResultFormatType.JSON)
                    .result("results/jmh.json")
                    .build();

            new Runner(options).run();
        }
    }

    /** 100k deterministic rows (fixed seed, fixed base time) in 5k-row batches. */
    private static void seed(PostgreSQLContainer<?> postgres) throws Exception {
        Random random = new Random(7);
        String[] currencies = {"USD", "EUR", "JPY"};
        String[] statuses = {"PENDING", "COMPLETED", "FAILED", "CANCELLED"};
        String[] words = {"Invoice", "Transfer", "Subscription", "Payroll", "Refund"};
        UUID account = new UUID(0xBE7C, 1);
        UUID account2 = new UUID(0xBE7C, 2);

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setAutoCommit(false);
            try (PreparedStatement accounts = connection.prepareStatement(
                    "INSERT INTO account (id, name, risk_rating) VALUES (?, ?, ?)")) {
                for (UUID id : new UUID[]{account, account2}) {
                    accounts.setObject(1, id);
                    accounts.setString(2, "Bench " + id);
                    accounts.setString(3, "LOW");
                    accounts.addBatch();
                }
                accounts.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO transactions
                      (id, account_id, amount, currency, status, type, description, counterparty, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                for (int i = 0; i < ROWS; i++) {
                    insert.setObject(1, new UUID(0xBE7C_0001L, i));
                    insert.setObject(2, random.nextBoolean() ? account : account2);
                    insert.setBigDecimal(3, java.math.BigDecimal.valueOf(100 + random.nextInt(99_900), 2));
                    insert.setString(4, currencies[random.nextInt(currencies.length)]);
                    insert.setString(5, statuses[random.nextInt(statuses.length)]);
                    insert.setString(6, random.nextBoolean() ? "DEBIT" : "CREDIT");
                    insert.setString(7, words[random.nextInt(words.length)] + " payment ref " + i);
                    insert.setString(8, random.nextInt(7) == 0 ? null : "Counterparty " + random.nextInt(500));
                    insert.setObject(9, BASE.minus(random.nextInt(120), ChronoUnit.DAYS)
                            .minusSeconds(random.nextInt(86_400)).atOffset(ZoneOffset.UTC));
                    insert.addBatch();
                    if ((i + 1) % 5_000 == 0) {
                        insert.executeBatch();
                        connection.commit();
                    }
                }
                insert.executeBatch();
                connection.commit();
            }
        }
        System.out.printf("Seeded %,d rows%n", ROWS);
    }
}
