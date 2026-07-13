package dev.querylab.api;

import dev.querylab.conformance.SeedData;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Dev-time Testcontainers launcher:
 *
 * <pre>./mvnw -pl query-api spring-boot:test-run</pre>
 *
 * boots the real application against a throwaway Postgres — Flyway migrates it,
 * {@code seedRunner} loads the conformance dataset, Swagger UI is ready at
 * <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a>. Zero local setup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestQueryLabApplication {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    Object seedRunner(DataSource dataSource) {
        SeedData.insertAll(dataSource);
        return new Object();
    }

    public static void main(String[] args) {
        SpringApplication.from(QueryLabApplication::main)
                .with(TestQueryLabApplication.class)
                .run(args);
    }
}
