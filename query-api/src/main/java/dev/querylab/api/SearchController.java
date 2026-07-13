package dev.querylab.api;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transaction search", description = "One dynamic-filter contract, five interchangeable engines")
public class SearchController {

    private final EngineRegistry engines;
    private final SqlExplainer explainer;

    public SearchController(EngineRegistry engines, SqlExplainer explainer) {
        this.engines = engines;
        this.explainer = explainer;
    }

    @GetMapping("/search")
    @Operation(summary = "Search transactions with any combination of filters",
            description = "Every filter is optional and composes with AND. Select the backing "
                    + "implementation with ?engine=jdbc|jpa|querydsl|jooq|mybatis — same request, "
                    + "same response, five different SQL stacks.")
    public SearchResponse search(
            @ParameterObject SearchRequest request,
            @Parameter(description = "jdbc | jpa | querydsl | jooq | mybatis")
            @RequestParam(required = false) String engine) {
        TransactionSearchPort port = engines.get(engine);
        TransactionSearchCriteria criteria = request.toCriteria();

        long start = System.nanoTime();
        SearchResult<Transaction> result = port.search(criteria);
        long tookMillis = (System.nanoTime() - start) / 1_000_000;

        return SearchResponse.of(port.engineName(), tookMillis, result);
    }

    @GetMapping("/search/explain")
    @Operation(summary = "Show the SQL an engine would run for these filters, without executing it")
    public SqlExplainer.Explanation explain(
            @ParameterObject SearchRequest request,
            @RequestParam(required = false) String engine) {
        return explainer.explain(engines.get(engine), request.toCriteria());
    }
}
