package dev.querylab.engine.jooq;

import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.conformance.AbstractTransactionSearchConformanceTest;
import dev.querylab.conformance.ConformancePostgres;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** No Spring here either: a DSLContext straight over the shared DataSource. */
class JooqConformanceTest extends AbstractTransactionSearchConformanceTest {

    private static final JooqTransactionSearchAdapter PORT = new JooqTransactionSearchAdapter(
            DSL.using(ConformancePostgres.dataSource(), SQLDialect.POSTGRES));

    @Override
    protected TransactionSearchPort port() {
        return PORT;
    }
}
