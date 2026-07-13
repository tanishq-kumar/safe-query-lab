package dev.querylab.engine.jooq;

import dev.querylab.common.search.TransactionSearchPort;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@link DSLContext} is provided by spring-boot-starter-jooq in the API
 * module (or built by hand in tests/benchmarks — no Spring required by the
 * adapter itself).
 */
@Configuration
public class JooqEngineConfiguration {

    @Bean(name = "jooq")
    public TransactionSearchPort jooqSearchPort(DSLContext dsl) {
        return new JooqTransactionSearchAdapter(dsl);
    }
}
