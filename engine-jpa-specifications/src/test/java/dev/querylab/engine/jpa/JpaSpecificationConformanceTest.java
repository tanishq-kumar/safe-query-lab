package dev.querylab.engine.jpa;

import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.conformance.AbstractTransactionSearchConformanceTest;
import dev.querylab.conformance.ConformancePostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = JpaConformanceTestApp.class)
class JpaSpecificationConformanceTest extends AbstractTransactionSearchConformanceTest {

    @Autowired
    @Qualifier("jpa")
    private TransactionSearchPort port;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // Points Spring at the ALREADY migrated + seeded singleton container;
        // Hibernate manages nothing (Flyway owns the schema).
        registry.add("spring.datasource.url", ConformancePostgres::jdbcUrl);
        registry.add("spring.datasource.username", ConformancePostgres::username);
        registry.add("spring.datasource.password", ConformancePostgres::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Override
    protected TransactionSearchPort port() {
        return port;
    }
}
