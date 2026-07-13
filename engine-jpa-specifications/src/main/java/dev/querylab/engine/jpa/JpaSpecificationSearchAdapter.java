package dev.querylab.engine.jpa;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Technique 1: Spring Data JPA Specifications.
 *
 * <p>Pagination and counting come for free: {@code findAll(spec, pageable)}
 * runs the data query AND derives a count query from the same Specification.
 * That derivation is also this technique's classic trap — specifications that
 * use {@code query.distinct()} or add fetch-joins break the derived count
 * (guard with {@code query.getResultType()} checks). Safe here because v1 is
 * single-table; the README's join stretch goal walks through the trap.
 */
public class JpaSpecificationSearchAdapter implements TransactionSearchPort {

    private final TransactionEntityRepository repository;

    public JpaSpecificationSearchAdapter(TransactionEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        Page<TransactionEntity> page = repository.findAll(
                TransactionSpecifications.fromCriteria(criteria),
                PageRequest.of(criteria.page(), criteria.size(), sort(criteria)));

        return new SearchResult<>(
                page.map(TransactionEntity::toDomain).getContent(),
                page.getTotalElements(),
                criteria.page(),
                criteria.size());
    }

    @Override
    public String engineName() {
        return "jpa";
    }

    private static Sort sort(TransactionSearchCriteria criteria) {
        // Property names come from a switch over the whitelist enum — same
        // injection-unrepresentable pattern as every other engine, just with
        // JPA property paths instead of column names.
        String property = switch (criteria.sortBy()) {
            case CREATED_AT -> TransactionColumns.CREATED_AT;
            case AMOUNT -> TransactionColumns.AMOUNT;
            case ID -> TransactionColumns.ID;
        };
        Sort.Direction direction = switch (criteria.sortDirection()) {
            case ASC -> Sort.Direction.ASC;
            case DESC -> Sort.Direction.DESC;
        };
        Sort primary = Sort.by(direction, property);
        return criteria.sortBy() == SortKey.ID
                ? primary
                : primary.and(Sort.by(Sort.Direction.ASC, TransactionColumns.ID));
    }
}
