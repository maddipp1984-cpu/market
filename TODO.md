# TODO - Zeitreihensystem

## Offen

### Architektur / Infrastruktur
- [ ] Transaktionsmanagement: Zusammenhängende Schreiboperationen in einer Transaktion
- [ ] Fehlerbehandlung: Fachliche Exceptions, Logging, Retry bei Connection-Timeouts
- [ ] Bulk-Lesen: Batch-Operationen für viele Zeitreihen im selben Zeitraum

### Aggregation / DimensionConverter
- [ ] Start-Normalisierung fixen: Ergebnis-Slice darf nicht über Startzeitpunkt lügen bei Teildaten
- [ ] Jahr→Monat SUM: Proportional nach Monatslänge verteilen statt /12
- [ ] AVG-Kaskadierung: DST-Tage korrekt gewichten (92 vs 96 QH)
- [ ] Review-Findings fixen: Fehlende Tests (Anschnitt bei Aggregation, Metadaten, MIN/MAX, Fehlerfälle)

### Sonstiges
- [ ] TimescaleDB per Docker aufsetzen und Schema testen
- [ ] Integrationstests mit Testcontainers
- [ ] Performance-Benchmark: Bulk-Insert und Lese-Geschwindigkeit
- [ ] Upsert-Logik für Hypertables (Unique-Constraint auf Hypertables)

## Erledigt
- [x] Datenbankarchitektur entworfen (TimescaleDB)
- [x] SQL-Schema erstellt
- [x] Java-Grundstruktur implementiert (Model, DB, DST, Service)
- [x] Gradle-Projekt mit Wrapper aufgesetzt
