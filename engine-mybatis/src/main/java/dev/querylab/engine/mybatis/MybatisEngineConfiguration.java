package dev.querylab.engine.mybatis;

import dev.querylab.common.search.TransactionSearchPort;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @MapperScan} lives HERE, on the engine's own configuration — the
 * classic cross-module pitfall is putting it on the Boot app class, whose
 * package-based scan never reaches a sibling module's mappers.
 */
@Configuration
@MapperScan(basePackageClasses = TransactionMapper.class)
public class MybatisEngineConfiguration {

    @Bean(name = "mybatis")
    public TransactionSearchPort mybatisSearchPort(TransactionMapper mapper) {
        return new MybatisTransactionSearchAdapter(mapper);
    }
}
