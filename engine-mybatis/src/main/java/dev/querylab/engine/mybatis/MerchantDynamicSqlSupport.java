package dev.querylab.engine.mybatis;

import org.mybatis.dynamic.sql.AliasableSqlTable;
import org.mybatis.dynamic.sql.SqlColumn;

import java.util.UUID;

/** Table support for the joined {@code merchant} table (optional, LEFT JOIN). */
public final class MerchantDynamicSqlSupport {

    public static final Merchant merchant = new Merchant();
    public static final SqlColumn<UUID> id = merchant.id;
    public static final SqlColumn<String> name = merchant.name;
    public static final SqlColumn<String> category = merchant.category;
    public static final SqlColumn<String> country = merchant.country;

    private MerchantDynamicSqlSupport() {
    }

    public static final class Merchant extends AliasableSqlTable<Merchant> {
        public final SqlColumn<UUID> id = column("id");
        public final SqlColumn<String> name = column("name");
        public final SqlColumn<String> category = column("category");
        public final SqlColumn<String> country = column("country");

        public Merchant() {
            super("merchant", Merchant::new);
        }
    }
}
