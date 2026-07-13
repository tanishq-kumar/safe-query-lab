package dev.querylab.api;

import dev.querylab.engine.jooq.JooqEngineConfiguration;
import dev.querylab.engine.jpa.JpaEngineConfiguration;
import dev.querylab.engine.mybatis.MybatisEngineConfiguration;
import dev.querylab.engine.querydsl.QuerydslEngineConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * All five engines live in one application context simultaneously — engine
 * choice is per-request, not per-deployment, which is what makes side-by-side
 * comparison a curl away. Wiring is an explicit {@code @Import} list: no
 * cross-module classpath scanning to reverse-engineer.
 */
@SpringBootApplication
@Import({
        JdbcEngineConfiguration.class,
        JpaEngineConfiguration.class,
        QuerydslEngineConfiguration.class,
        JooqEngineConfiguration.class,
        MybatisEngineConfiguration.class,
})
public class QueryLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryLabApplication.class, args);
    }
}
