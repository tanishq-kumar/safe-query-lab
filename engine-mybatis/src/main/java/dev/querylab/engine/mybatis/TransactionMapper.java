package dev.querylab.engine.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.SelectProvider;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.CommonCountMapper;

import java.util.List;

/**
 * {@link SqlProviderAdapter} hands the pre-rendered statement (SQL + parameter
 * map) to MyBatis; the count method comes from {@link CommonCountMapper}.
 * Instant/UUID columns ride MyBatis's built-in JSR-310 handler and the
 * driver's native object mapping respectively.
 */
@Mapper
public interface TransactionMapper extends CommonCountMapper {

    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @Results(id = "transactionRow", value = {
            @Result(column = "id", property = "id", typeHandler = UuidTypeHandler.class),
            @Result(column = "account_id", property = "accountId", typeHandler = UuidTypeHandler.class),
            @Result(column = "amount", property = "amount"),
            @Result(column = "currency", property = "currency"),
            @Result(column = "status", property = "status"),
            @Result(column = "type", property = "type"),
            @Result(column = "description", property = "description"),
            @Result(column = "counterparty", property = "counterparty"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "risk_rating", property = "accountRiskRating"),
            @Result(column = "name", property = "merchantName"),
            @Result(column = "category", property = "merchantCategory"),
            @Result(column = "country", property = "merchantCountry")
    })
    List<TransactionRow> selectMany(SelectStatementProvider selectStatement);
}
