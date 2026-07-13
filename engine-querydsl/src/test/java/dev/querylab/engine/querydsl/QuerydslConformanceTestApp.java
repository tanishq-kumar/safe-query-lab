package dev.querylab.engine.querydsl;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(QuerydslEngineConfiguration.class)
class QuerydslConformanceTestApp {
}
