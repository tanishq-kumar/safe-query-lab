package dev.querylab.engine.mybatis;

import org.mybatis.dynamic.sql.AliasableSqlTable;
import org.mybatis.dynamic.sql.SqlColumn;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.Instant;
import java.util.UUID;

/**
 * The hand-written table support class — exactly what MyBatis Generator would
 * emit. Typed {@link SqlColumn} constants are MyBatis Dynamic SQL's answer to
 * jOOQ's generated metamodel, minus the build-time schema check: nothing
 * verifies these names against the database until a query runs.
 */
public final class TransactionDynamicSqlSupport {

    public static final Transactions transactions = new Transactions();
    public static final SqlColumn<UUID> id = transactions.id;
    public static final SqlColumn<UUID> accountId = transactions.accountId;
    public static final SqlColumn<BigDecimal> amount = transactions.amount;
    public static final SqlColumn<String> currency = transactions.currency;
    public static final SqlColumn<String> status = transactions.status;
    public static final SqlColumn<String> type = transactions.type;
    public static final SqlColumn<String> description = transactions.description;
    public static final SqlColumn<String> counterparty = transactions.counterparty;
    public static final SqlColumn<Instant> createdAt = transactions.createdAt;

    private TransactionDynamicSqlSupport() {
    }

    public static final class Transactions extends AliasableSqlTable<Transactions> {
        public final SqlColumn<UUID> id = column("id");
        public final SqlColumn<UUID> accountId = column("account_id");
        public final SqlColumn<BigDecimal> amount = column("amount");
        public final SqlColumn<String> currency = column("currency");
        public final SqlColumn<String> status = column("status");
        public final SqlColumn<String> type = column("type");
        public final SqlColumn<String> description = column("description");
        public final SqlColumn<String> counterparty = column("counterparty");
        public final SqlColumn<Instant> createdAt = column("created_at", JDBCType.TIMESTAMP_WITH_TIMEZONE);

        public Transactions() {
            super("transactions", Transactions::new);
        }
    }
}
