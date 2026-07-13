package dev.querylab.api;

import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.engine.jdbc.JdbcTransactionSearchAdapter;
import dev.querylab.engine.jooq.JooqTransactionSearchAdapter;
import dev.querylab.engine.mybatis.MybatisTransactionSearchAdapter;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Renders the SQL an engine WOULD run, without executing it. Only possible for
 * the engines that build their SQL eagerly (JDBC, jOOQ, MyBatis); Hibernate
 * generates SQL deep inside flushing/execution, so the two JPA-based engines
 * honestly report "unsupported" — itself a point of comparison: SQL visibility
 * is an architectural property, not a feature toggle.
 */
@Component
public class SqlExplainer {

    public record Explanation(String engine, boolean supported, String sql, String note) {
    }

    public Explanation explain(TransactionSearchPort port, TransactionSearchCriteria criteria) {
        Optional<String> sql = switch (port) {
            case JdbcTransactionSearchAdapter jdbc -> Optional.of(jdbc.renderSql(criteria));
            case JooqTransactionSearchAdapter jooq -> Optional.of(jooq.renderSql(criteria));
            case MybatisTransactionSearchAdapter mybatis -> Optional.of(mybatis.renderSql(criteria));
            default -> Optional.empty();
        };
        return sql
                .map(s -> new Explanation(port.engineName(), true, s, "Bind parameters shown as placeholders; values are always bound, never inlined."))
                .orElseGet(() -> new Explanation(port.engineName(), false, null,
                        "Hibernate-backed engines generate SQL during execution; it is visible in logs (spring.jpa.show-sql) but not extractable up front."));
    }
}
