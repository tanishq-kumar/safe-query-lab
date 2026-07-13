package dev.querylab.conformance;

import dev.querylab.common.search.TransactionSearchPort;

/**
 * Runs the suite against the reference implementation itself. The
 * reference-relative assertions are tautological here; the value is in the
 * absolute assertions on named seed rows, which prove the suite's expectations
 * (and the seed's edge-case construction) before any SQL engine exists.
 */
class ReferencePortConformanceTest extends AbstractTransactionSearchConformanceTest {

    private static final ReferencePortAdapter PORT = new ReferencePortAdapter();

    @Override
    protected TransactionSearchPort port() {
        return PORT;
    }
}
