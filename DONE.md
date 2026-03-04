# Changelog

## 2026-03-04 — Übergeordnete Objekte (ts_object)
- `ts_object_type` + `ts_object` Tabellen: Zeitreihen können Objekten zugeordnet werden (1:n)
- `ObjectType` Enum, `TsObject` Model, `ObjectRepository` CRUD
- `TimeSeriesHeader` um `objectId` (nullable) erweitert
- `HeaderRepository`: `object_id` in INSERT/UPDATE/SELECT, neue `findByObjectId()`
- `TimeSeriesService`: Objekt-Operationen (create, get, assign, remove)
- Migration 003, schema.sql aktualisiert
- 4 initiale Objekttypen: CONTRACT_VHP, CONTRACT, CONTRACT_VERANS, ANS

## 2026-03-04 — Write-API im TimeSeriesClient
- `TimeSeriesClient.write(tsId, slice)`: Schreibt Zeitreihen mit automatischer Dimensionskonvertierung
- Subdaily (QH/H): Tageweiser Split mit DST-aware intervalsPerDay
- DAY/MONTH/YEAR: Einzelwerte über `writeSimple`
- `TimeSeriesService.writeSimple()`: Zwei Overloads (LocalDate + int year)
- Aggregation (QH→Tag) und Disaggregation (Tag→QH, Gleichverteilung) beim Schreiben
- 12 Tests: Gleiche Dimension, Disaggregation (inkl. DST Frühling/Herbst), Aggregation, NaN, Fehlerfälle

## 2026-03-04 — Package-Struktur für Monolith-Architektur
- Package-Struktur umgebaut: `de.projekt` als Basis, `common.db` für Infrastruktur, `timeseries.api` als Domänen-Fassade, `timeseries.repository` statt `db`
- `benchmark` auf `de.projekt.benchmark` verschoben
- `build.gradle` mainClass angepasst

## 2026-03-04 — TimeSeriesSlice statt DataPoint
- `TimeSeriesSlice` eingeführt: `LocalDateTime start/end` + flaches `double[]` + lazy `getTimestamp(index)`
- DB liefert nur Werte-Arrays (`SELECT vals ...`), kein Datum in der Rückgabe
- Aufrufer übergibt `LocalDateTime start` und `LocalDateTime end`
- `DataPoint` entfernt — kein Objekt-Overhead mehr pro Messwert
- `readExpanded()` + `readRaw()` entfernt — nur noch `read(tsId, dim, start, end)`
- Read-Stored-Procedures nicht mehr nötig (direktes SQL auf Tabelle)
- `timezone` aus `TimeSeriesHeader` und Repository entfernt — `Europe/Berlin` als Konstante
- Benchmark vereinfacht: eine `benchmarkRead()`-Methode für beliebige Zeitbereiche
- Speicherverbrauch pro Zeitreihe/Jahr: ~274 KB statt ~4,3 MB

## 2026-03-04 — Code-Review Fixes
- **K-1**: NPE-Fix in `readRaw()` — NULL-Werte im Array werden zu `Double.NaN`
- **K-3**: Dimensions-Validierung in `writeDay`, `writeYear`, `readExpanded`, `readRaw`
- **E-2**: Toter Code in `count()` entfernt (identische if/else-Zweige)
- **E-4**: Bug in `delete()` für YEAR-Dimension behoben (`ts_year` statt `ts_date`)
- **E-6**: Benchmark-Pfad in `Main.main()` in try-catch eingeschlossen
- **E-7**: HikariCP Pool-Name gesetzt (`ts-pool`)
- **H-4**: `getEnvOrDefault()` in `EnvUtil` ausgelagert (Deduplizierung)
- Ungenutzte Klassen entfernt: `BulkWriter`, `DstAwareTimeGenerator`
