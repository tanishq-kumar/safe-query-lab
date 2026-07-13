package dev.querylab.common.search;

/**
 * Escapes SQL LIKE wildcards so user input matches <em>literally</em>.
 *
 * <p>Parameter binding stops classic SQL injection, but a bound LIKE pattern still
 * interprets {@code %}, {@code _} and the escape character itself. Unescaped, a
 * search for {@code "100%"} matches everything starting with "100" — a soft
 * injection that silently corrupts results. Every engine that does not escape
 * internally (all except jOOQ) must route user text through this class; the
 * conformance suite proves it with a seeded literal-{@code %} row.
 *
 * <p>The escape character is backslash, which is also PostgreSQL's default —
 * but each engine still declares {@code ESCAPE '\'} explicitly where its API
 * allows, so the SQL is portable and self-documenting.
 */
public final class LikeEscaper {

    public static final char ESCAPE_CHAR = '\\';

    private LikeEscaper() {
    }

    /** Escapes {@code \}, {@code %} and {@code _} in the given text. */
    public static String escape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** Builds a case-insensitive-ready containment pattern: {@code %escaped%}. */
    public static String containsPattern(String text) {
        return "%" + escape(text) + "%";
    }
}
