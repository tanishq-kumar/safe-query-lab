package dev.querylab.engine.mybatis;

import org.mybatis.dynamic.sql.AliasableSqlTable;
import org.mybatis.dynamic.sql.SqlColumn;

import java.util.UUID;

/** Table support for the joined {@code account} table. */
public final class AccountDynamicSqlSupport {

    public static final Account account = new Account();
    public static final SqlColumn<UUID> id = account.id;
    public static final SqlColumn<String> riskRating = account.riskRating;

    private AccountDynamicSqlSupport() {
    }

    public static final class Account extends AliasableSqlTable<Account> {
        public final SqlColumn<UUID> id = column("id");
        public final SqlColumn<String> riskRating = column("risk_rating");

        public Account() {
            super("account", Account::new);
        }
    }
}
