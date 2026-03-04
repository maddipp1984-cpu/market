# Zeitreihensystem

## Übersicht
Performantes Zeitreihensystem für >10 Mio Zeitreihen mit TimescaleDB (PostgreSQL-Extension).

## Zeitdimensionen
| Code | Dimension | Tabelle | Zeittyp |
|------|-----------|---------|---------|
| 1 | 15 Minuten | ts_values_15min | TIMESTAMPTZ |
| 2 | 1 Stunde | ts_values_1h | TIMESTAMPTZ |
| 3 | Tag | ts_values_day | DATE |
| 4 | Monat | ts_values_month | DATE |
| 5 | Jahr | ts_values_year | SMALLINT |

## Architektur

### Tabellendesign
- **Header-Tabelle** (`ts_header`): Metadaten (Key, Dimension, Einheit, Zeitzone)
- **Separate Werte-Tabellen** pro Dimension: Unterschiedliche Chunk-Größen/Kompression
- **TimescaleDB Hypertables** für 15min, 1h, Tag, Monat (nicht für Jahr)
- **Hash-Partitionierung** auf `ts_id` für schnellen Einzelreihen-Zugriff

### Monolith-Architektur & Schichten
- **`de.projekt`** als Basis-Package — `timeseries` ist eine Domäne unter vielen (zukünftig)
- **`common.db`** — Shared Infrastruktur (ConnectionPool), nicht domänenspezifisch
- **`timeseries.api`** — einzige öffentliche Schnittstelle der Domäne
- **Schichten-Regel**: `REST-Controller → Business-Layer → timeseries.api → repository`
- Nichts unterhalb von `timeseries.api` wird direkt von außen aufgerufen

### DST-Handling
- `TIMESTAMPTZ` speichert intern UTC → jeder Zeitpunkt eindeutig
- Normaltag: 96 QH / 24 H
- Sommerzeit (März): 92 QH / 23 H
- Winterzeit (Oktober): 100 QH / 25 H
- Zeitzone immer `Europe/Berlin` (Konstante in `TimeSeriesSlice.ZONE`)
- Timestamps werden lazy berechnet: `slice.getTimestamp(date, index)`

### Performance
- **Kompression**: `segmentby = ts_id`, automatisch nach 3-6 Monaten
- **Lesen**: Index `(ts_id, ts_time)`, fetchSize=10.000

## Projektstruktur
```
src/de/projekt/
    Main.java                              -- Einstiegspunkt
    common/
        EnvUtil.java                       -- Umgebungsvariablen-Hilfsmethoden
        db/
            ConnectionPool.java            -- HikariCP Wrapper (shared Infrastruktur)
    timeseries/
        api/
            TimeSeriesService.java         -- Öffentliche Fassade der Domäne
        model/
            TimeDimension.java             -- Enum mit Tabellen-Mapping
            TimeSeriesHeader.java          -- Metadaten-Modell
            TimeSeriesSlice.java           -- Tages-Slices mit lazy Timestamps
        repository/
            HeaderRepository.java          -- CRUD ts_header
            TimeSeriesRepository.java      -- Lesen/Schreiben/Löschen
    benchmark/
        Benchmark.java                     -- Lese-Benchmark gegen PERF_TEST-Daten
sql/
    schema.sql                         -- Komplettes DB-Schema
    procedures/                        -- Eine Datei pro Stored Procedure
        ts_intervals_per_day.sql       -- DST-bewusste Intervallberechnung
        ts_generate_timestamps.sql     -- Timestamps für einen Tag erzeugen
        ts_write_15min_day.sql         -- 15min: Tag schreiben (Upsert)
        ts_write_15min_year.sql        -- 15min: Jahr schreiben
        ts_write_15min_range.sql       -- 15min: Bereich schreiben
        ts_write_1h_day.sql            -- 1h: Tag schreiben (Upsert)
        ts_write_1h_year.sql           -- 1h: Jahr schreiben
        ts_read_15min.sql              -- 15min: Expanded lesen
        ts_read_15min_raw.sql          -- 15min: Raw lesen
        ts_read_1h.sql                 -- 1h: Expanded lesen
        ts_read_1h_raw.sql             -- 1h: Raw lesen
        ts_delete_15min.sql            -- 15min: Löschen
        ts_delete_1h.sql               -- 1h: Löschen
benchmarks/
    YYYY-MM-DD_beschreibung.md         -- Benchmark-Ergebnisse
```

## Changelog
- Nach jedem abgeschlossenen Feature/Umbau: Eintrag in `DONE.md` mit Datum und Zusammenfassung

## Berechtigungen
- Alle `./gradlew`-Befehle dürfen ohne Rückfrage ausgeführt werden

## Build & Run
```bash
./gradlew build                        # Kompilieren + Tests
./gradlew shadowJar                    # Fat-JAR erstellen

# Umgebungsvariablen
TS_JDBC_URL=jdbc:postgresql://localhost:5432/timeseries
TS_DB_USER=postgres
TS_DB_PASSWORD=postgres
```

## Benchmark
- **Code:** `src/de/projekt/benchmark/Benchmark.java`
- **Aufruf:** `./gradlew run --args="benchmark"` (Main.java leitet weiter)
- **Testdaten:** 20.000 PERF_TEST-Zeitreihen (PERF_TEST_00001–20000) in der DB, nicht löschen!
- **Ergebnisse:** `benchmarks/` – Dateien nach Schema `YYYY-MM-DD_beschreibung.md`
- Nur Lese-Benchmarks gegen existierende PERF_TEST-Daten (kein Schreiben/Cleanup)

## Dependencies
- PostgreSQL JDBC 42.7.3
- HikariCP 5.1.0
- SLF4J 2.0.12
- JUnit 5.10.2 (Test)
