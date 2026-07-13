package dev.querylab.engine.mybatis;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(MybatisEngineConfiguration.class)
class MybatisConformanceTestApp {
}
