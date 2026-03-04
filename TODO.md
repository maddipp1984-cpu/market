# TODO - Zeitreihensystem

## Offen

### Architektur / Infrastruktur
- [ ] **A1** — Transaktionsmanagement: Zusammenhängende Schreiboperationen in einer Transaktion
- [ ] **A2** — Fehlerbehandlung: Fachliche Exceptions, Logging, Retry bei Connection-Timeouts
- [ ] **A3** — Bulk-Lesen: Batch-Operationen für viele Zeitreihen im selben Zeitraum
- [ ] **A4** — Service-API vervollständigen: writeRange, writeSimple, readSimple, delete(from, to) im Service exponieren
- [x] **A5** — Stored Procedures fixen: `timezone`-Referenz auf `ts_header` entfernen, Konstante `'Europe/Berlin'` nutzen

### Aggregation / DimensionConverter
- [ ] **D1** — Start-Normalisierung fixen: Ergebnis-Slice darf nicht über Startzeitpunkt lügen bei Teildaten
- [ ] **D2** — Jahr→Monat SUM: Proportional nach Monatslänge verteilen statt /12
- [ ] **D3** — AVG-Kaskadierung: DST-Tage korrekt gewichten (92 vs 96 QH)
- [ ] **D4** — Review-Findings fixen: Fehlende Tests (Anschnitt bei Aggregation, Metadaten, MIN/MAX, Fehlerfälle)

### Write-API
- [x] **W0** — Write-API im TimeSeriesClient: write(tsId, slice) mit automatischer Dimensions-Konvertierung
- [ ] **W1** — Unit-Konvertierung beim Schreiben: Eingabewerte automatisch in DB-Unit konvertieren (z.B. MWh→kWh)

### Sonstiges
- [ ] **S1** — TimescaleDB per Docker aufsetzen und Schema testen
- [ ] **S2** — Integrationstests mit Testcontainers
- [ ] **S3** — Performance-Benchmark: Bulk-Insert und Lese-Geschwindigkeit
- [ ] **S4** — Upsert-Logik für Hypertables (Unique-Constraint auf Hypertables)
- [ ] **S5** — Continuous Aggregates evaluieren: Materialisierte Views für häufig abgefragte Aggregationen (QH→Tag) statt separate Tabellen
- [ ] **S6** — MSCONS-Import/Export: EDIFACT-Lastgänge einlesen/schreiben (Branchenstandard Energiewirtschaft)

## Erledigt
- [x] Datenbankarchitektur entworfen (TimescaleDB)
- [x] SQL-Schema erstellt
- [x] Java-Grundstruktur implementiert (Model, DB, DST, Service)
- [x] Gradle-Projekt mit Wrapper aufgesetzt
- [x] Dimensionskonvertierung (Aggregation/Disaggregation) im TimeSeriesClient
- [x] Unit-Konvertierung: UnitCategory, UnitConverter (Faktor, Offset, Power↔Energy DST-aware), Client-Integration, Tests + Mockito
- [x] Write-API: TimeSeriesClient.write() mit Disaggregation/Aggregation, writeSimple im Service, Tests
