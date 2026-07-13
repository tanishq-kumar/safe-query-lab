package dev.querylab.bench;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.engine.jdbc.JdbcTransactionSearchAdapter;
import dev.querylab.engine.jooq.JooqTransactionSearchAdapter;
import dev.querylab.engine.jpa.JpaSpecificationSearchAdapter;
import dev.querylab.engine.jpa.TransactionEntityRepository;
import dev.querylab.engine.mybatis.MybatisTransactionSearchAdapter;
import dev.querylab.engine.mybatis.TransactionMapper;
import dev.querylab.engine.querydsl.QuerydslTransactionSearchAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Map;

/**
 * Builds the requested engine inside the forked benchmark JVM. JMH forks do not
 * inherit the parent's objects (or its Testcontainers), so the JDBC coordinates
 * arrive as {@code -Dbench.jdbc.*} system properties set by {@link BenchmarkRunner}.
 *
 * <p>Deliberately no Spring anywhere: Hibernate boots from persistence.xml, the
 * Spring Data repository comes from a bare {@code JpaRepositoryFactory}, MyBatis
 * from a hand-built {@code Configuration} — so the numbers measure the query
 * stacks, not container startup or proxying.
 */
@State(Scope.Benchmark)
public class EngineState {

    @Param({"jdbc", "jpa", "querydsl", "jooq", "mybatis"})
    public String engine;

    private HikariDataSource dataSource;
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private TransactionSearchPort port;

    @Setup
    public void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(required("bench.jdbc.url"));
        config.setUsername(required("bench.jdbc.user"));
        config.setPassword(required("bench.jdbc.password"));
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);

        port = switch (engine) {
            case "jdbc" -> new JdbcTransactionSearchAdapter(dataSource);
            case "jooq" -> new JooqTransactionSearchAdapter(DSL.using(dataSource, SQLDialect.POSTGRES));
            case "mybatis" -> mybatisPort();
            case "jpa" -> new JpaSpecificationSearchAdapter(
                    new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(entityManager())
                            .getRepository(TransactionEntityRepository.class));
            case "querydsl" -> new QuerydslTransactionSearchAdapter(new JPAQueryFactory(entityManager()));
            default -> throw new IllegalArgumentException("Unknown engine " + engine);
        };
    }

    public TransactionSearchPort port() {
        return port;
    }

    private TransactionSearchPort mybatisPort() {
        Environment environment = new Environment("bench", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(TransactionMapper.class);
        SqlSessionManager sessionManager =
                SqlSessionManager.newInstance(new SqlSessionFactoryBuilder().build(configuration));
        // SqlSessionManager proxies mappers with open-session-per-invocation.
        return new MybatisTransactionSearchAdapter(sessionManager.getMapper(TransactionMapper.class));
    }

    private EntityManager entityManager() {
        if (entityManager == null) {
            entityManagerFactory = Persistence.createEntityManagerFactory("bench", Map.of(
                    "jakarta.persistence.jdbc.url", required("bench.jdbc.url"),
                    "jakarta.persistence.jdbc.user", required("bench.jdbc.user"),
                    "jakarta.persistence.jdbc.password", required("bench.jdbc.password")));
            entityManager = entityManagerFactory.createEntityManager();
        }
        return entityManager;
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        if (value == null) {
            throw new IllegalStateException(property + " not set — run via BenchmarkRunner, not the JMH main");
        }
        return value;
    }

    @TearDown
    public void tearDown() {
        if (entityManager != null) {
            entityManager.close();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
