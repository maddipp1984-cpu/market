# TS1+TS2: TimescaleDB-Optimierung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hash-Partitionierung von 3 Hypertables entfernen und `first_date`/`last_date` in `ts_header` einfuegen, um die Overview-Query von UNION-ALL ueber 5 Tabellen auf einen einfachen SELECT zu reduzieren.

**Architecture:** Zwei SQL-Migrationen (014, 015), Anpassung von 7 Stored Procedures (Write + Delete), Java-Code fuer writeSimple()-Header-Update und vereinfachte Overview-Query.

**Tech Stack:** PostgreSQL/TimescaleDB, PL/pgSQL, jOOQ, Spring Boot

---

### Task 1: Migration 014 — Hash-Partitionierung entfernen

**Files:**
- Create: `sql/migrations/014_remove_hash_partitioning.sql`

- [ ] **Step 1: Migration-Datei erstellen**

```sql
-- sql/migrations/014_remove_hash_partitioning.sql
-- Entfernt Hash-Partitionierung von ts_values_day, ts_values_1h, ts_values_15min.
-- Single-Node: Hash erzeugt nur unnoetige Chunks ohne Performance-Vorteil.
-- ACHTUNG: Erfordert Downtime. Reihenfolge: day (klein) → 1h → 15min (gross).

BEGIN;

-- ============================================================
-- 1. ts_values_day: Hash(ts_id, 4) entfernen
-- ============================================================

-- Kompression deaktivieren
SELECT decompress_chunk(c, true)
FROM show_chunks('ts_values_day') c
WHERE EXISTS (
    SELECT 1 FROM timescaledb_information.chunks
    WHERE hypertable_name = 'ts_values_day'
      AND chunk_name = c::text
      AND is_compressed = true
);
SELECT remove_compression_policy('ts_values_day', if_exists => true);

-- Neue Tabelle ohne Hash
CREATE TABLE ts_values_day_new (
    ts_id    BIGINT NOT NULL,
    ts_date  DATE NOT NULL,
    value    DOUBLE PRECISION
);
SELECT create_hypertable('ts_values_day_new', by_range('ts_date', INTERVAL '1 year'));

-- Daten kopieren
INSERT INTO ts_values_day_new (ts_id, ts_date, value)
SELECT ts_id, ts_date, value FROM ts_values_day;

-- Swap
DROP TABLE ts_values_day;
ALTER TABLE ts_values_day_new RENAME TO ts_values_day;

-- Index + FK + Kompression
CREATE UNIQUE INDEX idx_day_pk ON ts_values_day (ts_id, ts_date);
ALTER TABLE ts_values_day ADD CONSTRAINT fk_day_header
    FOREIGN KEY (ts_id) REFERENCES ts_header(ts_id);
ALTER TABLE ts_values_day SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_day', INTERVAL '2 years');

-- ============================================================
-- 2. ts_values_1h: Hash(ts_id, 4) entfernen
-- ============================================================

SELECT decompress_chunk(c, true)
FROM show_chunks('ts_values_1h') c
WHERE EXISTS (
    SELECT 1 FROM timescaledb_information.chunks
    WHERE hypertable_name = 'ts_values_1h'
      AND chunk_name = c::text
      AND is_compressed = true
);
SELECT remove_compression_policy('ts_values_1h', if_exists => true);

CREATE TABLE ts_values_1h_new (
    ts_id    BIGINT NOT NULL,
    ts_date  DATE NOT NULL,
    vals     DOUBLE PRECISION[] NOT NULL
);
SELECT create_hypertable('ts_values_1h_new', by_range('ts_date', INTERVAL '1 year'));

INSERT INTO ts_values_1h_new (ts_id, ts_date, vals)
SELECT ts_id, ts_date, vals FROM ts_values_1h;

DROP TABLE ts_values_1h;
ALTER TABLE ts_values_1h_new RENAME TO ts_values_1h;

CREATE UNIQUE INDEX idx_1h_pk ON ts_values_1h (ts_id, ts_date);
ALTER TABLE ts_values_1h ADD CONSTRAINT fk_1h_header
    FOREIGN KEY (ts_id) REFERENCES ts_header(ts_id);
ALTER TABLE ts_values_1h SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_1h', INTERVAL '6 months');

-- ============================================================
-- 3. ts_values_15min: Hash(ts_id, 8) entfernen
-- ============================================================

SELECT decompress_chunk(c, true)
FROM show_chunks('ts_values_15min') c
WHERE EXISTS (
    SELECT 1 FROM timescaledb_information.chunks
    WHERE hypertable_name = 'ts_values_15min'
      AND chunk_name = c::text
      AND is_compressed = true
);
SELECT remove_compression_policy('ts_values_15min', if_exists => true);

CREATE TABLE ts_values_15min_new (
    ts_id    BIGINT NOT NULL,
    ts_date  DATE NOT NULL,
    vals     DOUBLE PRECISION[] NOT NULL
);
SELECT create_hypertable('ts_values_15min_new', by_range('ts_date', INTERVAL '1 year'));

INSERT INTO ts_values_15min_new (ts_id, ts_date, vals)
SELECT ts_id, ts_date, vals FROM ts_values_15min;

DROP TABLE ts_values_15min;
ALTER TABLE ts_values_15min_new RENAME TO ts_values_15min;

CREATE UNIQUE INDEX idx_15min_pk ON ts_values_15min (ts_id, ts_date);
ALTER TABLE ts_values_15min ADD CONSTRAINT fk_15min_header
    FOREIGN KEY (ts_id) REFERENCES ts_header(ts_id);
ALTER TABLE ts_values_15min SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_15min', INTERVAL '3 months');

COMMIT;
```

- [ ] **Step 2: Migration ausfuehren**

Run: `docker exec -i timescaledb psql -U postgres -d timeseries < sql/migrations/014_remove_hash_partitioning.sql`
Expected: Keine Fehler. Jede Tabelle wird neu erstellt ohne Hash-Dimension.

- [ ] **Step 3: Verifizieren**

Run: `docker exec timescaledb psql -U postgres -d timeseries -c "SELECT hypertable_name, dimension_number, column_name, column_type FROM timescaledb_information.dimensions WHERE hypertable_name IN ('ts_values_15min','ts_values_1h','ts_values_day') ORDER BY hypertable_name, dimension_number;"`
Expected: Jede Tabelle hat nur 1 Dimension (ts_date range), keine Hash-Dimension mehr.

- [ ] **Step 4: Commit**

```bash
git add sql/migrations/014_remove_hash_partitioning.sql
git commit -m "feat(TS1): Hash-Partitionierung von 3 Hypertables entfernt"
```

---

### Task 2: Migration 015 — first_date/last_date in ts_header

**Files:**
- Create: `sql/migrations/015_header_date_range.sql`

- [ ] **Step 1: Migration-Datei erstellen**

```sql
-- sql/migrations/015_header_date_range.sql
-- Fuegt first_date/last_date zu ts_header hinzu und befuellt initial aus Werte-Tabellen.

ALTER TABLE ts_header ADD COLUMN first_date DATE;
ALTER TABLE ts_header ADD COLUMN last_date DATE;

-- Initial-Befuellung ueber alle 5 Dimensionen
UPDATE ts_header h SET
  first_date = sub.first_date,
  last_date = sub.last_date
FROM (
  SELECT ts_id, MIN(d) AS first_date, MAX(d) AS last_date FROM (
    SELECT ts_id, ts_date AS d FROM ts_values_15min
    UNION ALL SELECT ts_id, ts_date FROM ts_values_1h
    UNION ALL SELECT ts_id, ts_date FROM ts_values_day
    UNION ALL SELECT ts_id, ts_date FROM ts_values_month
    UNION ALL SELECT ts_id, make_date(ts_year, 1, 1) FROM ts_values_year
  ) x GROUP BY ts_id
) sub
WHERE h.ts_id = sub.ts_id;
```

- [ ] **Step 2: Migration ausfuehren**

Run: `MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/migrations/015_header_date_range.sql`
Expected: ALTER TABLE + UPDATE erfolgreich.

- [ ] **Step 3: Verifizieren**

Run: `docker exec timescaledb psql -U postgres -d timeseries -c "SELECT ts_id, ts_key, first_date, last_date FROM ts_header ORDER BY ts_id LIMIT 10;"`
Expected: Zeilen mit befuellten first_date/last_date Werten (oder NULL fuer Header ohne Werte).

- [ ] **Step 4: Commit**

```bash
git add sql/migrations/015_header_date_range.sql
git commit -m "feat(TS2): first_date/last_date in ts_header + Initial-Befuellung"
```

---

### Task 3: jOOQ Codegen neu generieren

**Files:**
- Modify: `src/generated/java/de/market/jooq/generated/` (automatisch)

- [ ] **Step 1: Codegen ausfuehren**

Run: `./gradlew generateJooq`
Expected: BUILD SUCCESSFUL. `TsHeader`-Klasse enthaelt neue Felder `FIRST_DATE` und `LAST_DATE`.

- [ ] **Step 2: Verifizieren**

Run: `grep -n "FIRST_DATE\|LAST_DATE" src/generated/java/de/market/jooq/generated/tables/TsHeader.java | head -10`
Expected: Zeilen mit `FIRST_DATE` und `LAST_DATE` Field-Definitionen.

- [ ] **Step 3: Commit**

```bash
git add src/generated/java/de/market/jooq/generated/
git commit -m "chore: jOOQ Codegen nach Migration 015 (first_date/last_date)"
```

---

### Task 4: Write-Procedures — Header-Update bei Schreibvorgaengen

**Files:**
- Modify: `sql/procedures/ts_write_15min_day.sql`
- Modify: `sql/procedures/ts_write_1h_day.sql`
- Modify: `sql/procedures/ts_write_15min_year.sql`
- Modify: `sql/procedures/ts_write_1h_year.sql`
- Modify: `sql/procedures/ts_write_15min_range.sql`

- [ ] **Step 1: ts_write_15min_day.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_write_15min_day.sql`:

```sql
-- Schreibt einen Tag (Upsert). Validiert Array-Laenge gegen DST.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_15min_day(
    p_ts_id  BIGINT,
    p_date   DATE,
    p_values DOUBLE PRECISION[]
) RETURNS VOID AS $$
DECLARE
    v_expected INTEGER;
    v_actual   INTEGER;
BEGIN
    v_expected := ts_intervals_per_day(p_date, INTERVAL '15 minutes');
    v_actual := array_length(p_values, 1);

    IF v_actual != v_expected THEN
        RAISE EXCEPTION 'DST-Fehler: Erwartet % Werte fuer %, erhalten %',
            v_expected, p_date, v_actual;
    END IF;

    INSERT INTO ts_values_15min (ts_id, ts_date, vals)
    VALUES (p_ts_id, p_date, p_values)
    ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

    UPDATE ts_header SET
        first_date = LEAST(first_date, p_date),
        last_date  = GREATEST(last_date, p_date)
    WHERE ts_id = p_ts_id;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 2: ts_write_1h_day.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_write_1h_day.sql`:

```sql
-- Schreibt einen Stundentag (Upsert). Validiert Array-Laenge gegen DST.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_1h_day(
    p_ts_id  BIGINT,
    p_date   DATE,
    p_values DOUBLE PRECISION[]
) RETURNS VOID AS $$
DECLARE
    v_expected INTEGER;
    v_actual   INTEGER;
BEGIN
    v_expected := ts_intervals_per_day(p_date, INTERVAL '1 hour');
    v_actual := array_length(p_values, 1);

    IF v_actual != v_expected THEN
        RAISE EXCEPTION 'DST-Fehler: Erwartet % Werte fuer %, erhalten %',
            v_expected, p_date, v_actual;
    END IF;

    INSERT INTO ts_values_1h (ts_id, ts_date, vals)
    VALUES (p_ts_id, p_date, p_values)
    ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

    UPDATE ts_header SET
        first_date = LEAST(first_date, p_date),
        last_date  = GREATEST(last_date, p_date)
    WHERE ts_id = p_ts_id;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 3: ts_write_15min_year.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_write_15min_year.sql`:

```sql
-- Schreibt ein ganzes Jahr aus einem flachen Array.
-- Splittet automatisch nach Tagen unter Beruecksichtigung von DST.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_15min_year(
    p_ts_id  BIGINT,
    p_year   INTEGER,
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_end_date  DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := make_date(p_year, 1, 1);
    v_end_date := make_date(p_year + 1, 1, 1);

    WHILE v_date < v_end_date LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '15 minutes');

        INSERT INTO ts_values_15min (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    IF v_offset - 1 != array_length(p_values, 1) THEN
        RAISE EXCEPTION 'Array-Laenge stimmt nicht: Erwartet %, erhalten %',
            v_offset - 1, array_length(p_values, 1);
    END IF;

    UPDATE ts_header SET
        first_date = LEAST(first_date, make_date(p_year, 1, 1)),
        last_date  = GREATEST(last_date, make_date(p_year, 12, 31))
    WHERE ts_id = p_ts_id;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 4: ts_write_1h_year.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_write_1h_year.sql`:

```sql
-- Schreibt ein ganzes Jahr Stundenwerte aus einem flachen Array.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_1h_year(
    p_ts_id  BIGINT,
    p_year   INTEGER,
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_end_date  DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := make_date(p_year, 1, 1);
    v_end_date := make_date(p_year + 1, 1, 1);

    WHILE v_date < v_end_date LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '1 hour');

        INSERT INTO ts_values_1h (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    IF v_offset - 1 != array_length(p_values, 1) THEN
        RAISE EXCEPTION 'Array-Laenge stimmt nicht: Erwartet %, erhalten %',
            v_offset - 1, array_length(p_values, 1);
    END IF;

    UPDATE ts_header SET
        first_date = LEAST(first_date, make_date(p_year, 1, 1)),
        last_date  = GREATEST(last_date, make_date(p_year, 12, 31))
    WHERE ts_id = p_ts_id;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 5: ts_write_15min_range.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_write_15min_range.sql`:

```sql
-- Schreibt einen Bereich (von-bis Datum) aus einem flachen Array.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_15min_range(
    p_ts_id  BIGINT,
    p_from   DATE,
    p_to     DATE,       -- exklusiv
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := p_from;
    WHILE v_date < p_to LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '15 minutes');

        INSERT INTO ts_values_15min (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    UPDATE ts_header SET
        first_date = LEAST(first_date, p_from),
        last_date  = GREATEST(last_date, p_to - 1)
    WHERE ts_id = p_ts_id;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 6: Procedures in DB laden**

Run:
```bash
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_write_15min_day.sql && \
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_write_1h_day.sql && \
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_write_15min_year.sql && \
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_write_1h_year.sql && \
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_write_15min_range.sql
```
Expected: 5x `CREATE FUNCTION`

- [ ] **Step 7: Commit**

```bash
git add sql/procedures/ts_write_15min_day.sql sql/procedures/ts_write_1h_day.sql sql/procedures/ts_write_15min_year.sql sql/procedures/ts_write_1h_year.sql sql/procedures/ts_write_15min_range.sql
git commit -m "feat(TS2): Write-Procedures aktualisieren first_date/last_date in ts_header"
```

---

### Task 5: Delete-Procedures — Header-Update nach Loeschvorgaengen

**Files:**
- Modify: `sql/procedures/ts_delete_15min.sql`
- Modify: `sql/procedures/ts_delete_1h.sql`

- [ ] **Step 1: ts_delete_15min.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_delete_15min.sql`:

```sql
-- Loescht 15min-Werte fuer eine Zeitreihe (optional eingeschraenkt auf Datumsbereich).
-- Berechnet first_date/last_date in ts_header neu.
CREATE OR REPLACE FUNCTION ts_delete_15min(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_count      INTEGER;
    v_first_date DATE;
    v_last_date  DATE;
BEGIN
    DELETE FROM ts_values_15min
    WHERE ts_id = p_ts_id
      AND (p_from IS NULL OR ts_date >= p_from)
      AND (p_to IS NULL OR ts_date < p_to);

    GET DIAGNOSTICS v_count = ROW_COUNT;

    -- Neue Grenzen aus verbleibenden Daten berechnen
    SELECT MIN(ts_date), MAX(ts_date)
    INTO v_first_date, v_last_date
    FROM ts_values_15min
    WHERE ts_id = p_ts_id;

    UPDATE ts_header SET
        first_date = v_first_date,
        last_date  = v_last_date
    WHERE ts_id = p_ts_id;

    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 2: ts_delete_1h.sql anpassen**

Ersetze den gesamten Inhalt von `sql/procedures/ts_delete_1h.sql`:

```sql
-- Loescht 1h-Werte fuer eine Zeitreihe (optional eingeschraenkt auf Datumsbereich).
-- Berechnet first_date/last_date in ts_header neu.
CREATE OR REPLACE FUNCTION ts_delete_1h(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_count      INTEGER;
    v_first_date DATE;
    v_last_date  DATE;
BEGIN
    DELETE FROM ts_values_1h
    WHERE ts_id = p_ts_id
      AND (p_from IS NULL OR ts_date >= p_from)
      AND (p_to IS NULL OR ts_date < p_to);

    GET DIAGNOSTICS v_count = ROW_COUNT;

    -- Neue Grenzen aus verbleibenden Daten berechnen
    SELECT MIN(ts_date), MAX(ts_date)
    INTO v_first_date, v_last_date
    FROM ts_values_1h
    WHERE ts_id = p_ts_id;

    UPDATE ts_header SET
        first_date = v_first_date,
        last_date  = v_last_date
    WHERE ts_id = p_ts_id;

    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 3: Procedures in DB laden**

Run:
```bash
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_delete_15min.sql && \
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/procedures/ts_delete_1h.sql
```
Expected: 2x `CREATE FUNCTION`

- [ ] **Step 4: Commit**

```bash
git add sql/procedures/ts_delete_15min.sql sql/procedures/ts_delete_1h.sql
git commit -m "feat(TS2): Delete-Procedures berechnen first_date/last_date neu"
```

---

### Task 6: Java — writeSimple() Header-Update

**Files:**
- Modify: `src/main/java/de/market/timeseries/repository/TimeSeriesRepository.java`

- [ ] **Step 1: writeSimple() erweitern**

In `src/main/java/de/market/timeseries/repository/TimeSeriesRepository.java`, die Methode `writeSimple()` (Zeilen 76-94) ersetzen:

```java
    public void writeSimple(long tsId, TimeDimension dim, LocalDate date, double value) {
        if (dim == TimeDimension.YEAR) {
            dsl.insertInto(table(name(dim.getTableName())))
                    .columns(field(name("ts_id")), field(name("ts_year")), field(name("value")))
                    .values(tsId, (short) date.getYear(), value)
                    .onConflict(field(name("ts_id")), field(name("ts_year")))
                    .doUpdate()
                    .set(field(name("value")), value)
                    .execute();

            // YEAR: first_date = 1. Jan, last_date = 31. Dez
            LocalDate firstOfYear = LocalDate.of(date.getYear(), 1, 1);
            LocalDate lastOfYear = LocalDate.of(date.getYear(), 12, 31);
            dsl.update(TS_HEADER)
                    .set(TS_HEADER.FIRST_DATE, least(TS_HEADER.FIRST_DATE, val(firstOfYear)))
                    .set(TS_HEADER.LAST_DATE, greatest(TS_HEADER.LAST_DATE, val(lastOfYear)))
                    .where(TS_HEADER.TS_ID.eq(tsId))
                    .execute();
        } else {
            dsl.insertInto(table(name(dim.getTableName())))
                    .columns(field(name("ts_id")), field(name("ts_date")), field(name("value")))
                    .values(tsId, date, value)
                    .onConflict(field(name("ts_id")), field(name("ts_date")))
                    .doUpdate()
                    .set(field(name("value")), value)
                    .execute();

            dsl.update(TS_HEADER)
                    .set(TS_HEADER.FIRST_DATE, least(TS_HEADER.FIRST_DATE, val(date)))
                    .set(TS_HEADER.LAST_DATE, greatest(TS_HEADER.LAST_DATE, val(date)))
                    .where(TS_HEADER.TS_ID.eq(tsId))
                    .execute();
        }
    }
```

Fehlende Imports am Dateianfang hinzufuegen (falls nicht vorhanden):

```java
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.least;
import static org.jooq.impl.DSL.greatest;
```

- [ ] **Step 2: Build pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/timeseries/repository/TimeSeriesRepository.java
git commit -m "feat(TS2): writeSimple() aktualisiert first_date/last_date in ts_header"
```

---

### Task 7: Java — Overview-Query vereinfachen

**Files:**
- Modify: `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java`

- [ ] **Step 1: CTE entfernen, Header-Spalten nutzen**

Ersetze den gesamten Inhalt von `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java`:

```java
package de.market.timeseries.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static de.market.jooq.generated.tables.TsUnit.TS_UNIT;
import static org.jooq.impl.DSL.*;

@Repository
public class TimeSeriesOverviewRepository extends AbstractOverviewRepository {

    public TimeSeriesOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return buildAndFetch(noCondition());
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return buildAndFetch(condition);
    }

    private List<Map<String, Object>> buildAndFetch(Condition condition) {
        var h = TS_HEADER.as("h");
        var u = TS_UNIT.as("u");
        var c = TS_CURRENCY.as("c");
        var o = TS_OBJECT.as("o");

        Field<String> dimensionLabel = when(h.TIME_DIM.eq((short) 1), inline("15 Minuten"))
                .when(h.TIME_DIM.eq((short) 2), inline("1 Stunde"))
                .when(h.TIME_DIM.eq((short) 3), inline("Tag"))
                .when(h.TIME_DIM.eq((short) 4), inline("Monat"))
                .when(h.TIME_DIM.eq((short) 5), inline("Jahr"))
                .otherwise(inline(""));

        return dsl
                .select(
                        h.TS_ID.as("id"),
                        h.TS_KEY.as("key"),
                        dimensionLabel.as("dimension"),
                        u.SYMBOL.as("unit"),
                        c.ISO_CODE.as("currency"),
                        o.OBJECT_KEY.as("object"),
                        h.DESCRIPTION.as("description"),
                        h.FIRST_DATE.as("firstDate"),
                        h.LAST_DATE.as("lastDate"),
                        h.CREATED_AT.as("createdAt"),
                        h.UPDATED_AT.as("updatedAt")
                )
                .from(h)
                .join(u).on(u.UNIT_ID.eq(h.UNIT_ID))
                .leftJoin(c).on(c.CURRENCY_ID.eq(h.CURRENCY_ID))
                .leftJoin(o).on(o.OBJECT_ID.eq(h.OBJECT_ID))
                .where(condition)
                .orderBy(h.TS_KEY)
                .fetchMaps();
    }
}
```

- [ ] **Step 2: Build pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java
git commit -m "feat(TS2): Overview-Query nutzt ts_header.first_date/last_date statt UNION ALL CTE"
```

---

### Task 8: schema.sql aktualisieren + Dokumentation

**Files:**
- Modify: `sql/schema.sql`
- Modify: `DONE.md`
- Modify: `TODO.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: schema.sql — Hash-Dimensionen entfernen**

In `sql/schema.sql` die drei `add_dimension(..., by_hash(...))` Zeilen entfernen:

Zeile 137: `SELECT add_dimension('ts_values_15min', by_hash('ts_id', 8));` — loeschen
Zeile 157: `SELECT add_dimension('ts_values_1h', by_hash('ts_id', 4));` — loeschen
Zeile 177: `SELECT add_dimension('ts_values_day', by_hash('ts_id', 4));` — loeschen

- [ ] **Step 2: schema.sql — first_date/last_date zu ts_header**

In `sql/schema.sql`, in der `CREATE TABLE ts_header` Definition (nach `updated_at`):

```sql
    first_date    DATE,
    last_date     DATE,
```

- [ ] **Step 3: TODO.md — TS1/TS2 abhaken**

```markdown
- [x] **TS1** — Hash-Partitionierung entfernt (Migration 014)
- [x] **TS2** — first_date/last_date in ts_header, Overview-Query vereinfacht (Migration 015)
```

- [ ] **Step 4: DONE.md — Eintrag**

Am Anfang der Datei:

```markdown
## 2026-04-02 — TimescaleDB-Optimierung (TS1+TS2)
- Hash-Partitionierung von 3 Hypertables entfernt (15min, 1h, day) — Single-Node braucht kein Hash
- first_date/last_date Spalten in ts_header, gepflegt durch Write- und Delete-Procedures + writeSimple()
- Overview-Query: UNION ALL CTE ueber 5 Tabellen durch direkten Header-Zugriff ersetzt
```

- [ ] **Step 5: CLAUDE.md — Performance-Abschnitt aktualisieren**

Im Performance-Abschnitt den Punkt "Hypertable Hash-Partitionierung" entfernen oder aktualisieren:

Ersetze:
```
- **Hypertable Hash-Partitionierung**: `ANY(array)` ist langsamer als parallele Einzelabfragen für QH/H
```
durch:
```
- **Hypertable Range-Only**: Hash-Partitionierung entfernt (Single-Node), nur Range auf ts_date
- **Overview-Performance**: first_date/last_date direkt in ts_header statt UNION ALL CTE
```

- [ ] **Step 6: Commit**

```bash
git add sql/schema.sql DONE.md TODO.md CLAUDE.md
git commit -m "docs: TS1+TS2 in schema.sql, TODO.md, DONE.md, CLAUDE.md dokumentiert"
```
