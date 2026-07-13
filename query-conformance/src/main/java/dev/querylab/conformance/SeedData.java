package dev.querylab.conformance;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static dev.querylab.common.model.TransactionStatus.CANCELLED;
import static dev.querylab.common.model.TransactionStatus.COMPLETED;
import static dev.querylab.common.model.TransactionStatus.FAILED;
import static dev.querylab.common.model.TransactionStatus.PENDING;
import static dev.querylab.common.model.TransactionType.CREDIT;
import static dev.querylab.common.model.TransactionType.DEBIT;

/**
 * The deterministic dataset behind every conformance run: ~20 hand-crafted rows
 * (each a named constant so tests can assert exact membership) plus 130 rows from
 * a fixed-seed generator. No {@code Instant.now()}, no random UUIDs — the same
 * bytes on every machine, every run.
 *
 * <p>{@link #EXPECTED} is the full dataset in memory, which is what lets the
 * {@link ReferencePortAdapter} compute expected results in plain Java streams:
 * behavioral equivalence is derived, never hand-counted.
 *
 * <p>Determinism details that matter:
 * <ul>
 *   <li>Amounts are normalized to scale 4 to match NUMERIC(19,4) round-trips,
 *       because {@code BigDecimal.equals} is scale-sensitive and the suite
 *       compares whole {@link Transaction} records.</li>
 *   <li>Timestamps are truncated to microseconds — timestamptz precision.</li>
 *   <li>UUIDs are built from small positive longs so Java's signed
 *       {@code UUID.compareTo} and Postgres's unsigned byte order agree
 *       (the reference implementation still compares hex strings, which is
 *       always Postgres-equivalent).</li>
 * </ul>
 */
public final class SeedData {

    public static final UUID ACCOUNT_MAIN = new UUID(0xACC0, 1);
    public static final UUID ACCOUNT_SECONDARY = new UUID(0xACC0, 2);
    /** Has exactly one transaction ({@link #TX_SINGLE_ACCOUNT}). */
    public static final UUID ACCOUNT_SINGLE = new UUID(0xACC0, 3);

    /** Anchor for all generated timestamps; never use Instant.now() in seed or tests. */
    public static final Instant BASE = Instant.parse("2026-01-15T12:00:00Z");
    /** Half-open range used by the boundary tests: [RANGE_FROM, RANGE_TO). */
    public static final Instant RANGE_FROM = Instant.parse("2026-01-10T00:00:00Z");
    public static final Instant RANGE_TO = Instant.parse("2026-01-14T00:00:00Z");

    // --- Hand-crafted rows: one per edge case the suite asserts on by name ---
    public static final Transaction TX_BOUNDARY_LOW =
            tx(1, ACCOUNT_MAIN, "99.9999", "USD", COMPLETED, DEBIT, "Boundary low payment ref 900", "Acme Corp", BASE.minus(1, ChronoUnit.HOURS));
    public static final Transaction TX_BOUNDARY_EXACT =
            tx(2, ACCOUNT_MAIN, "100.0000", "USD", COMPLETED, DEBIT, "Boundary exact payment ref 901", "Acme Corp", BASE.minus(2, ChronoUnit.HOURS));
    public static final Transaction TX_BOUNDARY_HIGH =
            tx(3, ACCOUNT_MAIN, "100.0001", "USD", COMPLETED, DEBIT, "Boundary high payment ref 902", "Acme Corp", BASE.minus(3, ChronoUnit.HOURS));
    public static final Transaction TX_ZERO =
            tx(4, ACCOUNT_MAIN, "0.0000", "EUR", PENDING, CREDIT, "Zero amount adjustment", "Internal", BASE.minus(4, ChronoUnit.HOURS));
    public static final Transaction TX_NEGATIVE =
            tx(5, ACCOUNT_MAIN, "-25.5000", "USD", COMPLETED, CREDIT, "Refund reversal ref 903", "Acme Corp", BASE.minus(5, ChronoUnit.HOURS));
    public static final Transaction TX_NULL_COUNTERPARTY =
            tx(6, ACCOUNT_SECONDARY, "42.0000", "USD", PENDING, DEBIT, "Card hold no counterparty yet", null, BASE.minus(6, ChronoUnit.HOURS));
    /** created_at exactly at RANGE_FROM — must be INCLUDED by the half-open range. */
    public static final Transaction TX_AT_FROM =
            tx(7, ACCOUNT_MAIN, "10.0000", "USD", COMPLETED, DEBIT, "Exactly at range start", "Acme Corp", RANGE_FROM);
    /** created_at exactly at RANGE_TO — must be EXCLUDED by the half-open range. */
    public static final Transaction TX_AT_TO =
            tx(8, ACCOUNT_MAIN, "11.0000", "USD", COMPLETED, DEBIT, "Exactly at range end", "Acme Corp", RANGE_TO);
    public static final Transaction TX_COFFEE =
            tx(9, ACCOUNT_SECONDARY, "4.5000", "USD", COMPLETED, DEBIT, "Coffee SHOP purchase", "Blue Bottle", BASE.minus(7, ChronoUnit.HOURS));
    /** The only row whose description contains a LITERAL "100%". */
    public static final Transaction TX_PERCENT =
            tx(10, ACCOUNT_MAIN, "55.0000", "USD", COMPLETED, DEBIT, "Discount 100%_done applied", "Retail GmbH", BASE.minus(8, ChronoUnit.HOURS));
    /** Wildcard decoy: contains "100" but not "100%" — an unescaped '%' would match it. */
    public static final Transaction TX_HUNDRED =
            tx(11, ACCOUNT_MAIN, "60.0000", "USD", COMPLETED, DEBIT, "Invoice 100 dollars flat", "Retail GmbH", BASE.minus(9, ChronoUnit.HOURS));
    /** The only row containing a LITERAL "_case". */
    public static final Transaction TX_UNDERSCORE =
            tx(12, ACCOUNT_MAIN, "61.0000", "USD", COMPLETED, DEBIT, "snake_case_import batch", "ETL Systems", BASE.minus(10, ChronoUnit.HOURS));
    /** Underscore decoy: " case" would match "_case" if '_' were left unescaped. */
    public static final Transaction TX_SPACE_CASE =
            tx(13, ACCOUNT_MAIN, "62.0000", "USD", COMPLETED, DEBIT, "test case sensitivity row", "QA Guild", BASE.minus(11, ChronoUnit.HOURS));
    public static final Transaction TX_UNICODE =
            tx(14, ACCOUNT_SECONDARY, "70.0000", "EUR", COMPLETED, CREDIT, "Übertragung nach München", "Bank AG", BASE.minus(12, ChronoUnit.HOURS));
    public static final Transaction TX_JPY =
            tx(15, ACCOUNT_SECONDARY, "5000.0000", "JPY", FAILED, DEBIT, "Tokyo settlement ref 904", "Nippon KK", BASE.minus(13, ChronoUnit.HOURS));
    public static final Transaction TX_CANCELLED =
            tx(16, ACCOUNT_SECONDARY, "15.0000", "EUR", CANCELLED, DEBIT, "Cancelled subscription ref 905", "SaaS Co", BASE.minus(14, ChronoUnit.HOURS));
    public static final Transaction TX_SINGLE_ACCOUNT =
            tx(17, ACCOUNT_SINGLE, "33.0000", "USD", COMPLETED, DEBIT, "Only row for the single account", "Solo LLC", BASE.minus(15, ChronoUnit.HOURS));
    // Three rows with IDENTICAL created_at and IDENTICAL amount: without the id
    // tiebreak, their relative order is unstable and pagination flakes.
    public static final Transaction TX_DUP_A =
            tx(18, ACCOUNT_MAIN, "77.0000", "USD", PENDING, DEBIT, "Duplicate sort key row A", "Dup Inc", BASE.minus(16, ChronoUnit.HOURS));
    public static final Transaction TX_DUP_B =
            tx(19, ACCOUNT_MAIN, "77.0000", "USD", PENDING, DEBIT, "Duplicate sort key row B", "Dup Inc", BASE.minus(16, ChronoUnit.HOURS));
    public static final Transaction TX_DUP_C =
            tx(20, ACCOUNT_MAIN, "77.0000", "USD", PENDING, DEBIT, "Duplicate sort key row C", "Dup Inc", BASE.minus(16, ChronoUnit.HOURS));

    /** The complete dataset — hand-crafted rows first, then 130 generated ones. */
    public static final List<Transaction> EXPECTED = buildAll();

    private SeedData() {
    }

    private static List<Transaction> buildAll() {
        List<Transaction> all = new ArrayList<>(List.of(
                TX_BOUNDARY_LOW, TX_BOUNDARY_EXACT, TX_BOUNDARY_HIGH, TX_ZERO, TX_NEGATIVE,
                TX_NULL_COUNTERPARTY, TX_AT_FROM, TX_AT_TO, TX_COFFEE, TX_PERCENT,
                TX_HUNDRED, TX_UNDERSCORE, TX_SPACE_CASE, TX_UNICODE, TX_JPY,
                TX_CANCELLED, TX_SINGLE_ACCOUNT, TX_DUP_A, TX_DUP_B, TX_DUP_C));

        Random random = new Random(42);
        String[] currencies = {"USD", "EUR", "JPY"};
        String[] words = {"invoice", "transfer", "subscription", "payroll", "refund"};
        String[] counterparties = {"Acme Corp", "Globex", "Initech", "Umbrella", "Wayne Ent"};
        TransactionStatus[] statuses = TransactionStatus.values();
        TransactionType[] types = TransactionType.values();

        for (int i = 0; i < 130; i++) {
            int n = 1000 + i;
            UUID account = random.nextBoolean() ? ACCOUNT_MAIN : ACCOUNT_SECONDARY;
            BigDecimal amount = BigDecimal.valueOf(100 + random.nextInt(99_900), 2).setScale(4);
            Instant createdAt = BASE
                    .minus(random.nextInt(40), ChronoUnit.DAYS)
                    .minusSeconds(random.nextInt(86_400))
                    .truncatedTo(ChronoUnit.MICROS);
            all.add(new Transaction(
                    new UUID(0x7A, n),
                    account,
                    amount,
                    currencies[random.nextInt(currencies.length)],
                    statuses[random.nextInt(statuses.length)],
                    types[random.nextInt(types.length)],
                    capitalize(words[random.nextInt(words.length)]) + " payment ref " + n,
                    random.nextInt(7) == 0 ? null : counterparties[random.nextInt(counterparties.length)],
                    createdAt));
        }
        return List.copyOf(all);
    }

    private static Transaction tx(int n, UUID account, String amount, String currency,
                                  TransactionStatus status, TransactionType type,
                                  String description, String counterparty, Instant createdAt) {
        return new Transaction(new UUID(0x7A, n), account, new BigDecimal(amount).setScale(4),
                currency, status, type, description, counterparty,
                createdAt.truncatedTo(ChronoUnit.MICROS));
    }

    private static String capitalize(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /** Batch-inserts accounts and all transactions. Idempotence is the caller's concern. */
    public static void insertAll(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement accounts = connection.prepareStatement(
                    "INSERT INTO account (id, name, risk_rating) VALUES (?, ?, ?)")) {
                insertAccount(accounts, ACCOUNT_MAIN, "Main Checking", "LOW");
                insertAccount(accounts, ACCOUNT_SECONDARY, "Secondary Savings", "MEDIUM");
                insertAccount(accounts, ACCOUNT_SINGLE, "Dormant Account", "HIGH");
                accounts.executeBatch();
            }
            try (PreparedStatement txs = connection.prepareStatement("""
                    INSERT INTO transactions
                      (id, account_id, amount, currency, status, type, description, counterparty, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                for (Transaction t : EXPECTED) {
                    txs.setObject(1, t.id());
                    txs.setObject(2, t.accountId());
                    txs.setBigDecimal(3, t.amount());
                    txs.setString(4, t.currency());
                    txs.setString(5, t.status().name());
                    txs.setString(6, t.type().name());
                    txs.setString(7, t.description());
                    txs.setString(8, t.counterparty());
                    // JDBC 4.2 defines OffsetDateTime, not Instant, for timestamptz.
                    txs.setObject(9, t.createdAt().atOffset(ZoneOffset.UTC));
                    txs.addBatch();
                }
                txs.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed conformance dataset", e);
        }
    }

    private static void insertAccount(PreparedStatement statement, UUID id, String name,
                                      String riskRating) throws SQLException {
        statement.setObject(1, id);
        statement.setString(2, name);
        statement.setString(3, riskRating);
        statement.addBatch();
    }
}
