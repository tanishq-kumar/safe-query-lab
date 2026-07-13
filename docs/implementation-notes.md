# Implementation Notes

This page pulls the high-signal inline notes into one place. The source comments
remain the source of truth; use this as a guided map before opening the code.

## Safety Model

The project demonstrates three separate injection surfaces:

- Values are always bound parameters.
- Dynamic `ORDER BY` is built only from enum switches, because identifiers cannot
  be bound as parameters.
- User text inside `LIKE` patterns is escaped so `%`, `_`, and the escape
  character are treated literally.

Primary sources:

- `query-common/src/main/java/dev/querylab/common/search/SortKey.java`
- `query-common/src/main/java/dev/querylab/common/search/LikeEscaper.java`
- `engine-jdbc/src/main/java/dev/querylab/engine/jdbc/JdbcTransactionSearchAdapter.java`

## Joins Across Tables

Each transaction has a mandatory `account` (INNER JOIN) and an optional `merchant`
(LEFT JOIN). The engines project `accountRiskRating` and the nullable
`merchantName`/`merchantCategory`/`merchantCountry` into the domain record, and
support filters on `accountRiskRating`, `merchantCategory`, `merchantCountry`.

The interesting differences are in *how* each engine follows the relationship:
raw JOIN SQL with qualified columns (JDBC), `@ManyToOne` associations traversed by
Criteria paths (JPA Specifications) or QueryDSL `tx.account.*` paths, a join graph
over the generated tables reused for both data and count queries (jOOQ), and the
`.join(table, on(...))` DSL with table aliases (MyBatis). A predicate on the
merchant LEFT JOIN behaves as an inner filter — rows with no merchant fail the
equality — which the conformance suite pins down alongside the null projection.

## Engine Notes

### JPA Specifications

`findAll(spec, pageable)` gives data and count queries from one Specification.
That is convenient, but the derived count query is the classic trap: distinct
queries and fetch joins can break it. This repo is safe because v1 is
single-table.

Source: `engine-jpa-specifications/src/main/java/dev/querylab/engine/jpa/JpaSpecificationSearchAdapter.java`

### QueryDSL

Predicates accumulate in `BooleanBuilder`, and generated Q-types make paths
compile-checked. The count query is explicit because QueryDSL 5 deprecated
`fetchResults()` and `fetchCount()` for arbitrary queries.

QueryDSL does not escape wildcard text for this use case, so it still uses the
shared `LikeEscaper`.

Source: `engine-querydsl/src/main/java/dev/querylab/engine/querydsl/QuerydslTransactionSearchAdapter.java`

### jOOQ

The generated metamodel comes from the real Flyway schema at build time. A
column rename breaks codegen/compile instead of production.

`DSL.noCondition()` is used as the neutral predicate, and the same `Condition`
object is reused for data and count queries. jOOQ's `containsIgnoreCase`
handles wildcard escaping internally.

Sources:

- `engine-jooq/pom.xml`
- `engine-jooq/src/main/java/dev/querylab/engine/jooq/JooqTransactionSearchAdapter.java`

### MyBatis Dynamic SQL

The `...WhenPresent` condition family is the main idea: null values do not
render, so the dynamic WHERE clause avoids manual if-statements.

The shared `WhereApplier` keeps the data and count query predicates identical.
Empty criteria require an explicit opt-in with
`setNonRenderingWhereClauseAllowed(true)`, which is a useful library-level
safety check.

Source: `engine-mybatis/src/main/java/dev/querylab/engine/mybatis/MybatisTransactionSearchAdapter.java`

### Raw JDBC

The JDBC adapter makes the fundamentals visible: SQL fragments and bind values
grow together, collections become one placeholder per element, and sort columns
come only from enum switches.

It uses two queries for pagination: one count query and one data query sharing
the same WHERE builder.

Source: `engine-jdbc/src/main/java/dev/querylab/engine/jdbc/JdbcTransactionSearchAdapter.java`

## Conformance Suite

The conformance suite is a test-fixture library consumed by every engine. It
starts one PostgreSQL Testcontainer per module JVM, applies the same Flyway
migration used by the API and jOOQ codegen, seeds deterministic data, and runs
all engines against the same behavioral contract.

The reference implementation is plain Java streams over the expected dataset.
That makes expected results derived, not hand-counted.

Important determinism choices:

- No `Instant.now()` or random UUIDs in seed data.
- Amounts are normalized to scale 4 to match `NUMERIC(19,4)`.
- Timestamps are truncated to PostgreSQL `timestamptz` precision.
- Every sort appends `id ASC` as the final tiebreak.

Sources:

- `query-conformance/src/main/java/dev/querylab/conformance/ConformancePostgres.java`
- `query-conformance/src/main/java/dev/querylab/conformance/ReferencePortAdapter.java`
- `query-conformance/src/main/java/dev/querylab/conformance/SeedData.java`
- `query-conformance/src/main/java/dev/querylab/conformance/AbstractTransactionSearchConformanceTest.java`

## Benchmarks

Benchmarks run outside Spring and use a larger deterministic PostgreSQL dataset.
The parent process starts the container and passes JDBC coordinates into forked
JMH JVMs through system properties, because forked benchmark JVMs cannot see the
parent process's Testcontainers objects.

Run with:

```bash
./mvnw -pl query-benchmarks exec:exec
```

Source: `query-benchmarks/src/main/java/dev/querylab/bench/BenchmarkRunner.java`

## Local Podman

For local machines without Docker Desktop, `scripts/mvn-podman` discovers the
Podman socket and exports the Docker-compatible environment variables
Testcontainers expects.

Run with:

```bash
./scripts/mvn-podman -B verify
```

Source: `scripts/mvn-podman`
