package dev.querylab.common.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortKeyTest {

    @ParameterizedTest
    @CsvSource({
            "createdAt, CREATED_AT",
            "created_at, CREATED_AT",
            "CREATEDAT, CREATED_AT",
            "amount, AMOUNT",
            "id, ID",
    })
    void parsesWhitelistedFields(String raw, SortKey expected) {
        assertThat(SortKey.fromParam(raw)).isEqualTo(expected);
    }

    @Test
    void blankDefaultsToCreatedAt() {
        assertThat(SortKey.fromParam(null)).isEqualTo(SortKey.CREATED_AT);
        assertThat(SortKey.fromParam("  ")).isEqualTo(SortKey.CREATED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "counterparty",                   // real column, deliberately not whitelisted
            "amount; DROP TABLE transactions", // the attack the whitelist exists for
            "amount DESC, id",
    })
    void rejectsAnythingOffTheWhitelist(String raw) {
        assertThatThrownBy(() -> SortKey.fromParam(raw))
                .isInstanceOf(UnknownSortFieldException.class)
                .hasMessageContaining("Allowed");
    }
}
