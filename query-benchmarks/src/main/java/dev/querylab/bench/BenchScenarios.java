package dev.querylab.bench;

import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.SortDirection;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;

import java.math.BigDecimal;
import java.time.Instant;

/** The two workloads every engine is measured on — identical by construction. */
public final class BenchScenarios {

    /** One predicate, default sort: the cheap common case. */
    public static final TransactionSearchCriteria SIMPLE = TransactionSearchCriteria.builder()
            .statuses(TransactionStatus.COMPLETED)
            .size(50)
            .build();

    /** Five predicates + IN + ILIKE + custom sort + deep-ish page: the worst realistic case. */
    public static final TransactionSearchCriteria COMPLEX = TransactionSearchCriteria.builder()
            .statuses(TransactionStatus.COMPLETED, TransactionStatus.PENDING)
            .type(TransactionType.DEBIT)
            .currency("USD")
            .minAmount(new BigDecimal("10"))
            .maxAmount(new BigDecimal("900"))
            .createdFrom(Instant.parse("2025-11-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-15T00:00:00Z"))
            .descriptionContains("ref")
            .sortBy(SortKey.AMOUNT)
            .sortDirection(SortDirection.ASC)
            .page(3)
            .size(50)
            .build();

    private BenchScenarios() {
    }

    public static TransactionSearchCriteria byName(String scenario) {
        return switch (scenario) {
            case "simple" -> SIMPLE;
            case "complex" -> COMPLEX;
            default -> throw new IllegalArgumentException("Unknown scenario " + scenario);
        };
    }
}
