-- Second table + relationship, so the search has to follow data across joins:
--   transactions -> account   (mandatory, INNER JOIN)
--   transactions -> merchant  (optional,  LEFT JOIN — merchant_id is nullable)
-- Every engine now projects joined columns into the domain record and can
-- filter on them; the conformance suite proves all five stay identical.

CREATE TABLE merchant (
    id       UUID PRIMARY KEY,
    name     TEXT NOT NULL,
    category TEXT NOT NULL,
    country  CHAR(2) NOT NULL
);

-- Nullable on purpose: internal transfers / adjustments have no merchant,
-- which is what exercises the LEFT JOIN and null-projection paths.
ALTER TABLE transactions ADD COLUMN merchant_id UUID REFERENCES merchant (id);

CREATE INDEX idx_tx_merchant ON transactions (merchant_id);
CREATE INDEX idx_merchant_category ON merchant (category);
