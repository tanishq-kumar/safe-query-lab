package dev.querylab.common.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LikeEscaperTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "plain text      | plain text",
            "100%            | 100\\%",
            "under_score     | under\\_score",
            "back\\slash     | back\\\\slash",
            "%_\\            | \\%\\_\\\\",
    })
    void escapesEveryLikeMetacharacter(String input, String expected) {
        assertThat(LikeEscaper.escape(input.trim())).isEqualTo(expected.trim());
    }

    @Test
    void backslashIsEscapedFirstSoEscapesAreNotDoubleEscaped() {
        // If % were escaped before \, "%" would become "\%" and then "\\%".
        assertThat(LikeEscaper.escape("%")).isEqualTo("\\%");
        assertThat(LikeEscaper.escape("\\%")).isEqualTo("\\\\\\%");
    }

    @Test
    void containsPatternWrapsWithWildcards() {
        assertThat(LikeEscaper.containsPattern("50% off")).isEqualTo("%50\\% off%");
    }
}
