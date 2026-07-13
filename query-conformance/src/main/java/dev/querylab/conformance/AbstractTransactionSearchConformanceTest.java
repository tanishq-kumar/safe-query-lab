package dev.querylab.conformance;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortDirection;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.querylab.common.search.TransactionSearchCriteria.MAX_PAGE_SIZE;
import static dev.querylab.common.search.TransactionSearchCriteria.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

/**
 * The behavioral-equivalence contract. Every engine module contributes a small
 * concrete subclass supplying its {@link TransactionSearchPort}; passing this
 * suite against the shared seeded Postgres proves the engine is interchangeable
 * with the other four.
 *
 * <p>Assertions are two-layered: every case compares the engine's full result
 * (content, order, and count) against the in-memory {@link ReferencePortAdapter},
 * and the edge cases additionally assert absolute expectations on named seed
 * rows — which is what validates the reference implementation itself.
 */
public abstract class AbstractTransactionSearchConformanceTest {

    private static final ReferencePortAdapter REFERENCE = new ReferencePortAdapter();

    /** The engine under test. Called repeatedly; return a cheap reference. */
    protected abstract TransactionSearchPort port();

    // ---------------------------------------------------------------- helpers

    private SearchResult<Transaction> assertMatchesReference(TransactionSearchCriteria criteria) {
        SearchResult<Transaction> expected = REFERENCE.search(criteria);
        SearchResult<Transaction> actual = port().search(criteria);

        assertThat(actual.content())
                .as("page content (order-sensitive) for %s", criteria)
                .containsExactlyElementsOf(expected.content());
        assertThat(actual.totalElements())
                .as("totalElements for %s", criteria)
                .isEqualTo(expected.totalElements());
        return actual;
    }

    private static TransactionSearchCriteria.Builder allRows() {
        return builder().size(MAX_PAGE_SIZE);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void emptyCriteriaReturnsEveryRowNewestFirst() {
        SearchResult<Transaction> result = assertMatchesReference(allRows().build());

        assertThat(result.totalElements()).isEqualTo(SeedData.EXPECTED.size());
        assertThat(result.content()).hasSize(SeedData.EXPECTED.size());
    }

    @ParameterizedTest
    @MethodSource("singleFilterCriteria")
    void eachFilterAloneNarrowsCorrectly(TransactionSearchCriteria criteria) {
        SearchResult<Transaction> result = assertMatchesReference(criteria);

        // Guard against a vacuous pass: the seed guarantees every filter has hits.
        assertThat(result.content()).isNotEmpty();
        assertThat(result.totalElements()).isLessThan(SeedData.EXPECTED.size());
    }

    static Stream<Arguments> singleFilterCriteria() {
        return Stream.of(
                named("statuses", allRows().statuses(TransactionStatus.FAILED).build()),
                named("type", allRows().type(TransactionType.CREDIT).build()),
                named("accountId", allRows().accountId(SeedData.ACCOUNT_SINGLE).build()),
                named("currency", allRows().currency("JPY").build()),
                named("minAmount", allRows().minAmount(new BigDecimal("500")).build()),
                named("maxAmount", allRows().maxAmount(new BigDecimal("10")).build()),
                named("createdFrom", allRows().createdFrom(SeedData.RANGE_FROM).build()),
                named("createdTo", allRows().createdTo(SeedData.RANGE_TO).build()),
                named("descriptionContains", allRows().descriptionContains("ref").build()),
                named("accountRiskRating", allRows().accountRiskRating("MEDIUM").build()),
                named("merchantCategory", allRows().merchantCategory("RETAIL").build()),
                named("merchantCountry", allRows().merchantCountry("DE").build())
        ).map(Arguments::of);
    }

    @Test
    void accountRiskRatingFiltersAcrossTheAccountJoin() {
        // ACCOUNT_SINGLE is the only HIGH-risk account and has exactly one row.
        SearchResult<Transaction> result = assertMatchesReference(
                allRows().accountRiskRating("HIGH").build());

        assertThat(result.content()).containsExactly(SeedData.TX_SINGLE_ACCOUNT);
        assertThat(result.content()).allMatch(t -> "HIGH".equals(t.accountRiskRating()));
    }

    @Test
    void merchantFilterExcludesTransactionsWithNoMerchant() {
        SearchResult<Transaction> result = assertMatchesReference(
                allRows().merchantCategory("RETAIL").build());

        // Retail merchants: Retail GmbH (TX_PERCENT, TX_HUNDRED) and Nippon KK (TX_JPY),
        // plus generated rows — but never a null-merchant row.
        assertThat(result.content())
                .contains(SeedData.TX_PERCENT, SeedData.TX_HUNDRED, SeedData.TX_JPY)
                .doesNotContain(SeedData.TX_NULL_COUNTERPARTY, SeedData.TX_BOUNDARY_LOW)
                .allMatch(t -> "RETAIL".equals(t.merchantCategory()));
    }

    @Test
    void joinedFieldsAreProjectedIncludingNulls() {
        // A merchant-bearing row carries all three merchant fields...
        SearchResult<Transaction> coffee = port().search(
                allRows().descriptionContains("coffee shop").build());
        assertThat(coffee.content()).containsExactly(SeedData.TX_COFFEE);
        assertThat(coffee.content().getFirst().merchantName()).isEqualTo("Blue Bottle");
        assertThat(coffee.content().getFirst().accountRiskRating()).isEqualTo("MEDIUM");

        // ...while a merchant-less row projects null merchant fields (LEFT JOIN),
        // and full-record equality against the reference already asserts this.
        SearchResult<Transaction> hold = assertMatchesReference(
                allRows().descriptionContains("card hold").build());
        assertThat(hold.content()).containsExactly(SeedData.TX_NULL_COUNTERPARTY);
        assertThat(hold.content().getFirst().merchantName()).isNull();
        assertThat(hold.content().getFirst().merchantCategory()).isNull();
    }

    @Test
    void statusSetBecomesAnInClause() {
        SearchResult<Transaction> result = assertMatchesReference(
                allRows().statuses(TransactionStatus.FAILED, TransactionStatus.CANCELLED).build());

        assertThat(result.content())
                .isNotEmpty()
                .allMatch(t -> t.status() == TransactionStatus.FAILED
                        || t.status() == TransactionStatus.CANCELLED);
    }

    @Test
    void combinedFiltersComposeWithAnd() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .statuses(TransactionStatus.COMPLETED, TransactionStatus.PENDING)
                .type(TransactionType.DEBIT)
                .currency("USD")
                .minAmount(new BigDecimal("1"))
                .descriptionContains("ref")
                .build());

        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void noMatchReturnsEmptyPageWithZeroTotal() {
        SearchResult<Transaction> result = assertMatchesReference(
                allRows().currency("XXX").build());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void amountBoundsAreInclusive() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .minAmount(new BigDecimal("100.0000"))
                .maxAmount(new BigDecimal("100.0000"))
                .build());

        assertThat(result.content()).contains(SeedData.TX_BOUNDARY_EXACT);
        assertThat(result.content()).doesNotContain(SeedData.TX_BOUNDARY_LOW, SeedData.TX_BOUNDARY_HIGH);
    }

    @Test
    void dateRangeIsHalfOpen() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .createdFrom(SeedData.RANGE_FROM)
                .createdTo(SeedData.RANGE_TO)
                .build());

        // [from, to): the row AT from is included, the row AT to is excluded.
        assertThat(result.content()).contains(SeedData.TX_AT_FROM);
        assertThat(result.content()).doesNotContain(SeedData.TX_AT_TO);
    }

    @Test
    void negativeAndZeroAmountsAreOrdinaryValues() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .maxAmount(new BigDecimal("0"))
                .build());

        assertThat(result.content()).contains(SeedData.TX_ZERO, SeedData.TX_NEGATIVE);
    }

    @Test
    void nullCounterpartyRoundTripsIntact() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .descriptionContains("card hold")
                .build());

        // Full record equality: proves the engine maps NULL to null, not "" or a crash.
        assertThat(result.content()).containsExactly(SeedData.TX_NULL_COUNTERPARTY);
    }

    @Test
    void textSearchIsCaseInsensitive() {
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .descriptionContains("coffee shop")
                .build());

        assertThat(result.content()).containsExactly(SeedData.TX_COFFEE);
    }

    @Test
    void percentWildcardInUserTextMatchesLiterally() {
        // Unescaped, "100%" degenerates to "contains 100" and would also match
        // the TX_HUNDRED decoy ("Invoice 100 dollars flat").
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .descriptionContains("100%")
                .build());

        assertThat(result.content()).containsExactly(SeedData.TX_PERCENT);
    }

    @Test
    void underscoreWildcardInUserTextMatchesLiterally() {
        // Unescaped, '_' matches any character, so "_case" would also match the
        // TX_SPACE_CASE decoy ("test case sensitivity row").
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .descriptionContains("_case")
                .build());

        assertThat(result.content()).containsExactly(SeedData.TX_UNDERSCORE);
    }

    @Test
    void unicodeContentRoundTrips() {
        // The search term stays ASCII on purpose: case-folding of non-ASCII text
        // differs between Java and Postgres's C locale (and between the engines'
        // lower()/upper() strategies). Full record equality still proves the
        // ü/Ü content survived the round trip byte-perfectly.
        SearchResult<Transaction> result = assertMatchesReference(allRows()
                .descriptionContains("nach m")
                .build());

        assertThat(result.content()).containsExactly(SeedData.TX_UNICODE);
    }

    @Test
    void paginationSlicesConsistently() {
        int pageSize = 10;
        long total = REFERENCE.search(allRows().build()).totalElements();
        int lastPage = (int) ((total - 1) / pageSize);

        SearchResult<Transaction> first = assertMatchesReference(builder().size(pageSize).page(0).build());
        assertThat(first.content()).hasSize(pageSize);

        SearchResult<Transaction> last = assertMatchesReference(builder().size(pageSize).page(lastPage).build());
        assertThat(last.content()).hasSizeBetween(1, pageSize);

        SearchResult<Transaction> beyond = assertMatchesReference(builder().size(pageSize).page(lastPage + 5).build());
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).as("total is not affected by the requested page").isEqualTo(total);
    }

    @Test
    void totalElementsIsInvariantAcrossPages() {
        var page0 = port().search(builder().currency("USD").size(5).page(0).build());
        var page2 = port().search(builder().currency("USD").size(5).page(2).build());

        assertThat(page0.totalElements()).isPositive().isEqualTo(page2.totalElements());
    }

    @ParameterizedTest
    @MethodSource("sortCombinations")
    void sortingMatchesReferenceExactly(SortKey key, SortDirection direction) {
        assertMatchesReference(allRows().sortBy(key).sortDirection(direction).build());
    }

    static Stream<Arguments> sortCombinations() {
        return Stream.of(SortKey.values())
                .flatMap(key -> Stream.of(SortDirection.values())
                        .map(direction -> Arguments.of(key, direction)));
    }

    @Test
    void duplicateSortKeysPaginateStably() {
        // Three seed rows share the same amount AND created_at; only the id ASC
        // tiebreak makes page walks deterministic. Walk them one row at a time.
        List<Transaction> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            SearchResult<Transaction> slice = assertMatchesReference(builder()
                    .descriptionContains("duplicate sort key")
                    .sortBy(SortKey.AMOUNT)
                    .sortDirection(SortDirection.ASC)
                    .size(1)
                    .page(page)
                    .build());
            collected.addAll(slice.content());
        }

        assertThat(collected)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(SeedData.TX_DUP_A, SeedData.TX_DUP_B, SeedData.TX_DUP_C);
    }
}
