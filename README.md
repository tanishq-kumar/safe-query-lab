# safe-query-lab

**The same production feature built five ways — and proven behaviorally identical.**

[![CI](https://github.com/tanishq-kumar/safe-query-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/tanishq-kumar/safe-query-lab/actions/workflows/ci.yml)

Every enterprise backend eventually grows this screen: *search transactions by any
combination of status, amount range, date range, account, currency, and free text*.
The query can't be written ahead of time — it has to be assembled from whatever
filters the user happened to send. Assembling SQL from user input is also exactly
how injection vulnerabilities are born.

For a source-guided tour of the inline implementation notes, see
[docs/implementation-notes.md](docs/implementation-notes.md).

This repo implements that one feature — dynamic, safe query generation — with the
five mainstream Java techniques, side by side:

| Module | Technique | One-line character |
|---|---|---|
| [engine-jdbc](engine-jdbc) | Raw JDBC + `PreparedStatement` | The fundamentals everything else abstracts |
| [engine-jpa-specifications](engine-jpa-specifications) | Spring Data JPA Specifications (Criteria API) | Composable predicate objects on the ORM you already have |
| [engine-querydsl](engine-querydsl) | QueryDSL (JPA backend) | Fluent, compile-checked queries via generated Q-types |
| [engine-jooq](engine-jooq) | jOOQ | SQL as a typesafe Java DSL, codegen'd from the real schema |
| [engine-mybatis](engine-mybatis) | MyBatis Dynamic SQL | SQL-first, with null-eliding conditions in the library |

All five implement one port —
`TransactionSearchPort.search(TransactionSearchCriteria): SearchResult<Transaction>`
— and all five must pass the **same conformance suite** (30 tests against a real
PostgreSQL via Testcontainers) before they may exist. Equivalence isn't claimed;
it's derived: expected results are computed by an in-memory reference
implementation over a deterministic 150-row dataset and compared record-for-record,
in order, against every engine.

```
safe-query-lab
├── query-common/               the contract: domain record, criteria DTO, port, LikeEscaper, Flyway schema
├── query-conformance/          the proof: seed data, reference impl, abstract 30-test suite, shared PG container
├── engine-jdbc/                ┐
├── engine-jpa-specifications/  │
├── engine-querydsl/            │ five interchangeable implementations
├── engine-jooq/                │
├── engine-mybatis/             ┘
├── query-api/                  Spring Boot API: ?engine= switches implementations per request
└── query-benchmarks/           JMH: end-to-end latency + pure construction overhead
```

## Quickstart

Requirements: JDK 21+, Docker (or Podman with the Docker socket enabled).

```bash
./mvnw verify                                   # build everything, run all conformance suites
./mvnw -pl engine-jooq -am test                 # prove any single engine on its own
./mvnw -pl query-api spring-boot:test-run       # boot the API on a throwaway, seeded Postgres
```

Using Podman locally:

```bash
podman machine init                             # first time only, if no machine exists
podman machine start
./scripts/mvn-podman -B verify
./scripts/mvn-podman -pl engine-jooq -am generate-sources
```

Then compare engines live:

```bash
curl 'localhost:8080/api/transactions/search?engine=jooq&status=COMPLETED&minAmount=50&q=ref&sortBy=amount'
curl 'localhost:8080/api/transactions/search/explain?engine=jooq&status=COMPLETED&q=coffee'
open http://localhost:8080/swagger-ui.html
```

Same request, five engines, identical results — only `engine=` changes.
(For `spring-boot:test-run` after a fresh clone, run `./mvnw install -DskipTests` once so
sibling modules resolve.)

## The three injection surfaces (and why "use PreparedStatement" isn't enough)

**1. Values.** The one everybody knows. Every engine here binds values; none
concatenates them. Table stakes.

**2. ORDER BY.** You *cannot* bind an identifier — `ORDER BY ?` binds a constant,
not a column. Any dynamic sort must splice a string into the SQL, so the defense
has to happen earlier. Here, raw sort input dies at the API boundary
(`SortKey.fromParam` → HTTP 400), and inside every engine the clause is built by
a `switch` over the enum returning compile-time constants. Injection isn't
*validated away* — it's **unrepresentable**:

```bash
$ curl 'localhost:8080/api/transactions/search?sortBy=amount;DROP%20TABLE%20transactions'
{"status":400, "detail":"Unknown sort field: 'amount;DROP TABLE transactions'. Allowed: [createdAt, amount, id]"}
```

**3. LIKE wildcards.** A bound pattern still interprets `%`, `_` and the escape
character. Search for `100%` unescaped and it degenerates into "contains 100" —
soft injection that silently corrupts results (and `%%%` makes a fine DoS
against a big table). The shared `LikeEscaper` neutralizes user text, and the
conformance suite proves it with decoy rows:

```bash
$ curl 'localhost:8080/api/transactions/search?q=100%25'   # one row: the literal "100%_done"
```

Engine trivia the suite surfaced: jOOQ escapes wildcards internally (with `!`),
QueryDSL and everything else do not. Same test, different code paths, identical
behavior — which is the point of conformance testing.

## What actually differs between the engines

| | JDBC | JPA Specs | QueryDSL | jOOQ | MyBatis Dynamic |
|---|---|---|---|---|---|
| Compile-time safety | none (strings) | property names as strings¹ | full (generated Q-types) | full (generated from live schema) | column names as typed constants² |
| Codegen required | no | no | apt on your entities | build-time DB (Docker) | no (hand-written support class) |
| Dynamic predicate idiom | `List<String>` + params in lockstep | `Specification`-per-filter, `allOf()` | `BooleanBuilder` | `Condition` + `noCondition()` | `isXxxWhenPresent()` |
| Count query | 2nd query, shared WHERE builder | derived automatically³ | explicit 2nd query⁴ | same `Condition` object reused | shared `WhereApplier` |
| SQL visibility before execution | total (you wrote it) | no (Hibernate, at flush time) | no (Hibernate) | total (`getSQL()`) | total (rendered provider) |
| Wildcard escaping | yours (`LikeEscaper`) | yours | yours | built-in (`!`) | yours |
| Returns entities or rows | rows you map | managed entities | managed entities | records you map | rows you map |
| Boilerplate per new filter | ~4 lines | ~5 lines | ~2 lines | ~2 lines | ~1 line |

¹ `hibernate-jpamodelgen` would fix this; skipped as a documented trade-off.
² Typed, but nothing checks them against the real schema until runtime — jOOQ's codegen is exactly that check, moved to build time.
³ And silently breaks with `distinct`/fetch-joins — the classic Specifications trap (see stretch goals).
⁴ `fetchResults()`/`fetchCount()` are deprecated because they wrap arbitrary queries in `count(*)` and break with `groupBy`.

### The `/explain` endpoint makes it tangible

`GET /api/transactions/search/explain?engine=…` returns the SQL an engine *would*
run. JDBC's is a clean parameterized string; jOOQ's shows its runtime `replace(...)`
escaping pipeline; MyBatis shows the rendered provider. The two Hibernate-backed
engines honestly report `supported: false` — SQL visibility is an architectural
property, not a feature flag, and that asymmetry is itself the lesson.

## The conformance suite (the part that makes this more than a demo)

- **One shared PostgreSQL Testcontainer per module JVM** (singleton pattern, not
  `@Container` — which would restart the DB per class), migrated by the same
  Flyway scripts the app and jOOQ codegen use. One schema source of truth.
- **Deterministic seed**: ~20 hand-crafted rows, each a named constant targeting
  an edge (amounts `99.9999/100.0000/100.0001` around a boundary, timestamps
  exactly *at* the half-open range edges, `NULL` counterparty, literal `%`/`_`
  decoys, ü/Ü content, three rows with identical sort keys) + 130 rows from
  `new Random(42)`. No `Instant.now()` anywhere.
- **A reference implementation** (`ReferencePortAdapter`, ~40 lines of Streams)
  defines the semantics executable-y; the suite ran green against it before any
  SQL existed. Every engine assertion is "matches the reference, exactly,
  in order" plus absolute assertions on the named rows.
- **Determinism contract**: every engine appends `id ASC` as the final sort
  tiebreak. Without it, three seeded rows with identical `(amount, created_at)`
  make pagination nondeterministic — there's a test that walks them page by page.

## Benchmarks (JMH)

```bash
./mvnw -pl query-benchmarks exec:exec        # ~6 min; writes results/jmh.json
```

Two measurements against a 100k-row Postgres, no Spring anywhere (Hibernate is
bootstrapped from `persistence.xml`, the Spring Data repository from a bare
`JpaRepositoryFactory`, MyBatis from a hand-built `Configuration`):

- **EndToEndSearchBenchmark** — build + execute + map, `SampleTime` mode,
  scenarios `simple` (1 predicate) and `complex` (8 predicates + IN + ILIKE +
  sort + page 3).
- **QueryConstructionBenchmark** — SQL assembly only, no database, for the three
  engines whose SQL is separable (JDBC, jOOQ, MyBatis).

Results from this machine (Apple Silicon, Podman VM — trends, not gospel):

End-to-end, `SampleTime` (lower is better):

| engine | simple (µs/op) | complex (µs/op) |
|---|---:|---:|
| jdbc | 1 223 ± 9 | 61 957 ± 720 |
| jooq | 1 490 ± 33 | 117 884 ± 14 900 |
| mybatis | 1 791 ± 18 | 28 467 ± 1 183 |
| querydsl | 3 445 ± 118 | 32 068 ± 569 |
| jpa | 3 665 ± 50 | 30 290 ± 438 |

Construction only, `AverageTime`:

| | simple (ns/op) | complex (ns/op) |
|---|---:|---:|
| JDBC string assembly | 30 | 132 |
| jOOQ AST render | 2 532 | 6 127 |
| MyBatis Dynamic SQL render | 3 379 | 7 321 |

Two honest readings, both interview-worthy:

- **Simple scenario**: raw JDBC wins and Hibernate-backed engines pay ~2.5 µs of
  entity-management tax per call. Real, measurable — and irrelevant next to any
  business logic.
- **Complex scenario, the surprise**: JDBC and jOOQ are *slower* than the ORMs.
  Not framework overhead — **query-plan divergence**. Those two engines use
  `ILIKE`, which the `gin_trgm_ops` index serves; the pattern `%ref%` matches a
  third of the table, so the bitmap index scan + recheck loses badly to the
  plain seq scan the `lower() LIKE` engines get. The API you pick shapes the SQL,
  the SQL shapes the plan, and the plan is what you pay for. `EXPLAIN ANALYZE`
  beats intuition, every time.

**The headline finding**: query *construction* costs nanoseconds-to-microseconds
while the *round trip* costs hundreds of microseconds to milliseconds. "DSL
overhead" is noise in any I/O-bound workload — pick your abstraction for safety
and maintainability, not for construction speed. Differences between engines
end-to-end are dominated by mapping strategy (managed entities vs. plain rows)
and driver behavior, not by the WHERE-clause builders.

JMH + Testcontainers gotcha worth knowing: forked measurement JVMs inherit
neither the parent's containers nor its objects, so the runner starts one
Postgres in the parent process and passes JDBC coordinates to forks via
`jvmArgsAppend`. And it must be launched with `exec:exec`, not `exec:java` —
JMH's forks are spawned with `java.class.path`, which inside Maven's own JVM is
just the classworlds launcher.

## Field notes — gotchas actually hit while building this

Every one of these bit during development; the fix is in the code with a comment.

1. **QueryDSL's `jakarta` classifier, twice** — needed on both `querydsl-apt`
   (annotation processor path) and `querydsl-jpa` (runtime). And a subtlety:
   Spring Boot's BOM manages the classifier-*less* artifact, and
   `dependencyManagement` does **not** apply across classifiers, so the jakarta
   artifact needs an explicit version. Never use the abandoned `apt-maven-plugin`.
2. **Q-class naming** — the generated static field is named after the Java class
   (`QTransactionEntity.transactionEntity`), not the `@Entity(name=...)` you set
   to avoid persistence-unit clashes.
3. **Two `@Entity` classes, one persistence unit** — Hibernate entity names
   default to the simple class name; both JPA engines mapping `transactions`
   would collide with `DuplicateMappingException` in the API app. Explicit
   `@Entity(name = "QuerydslTransactionEntity")` fixes it.
4. **MyBatis has no UUID `TypeHandler`** — and explicit `@Result` mappings
   refuse to fall back to the unknown-type handler: mapper parsing fails with
   *"No typehandler found for property id"*. Ship a 20-line `BaseTypeHandler<UUID>`.
5. **MyBatis refuses to render an empty WHERE** — all-`WhenPresent` conditions
   with empty criteria throw `NonRenderingWhereClauseException` unless you opt in
   via `configureStatement(c -> c.setNonRenderingWhereClauseAllowed(true))`. It's
   a safety feature (accidental full-table UPDATEs die loudly) none of the other
   engines has.
6. **`Instant` vs JDBC 4.2** — the spec maps `OffsetDateTime`, not `Instant`, to
   `timestamptz`. Convert at every JDBC binding site; Hibernate and jOOQ
   (`javaTimeTypes`) handle it natively; MyBatis has a built-in JSR-310 handler.
7. **`BigDecimal.equals` is scale-sensitive** — `100.00 ≠ 100.0000` to a record's
   `equals`, and NUMERIC(19,4) comes back at scale 4. Seed data is normalized to
   scale 4 so whole-record comparisons work; comparisons in SQL and in the
   reference use `compareTo` semantics.
8. **Java `UUID.compareTo` ≠ Postgres uuid ordering** — Java compares two signed
   longs; Postgres compares bytes unsigned. Sorting by id would diverge for ids
   with a high bit set. The reference compares hex strings (always
   Postgres-equivalent); seeds use small positive longs.
9. **Non-ASCII case-insensitivity is a minefield** — Java's `toLowerCase`
   lowercases `Ü`; Postgres `lower()` under the C locale (alpine images) does
   not, and MyBatis' `isLikeCaseInsensitive` upper-cases in *Java* while other
   engines lower-case in *SQL*. The engines use SQL-side `lower()` uniformly and
   the suite keeps case-folding assertions ASCII (unicode content is still
   asserted byte-perfect round-trip).
10. **Testcontainers `@Container` restarts per class** — use the static-singleton
    pattern; one container per module JVM cut suite time massively.
11. **RestTemplate double-encodes literal `%25` in URI templates** — pass hostile
    strings as template *variables* in tests, or you're testing the wrong bytes.
12. **`transactions`, not `transaction`** — the singular is near-reserved and
    would need quoting in five different SQL layers.

## Which would I choose?

- **Already on Spring Data JPA, moderate dynamism** → **Specifications**. No new
  dependency, composable, testable; watch the count-query trap once joins appear.
- **Complex, SQL-shaped queries; reporting; window functions; want the compiler
  to catch schema drift** → **jOOQ**. The build-time codegen against real
  migrations is the strongest correctness guarantee in this repo. Licensing:
  OSS jOOQ covers open-source databases only.
- **JPA shop that wants fluent type-safety beyond Specifications** → **QueryDSL**.
  Mind the maintenance story (the OpenFeign fork is where the ecosystem energy is).
- **SQL-first team, DBA-reviewed queries, or MyBatis heritage** → **MyBatis
  Dynamic SQL**. The `WhenPresent` family is genuinely elegant for this problem.
- **Raw JDBC** → almost never for feature code, but everyone on the team should
  be able to write the safe version — it's what all the others compile down to,
  and it's the interview question.

## Stretch goals (deliberately out of v1 scope)

The `account` table and FK ship in the schema already; adding an
`account.riskRating` filter would demonstrate each engine's join story — and the
JPA Specifications count-query trap (`query.getResultType()` guard, fetch-join
vs. `EXISTS` subquery). Also interesting: keyset/seek pagination (the `id ASC`
tiebreak is the groundwork), `status = ANY(?)` array binding in the JDBC engine,
and Hibernate's `@JdbcTypeCode` route for native PG enums.

## IDE setup

Generated sources appear after the first build:

```bash
./mvnw -pl engine-jooq,engine-querydsl generate-sources   # jOOQ tables + Q-types
```

then mark `target/generated-sources/{jooq,annotations}` as generated-sources
roots if your IDE didn't.
