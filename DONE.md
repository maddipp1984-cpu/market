# Changelog

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
