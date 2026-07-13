package dev.querylab.api;

import dev.querylab.conformance.ReferencePortAdapter;
import dev.querylab.conformance.SeedData;
import dev.querylab.common.search.TransactionSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end through HTTP: the conformance idea repeated at the outermost
 * layer. All five engines must return identical result sets for the same
 * request, and the two injection defenses must reject/neutralize hostile input.
 */
@SpringBootTest(classes = QueryLabApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiSmokeTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start(); // singleton across this module's JVM; Ryuk cleans up
    }

    private static boolean seeded;

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void seedOnce(@Autowired DataSource dataSource) {
        // after the context is up, so Flyway has already created the schema
        if (!seeded) {
            SeedData.insertAll(dataSource);
            seeded = true;
        }
    }

    private static final String FILTERS =
            "status=COMPLETED&status=PENDING&currency=USD&minAmount=1&q=ref&sortBy=amount&sortDir=asc&size=200";

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "jpa", "querydsl", "jooq", "mybatis"})
    void everyEngineAnswersIdentically(String engine) {
        long expected = new ReferencePortAdapter()
                .search(TransactionSearchCriteria.unfiltered()).totalElements();

        ResponseEntity<Map<String, Object>> response = get(
                "/api/transactions/search?engine=" + engine + "&size=200");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("engine")).isEqualTo(engine);
        assertThat(((Number) response.getBody().get("totalElements")).longValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "jpa", "querydsl", "jooq", "mybatis"})
    void filteredResultsAgreeAcrossEnginesOverHttp(String engine) {
        // Compare each engine's HTTP answer to the in-memory reference.
        long expected = new ReferencePortAdapter().search(TransactionSearchCriteria.builder()
                .statuses(dev.querylab.common.model.TransactionStatus.COMPLETED,
                        dev.querylab.common.model.TransactionStatus.PENDING)
                .currency("USD")
                .minAmount(new BigDecimal("1"))
                .descriptionContains("ref")
                .size(200)
                .build()).totalElements();

        ResponseEntity<Map<String, Object>> response = get(
                "/api/transactions/search?engine=" + engine + "&" + FILTERS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) response.getBody().get("totalElements")).longValue()).isEqualTo(expected);
    }

    @Test
    void sortInjectionIsRejectedWith400() {
        ResponseEntity<Map<String, Object>> response = get(
                "/api/transactions/search?sortBy=amount;DROP TABLE transactions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains("Unknown sort field");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void likeWildcardsAreNeutralizedOverHttp() {
        // The raw '%' rides a URI-template variable so it is encoded exactly once
        // (a literal "%25" in the template string would be encoded a second time).
        ResponseEntity<Map<String, Object>> response = (ResponseEntity) http.getForEntity(
                "/api/transactions/search?q={q}", Map.class, "100%");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Only the literal "100%" row — not every description containing "100".
        assertThat(((Number) response.getBody().get("totalElements")).longValue()).isEqualTo(1);
    }

    @Test
    void unknownEngineListsTheValidOnes() {
        ResponseEntity<Map<String, Object>> response = get("/api/transactions/search?engine=hibernate6");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail"))
                .contains("jdbc", "jpa", "querydsl", "jooq", "mybatis");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "jooq", "mybatis"})
    void explainRendersSqlForEagerEngines(String engine) {
        ResponseEntity<Map<String, Object>> response = get(
                "/api/transactions/search/explain?engine=" + engine + "&status=COMPLETED&q=coffee");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("supported")).isEqualTo(true);
        assertThat(((String) response.getBody().get("sql")).toLowerCase()).contains("select", "where");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpa", "querydsl"})
    void explainIsHonestAboutHibernateEngines(String engine) {
        ResponseEntity<Map<String, Object>> response = get(
                "/api/transactions/search/explain?engine=" + engine);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("supported")).isEqualTo(false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map<String, Object>> get(String path) {
        return (ResponseEntity) http.getForEntity(path, Map.class);
    }
}
