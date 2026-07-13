package dev.querylab.engine.jpa;

/**
 * JPA property names in one place. The type-safe alternative is the annotation
 * processor {@code hibernate-jpamodelgen} (generates {@code TransactionEntity_}
 * metamodel classes); skipped here as a conscious trade-off — this project
 * already demonstrates codegen with jOOQ and QueryDSL, and a constants class
 * keeps this module's build plain.
 */
final class TransactionColumns {

    static final String ID = "id";
    static final String ACCOUNT_ID = "accountId";
    static final String AMOUNT = "amount";
    static final String CURRENCY = "currency";
    static final String STATUS = "status";
    static final String TYPE = "type";
    static final String DESCRIPTION = "description";
    static final String CREATED_AT = "createdAt";

    private TransactionColumns() {
    }
}
