package dev.querylab.engine.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.querylab.common.search.TransactionSearchPort;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(basePackageClasses = TransactionEntity.class)
public class QuerydslEngineConfiguration {

    @Bean(name = "querydsl")
    public TransactionSearchPort querydslSearchPort(EntityManager entityManager) {
        // The injected EntityManager is Spring's shared, thread-safe proxy;
        // JPAQueryFactory holds it safely as a singleton.
        return new QuerydslTransactionSearchAdapter(new JPAQueryFactory(entityManager));
    }
}
