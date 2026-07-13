package dev.querylab.engine.jdbc;

import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.conformance.AbstractTransactionSearchConformanceTest;
import dev.querylab.conformance.ConformancePostgres;

/**
 * No Spring context anywhere: adapter + DataSource, straight against the shared
 * seeded container. The fastest of the five conformance suites for a reason.
 */
class JdbcTransactionSearchConformanceTest extends AbstractTransactionSearchConformanceTest {

    private static final JdbcTransactionSearchAdapter PORT =
            new JdbcTransactionSearchAdapter(ConformancePostgres.dataSource());

    @Override
    protected TransactionSearchPort port() {
        return PORT;
    }
}
