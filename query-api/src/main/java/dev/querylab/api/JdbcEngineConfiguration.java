package dev.querylab.api;

import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.engine.jdbc.JdbcTransactionSearchAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Lives in the API module, not in engine-jdbc: that module is deliberately
 * 100% java.sql with zero framework dependencies, so its Spring wiring is the
 * consumer's job.
 */
@Configuration
public class JdbcEngineConfiguration {

    @Bean(name = "jdbc")
    public TransactionSearchPort jdbcSearchPort(DataSource dataSource) {
        return new JdbcTransactionSearchAdapter(dataSource);
    }
}
