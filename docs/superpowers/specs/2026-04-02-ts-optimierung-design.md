# TS1+TS2: TimescaleDB-Optimierung (Hash-Partitionierung + first/last_date)

## Zusammenfassung

Zwei zusammenhaengende DB-Optimierungen:
1. **TS1**: Hash-Partitionierung von Hypertables entfernen (Single-Node, kontraproduktiv)
2. **TS2**: `first_date`/`last_date` Spalten in `ts_header` einfuegen, bei Write und Delete pflegen, Overview-Query vereinfachen

## Entscheidungen

| Frage | Entscheidung |
|-------|-------------|
| Multi-Node geplant? | Nein, Single-Node — Hash-Partitionierung entfernen |
| first/last_date bei Delete? | Ja, nach Delete MIN/MAX aus Werte-Tabelle nachschlagen |
| Downtime moeglich? | Ja, System in Entwicklung |
| Migrations-Format | SQL-Dateien in `sql/migrations/` (014, 015) |

## TS1: Hash-Partitionierung entfernen

### Betroffene Tabellen

| Tabelle | Aktuell | Neu |
|---------|---------|-----|
| `ts_values_15min` | Range (1 Jahr) + Hash(ts_id, 8) | Range (1 Jahr) |
| `ts_values_1h` | Range (1 Jahr) + Hash(ts_id, 4) | Range (1 Jahr) |
| `ts_values_day` | Range (1 Jahr) + Hash(ts_id, 4) | Range (1 Jahr) |
| `ts_values_month` | Range (5 Jahre), kein Hash | Keine Aenderung |
| `ts_values_year` | Kein Hypertable | Keine Aenderung |

### Migration `014_remove_hash_partitioning.sql`

Fuer jede betroffene Tabelle (15min, 1h, day):

1. Kompression deaktivieren (`decompress_chunk` fuer alle komprimierten Chunks)
2. Kompressions-Policy entfernen
3. Neue Tabelle ohne Hash erstellen (gleiche Spalten, gleiches Hypertable-Setup ohne `add_dimension`)
4. Daten kopieren: `INSERT INTO new_table SELECT * FROM old_table`
5. Alte Tabelle droppen
6. Neue Tabelle umbenennen
7. Indizes neu erstellen
8. Kompressions-Policy neu einrichten

### Reihenfolge

`ts_values_day` zuerst (kleinste Datenmenge), dann `ts_values_1h`, dann `ts_values_15min` (groesste Datenmenge). So kann bei Problemen frueh abgebrochen werden.

## TS2: first_date / last_date in ts_header

### Migration `015_header_date_range.sql`

```sql
ALTER TABLE ts_header ADD COLUMN first_date DATE;
ALTER TABLE ts_header ADD COLUMN last_date DATE;

-- Initial-Befuellung aus allen 5 Werte-Tabellen
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

### Stored Procedures anpassen

Alle **Write-Procedures** bekommen am Ende ein UPDATE auf `ts_header`:

| Procedure | Aenderung |
|-----------|-----------|
| `ts_write_15min_day` | `UPDATE ts_header SET first_date = LEAST(first_date, p_date), last_date = GREATEST(last_date, p_date) WHERE ts_id = p_ts_id` |
| `ts_write_1h_day` | Analog |
| `ts_write_15min_year` | `LEAST(first_date, make_date(p_year,1,1))`, `GREATEST(last_date, make_date(p_year,12,31))` |
| `ts_write_1h_year` | Analog |
| `ts_write_15min_range` | `LEAST(first_date, p_from)`, `GREATEST(last_date, p_to - 1)` |

Alle **Delete-Procedures** berechnen nach dem Delete das neue MIN/MAX:

| Procedure | Aenderung |
|-----------|-----------|
| `ts_delete_15min` | Nach DELETE: `SELECT MIN(ts_date), MAX(ts_date) FROM ts_values_15min WHERE ts_id = p_ts_id` in Kombination mit anderen Dimensionen → UPDATE ts_header |
| `ts_delete_1h` | Analog |

Fuer Delete muss das Minimum/Maximum ueber **alle** Werte-Tabellen der jeweiligen Zeitreihe berechnet werden (eine ZR hat nur eine Dimension, also reicht die eine Tabelle basierend auf `ts_header.time_dim`).

### Java-Code anpassen: writeSimple()

`TimeSeriesRepository.writeSimple()` schreibt DAY/MONTH/YEAR per jOOQ. Nach dem UPSERT muss ein zusaetzliches UPDATE auf `ts_header` erfolgen:

```java
// Nach dem UPSERT in writeSimple():
dsl.update(TS_HEADER)
   .set(TS_HEADER.FIRST_DATE, least(TS_HEADER.FIRST_DATE, date))
   .set(TS_HEADER.LAST_DATE, greatest(TS_HEADER.LAST_DATE, date))
   .where(TS_HEADER.TS_ID.eq(tsId))
   .execute();
```

Fuer YEAR-Dimension: `date = LocalDate.of(year, 1, 1)` fuer first_date, `LocalDate.of(year, 12, 31)` fuer last_date.

### Java-Code anpassen: deleteSubdaily()

Nach dem Delete-Procedure-Aufruf ist das Update bereits in der Procedure enthalten — kein zusaetzlicher Java-Code noetig.

Fuer DAY/MONTH/YEAR existiert aktuell kein Delete im Java-Code. Falls spaeter ergaenzt, muss dort ebenfalls `first_date`/`last_date` nachberechnet werden.

### Java-Code anpassen: TimeSeriesOverviewRepository

Die UNION-ALL-CTE (`value_range`) wird komplett entfernt. Stattdessen:

```java
// Vorher: LEFT JOIN auf CTE mit UNION ALL ueber 5 Tabellen
// Nachher: direkt aus ts_header lesen
select(
    h.TS_ID.as("id"),
    h.TS_KEY.as("key"),
    ...
    h.FIRST_DATE.as("firstDate"),   // NEU: direkt aus Header
    h.LAST_DATE.as("lastDate"),     // NEU: direkt aus Header
    ...
)
.from(h)
.join(u).on(...)
.leftJoin(c).on(...)
.leftJoin(o).on(...)
// Kein LEFT JOIN auf value_range mehr
```

### jOOQ Codegen

Nach der Migration `./gradlew generateJooq` ausfuehren — die neuen Spalten `FIRST_DATE` und `LAST_DATE` muessen in der generierten `TsHeader`-Klasse erscheinen.

## Dateien

### Neue Dateien
- `sql/migrations/014_remove_hash_partitioning.sql`
- `sql/migrations/015_header_date_range.sql`

### Geaenderte Dateien
- `sql/procedures/ts_write_15min_day.sql` — UPDATE ts_header am Ende
- `sql/procedures/ts_write_1h_day.sql` — UPDATE ts_header am Ende
- `sql/procedures/ts_write_15min_year.sql` — UPDATE ts_header am Ende
- `sql/procedures/ts_write_1h_year.sql` — UPDATE ts_header am Ende
- `sql/procedures/ts_write_15min_range.sql` — UPDATE ts_header am Ende
- `sql/procedures/ts_delete_15min.sql` — MIN/MAX Nachberechnung + UPDATE ts_header
- `sql/procedures/ts_delete_1h.sql` — MIN/MAX Nachberechnung + UPDATE ts_header
- `src/main/java/de/market/timeseries/repository/TimeSeriesRepository.java` — writeSimple() + Header-Update
- `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java` — CTE entfernen, Header-Spalten nutzen
- `src/generated/java/de/market/jooq/generated/` — Regeneriert nach Migration

## Abgrenzung

- **Kein Flyway**: Migrationen bleiben manuell
- **Kein Oracle**: Nur PostgreSQL/TimescaleDB betroffen
- **Keine Read-Procedures**: Werden nicht angefasst (nicht mehr in Verwendung)
- **Kein Frontend**: Overview-API liefert weiterhin `firstDate`/`lastDate` — das Frontend aendert sich nicht
