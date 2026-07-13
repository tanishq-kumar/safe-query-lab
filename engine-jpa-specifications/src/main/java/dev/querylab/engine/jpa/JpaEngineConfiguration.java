package dev.querylab.engine.jpa;

import dev.querylab.common.search.TransactionSearchPort;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Self-contained wiring for this engine: the API module {@code @Import}s this
 * class and gets the entity, the repository, and the port bean — no classpath
 * scanning reaching across modules. The bean NAME is the engine's public
 * identifier: the API selects engines from an injected
 * {@code Map<String, TransactionSearchPort>} keyed by bean name.
 */
@Configuration
@EntityScan(basePackageClasses = TransactionEntity.class)
@EnableJpaRepositories(basePackageClasses = TransactionEntityRepository.class)
public class JpaEngineConfiguration {

    @Bean(name = "jpa")
    public TransactionSearchPort jpaSearchPort(TransactionEntityRepository repository) {
        return new JpaSpecificationSearchAdapter(repository);
    }
}
