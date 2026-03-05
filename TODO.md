# TODO - Zeitreihensystem

## Offen

### Architektur / Infrastruktur
- [ ] **A1** — Transaktionsmanagement: Zusammenhängende Schreiboperationen in einer Transaktion
- [ ] **A2** — Fehlerbehandlung: Fachliche Exceptions, Logging, Retry bei Connection-Timeouts
- [ ] **A3** — Bulk-Lesen: Batch-Operationen für viele Zeitreihen im selben Zeitraum
- [x] **A4** — Service-API vervollständigen: writeRange, writeSimple, readSimple, delete(from, to) im Service exponieren
- [x] **A5** — Stored Procedures fixen: `timezone`-Referenz auf `ts_header` entfernen, Konstante `'Europe/Berlin'` nutzen

### Aggregation / DimensionConverter
- [x] **D1** — Start-Normalisierung: Kein Bug — Slice bildet immer den angeforderten Zeitraum ab, fehlende Daten = NaN
- [ ] **D2** — Jahr→Monat SUM: Proportional nach Monatslänge verteilen statt /12
- [x] **D3** — AVG-Kaskadierung: DST-Tage korrekt gewichten (92 vs 96 QH)
- [x] **D4** — Review-Findings fixen: Fehlende Tests (Anschnitt bei Aggregation, Metadaten, MIN/MAX, Fehlerfälle)

### REST-API Aufräumen
- [ ] **R1** — ObjectController prüfen: Wird er gebraucht? Vermutlich nein → entfernen
- [ ] **R2** — TimeSeriesController prüfen: Nicht jede interne API soll exponiert werden → Endpoints reduzieren

### Sonstiges
- [x] **S1** — TimescaleDB per Docker aufsetzen und Schema testen
- [ ] **S2** — Integrationstests mit Testcontainers
- [ ] **S3** — Performance-Benchmark: Bulk-Insert und Lese-Geschwindigkeit
- [ ] **S4** — Upsert-Logik für Hypertables (Unique-Constraint auf Hypertables)
- [ ] **S5** — Continuous Aggregates evaluieren: Materialisierte Views für häufig abgefragte Aggregationen (QH→Tag) statt separate Tabellen

## Erledigt
- [x] Datenbankarchitektur entworfen (TimescaleDB)
- [x] SQL-Schema erstellt
- [x] Java-Grundstruktur implementiert (Model, DB, DST, Service)
- [x] Gradle-Projekt mit Wrapper aufgesetzt
- [x] Dimensionskonvertierung (Aggregation/Disaggregation) im TimeSeriesClient
- [x] Unit-Konvertierung: UnitCategory, UnitConverter (Faktor, Offset, Power↔Energy DST-aware), Client-Integration, Tests + Mockito
- [x] Write-API: TimeSeriesClient.write() mit Disaggregation/Aggregation, writeSimple im Service, Tests
- [x] Übergeordnete Objekte (ts_object): ObjectType-Enum, TsObject-Model, ObjectRepository, Service-Integration, Migration, Tests
- [x] **Spring Boot Migration**: Java 17, Maven-Standard-Layout, Spring Boot 3.4.1, REST-Controller, Exception Handling
