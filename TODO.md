# TODO - Zeitreihensystem

## Offen
- [ ] TimescaleDB per Docker aufsetzen und Schema testen
- [ ] Unit-Tests für DstAwareTimeGenerator
- [ ] Integrationstests mit Testcontainers
- [ ] Performance-Benchmark: Bulk-Insert und Lese-Geschwindigkeit
- [ ] Upsert-Logik für Hypertables (Unique-Constraint auf Hypertables)
- [ ] Fehlerbehandlung und Retry-Logik im BulkWriter
- [ ] CLI oder REST-API für Zeitreihen-Zugriff

## Erledigt
- [x] Datenbankarchitektur entworfen (TimescaleDB)
- [x] SQL-Schema erstellt
- [x] Java-Grundstruktur implementiert (Model, DB, DST, Service)
- [x] Gradle-Projekt mit Wrapper aufgesetzt
