package dev.querylab.common.search;

import dev.querylab.common.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionSearchCriteriaTest {

    @Test
    void defaultsAreAppliedForNullSortAndEmptyStrings() {
        var criteria = new TransactionSearchCriteria(null, null, null, " ", null, null,
                null, null, "", 0, 50, null, null);

        assertThat(criteria.statuses()).isEmpty();
        assertThat(criteria.currency()).isNull();
        assertThat(criteria.descriptionContains()).isNull();
        assertThat(criteria.sortBy()).isEqualTo(SortKey.CREATED_AT);
        assertThat(criteria.sortDirection()).isEqualTo(SortDirection.DESC);
    }

    @Test
    void sizeIsClampedToBounds() {
        assertThat(TransactionSearchCriteria.builder().size(0).build().size()).isEqualTo(1);
        assertThat(TransactionSearchCriteria.builder().size(-5).build().size()).isEqualTo(1);
        assertThat(TransactionSearchCriteria.builder().size(10_000).build().size())
                .isEqualTo(TransactionSearchCriteria.MAX_PAGE_SIZE);
    }

    @Test
    void negativePageIsRejected() {
        assertThatThrownBy(() -> TransactionSearchCriteria.builder().page(-1).build())
                .isInstanceOf(InvalidCriteriaException.class)
                .hasMessageContaining("page");
    }

    @Test
    void contradictoryAmountRangeIsRejected() {
        assertThatThrownBy(() -> TransactionSearchCriteria.builder()
                .minAmount(new BigDecimal("100"))
                .maxAmount(new BigDecimal("50"))
                .build())
                .isInstanceOf(InvalidCriteriaException.class)
                .hasMessageContaining("minAmount");
    }

    @Test
    void contradictoryDateRangeIsRejected() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThatThrownBy(() -> TransactionSearchCriteria.builder()
                .createdFrom(now.plusSeconds(1))
                .createdTo(now)
                .build())
                .isInstanceOf(InvalidCriteriaException.class)
                .hasMessageContaining("createdFrom");
    }

    @Test
    void equalFromAndToIsAllowedAndMeansEmptyHalfOpenRange() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var criteria = TransactionSearchCriteria.builder()
                .createdFrom(now).createdTo(now).build();
        assertThat(criteria.createdFrom()).isEqualTo(criteria.createdTo());
    }

    @Test
    void statusSetIsDefensivelyCopied() {
        Set<TransactionStatus> mutable = new HashSet<>(Set.of(TransactionStatus.PENDING));
        var criteria = TransactionSearchCriteria.builder().statuses(mutable).build();
        mutable.add(TransactionStatus.FAILED);

        assertThat(criteria.statuses()).containsExactly(TransactionStatus.PENDING);
    }

    @Test
    void offsetIsPageTimesSize() {
        assertThat(TransactionSearchCriteria.builder().page(3).size(25).build().offset())
                .isEqualTo(75);
    }
}
