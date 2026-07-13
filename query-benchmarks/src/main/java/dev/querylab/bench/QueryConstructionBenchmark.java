package dev.querylab.bench;

import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.engine.jdbc.JdbcTransactionSearchAdapter;
import dev.querylab.engine.jooq.JooqTransactionSearchAdapter;
import dev.querylab.engine.mybatis.MybatisTransactionSearchAdapter;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * Predicate assembly + SQL rendering only — no database, no connection. Covers
 * the three engines whose SQL is built eagerly and separably (JDBC, jOOQ,
 * MyBatis); Hibernate-based engines interleave SQL generation with execution
 * and cannot be measured this way — which is itself a finding.
 *
 * <p>The expected headline: construction is nanoseconds-to-microseconds while a
 * database round trip is hundreds of microseconds at best — "DSL overhead" is
 * noise in any I/O-bound workload.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class QueryConstructionBenchmark {

    @Param({"simple", "complex"})
    public String scenario;

    private TransactionSearchCriteria criteria;
    private JdbcTransactionSearchAdapter jdbc;
    private JooqTransactionSearchAdapter jooq;
    private MybatisTransactionSearchAdapter mybatis;

    @Setup
    public void setUp() {
        criteria = BenchScenarios.byName(scenario);
        jdbc = new JdbcTransactionSearchAdapter(null);           // renderSql never touches the DataSource
        jooq = new JooqTransactionSearchAdapter(DSL.using(SQLDialect.POSTGRES)); // connection-less DSLContext
        mybatis = new MybatisTransactionSearchAdapter(null);     // renderSql never touches the mapper
    }

    @Benchmark
    public String jdbcStringAssembly() {
        return jdbc.renderSql(criteria);
    }

    @Benchmark
    public String jooqAstRender() {
        return jooq.renderSql(criteria);
    }

    @Benchmark
    public String mybatisDynamicSqlRender() {
        return mybatis.renderSql(criteria);
    }
}
