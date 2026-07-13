package dev.querylab.engine.mybatis;

import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.conformance.AbstractTransactionSearchConformanceTest;
import dev.querylab.conformance.ConformancePostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = MybatisConformanceTestApp.class)
class MybatisConformanceTest extends AbstractTransactionSearchConformanceTest {

    @Autowired
    @Qualifier("mybatis")
    private TransactionSearchPort port;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ConformancePostgres::jdbcUrl);
        registry.add("spring.datasource.username", ConformancePostgres::username);
        registry.add("spring.datasource.password", ConformancePostgres::password);
    }

    @Override
    protected TransactionSearchPort port() {
        return port;
    }
}
