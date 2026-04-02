# TODO - Zeitreihensystem

## Offen

### QueryRegistry — ERLEDIGT (jOOQ-Migration)
- [x] **Q1** — Migration `007_query_registry.sql` ausfuehren
- [x] **Q2** — QueryRegistry/QueryLoader/XML-Queries durch jOOQ DSL ersetzt, ts_query per Migration 010 entfernt

### Umbenennung — ERLEDIGT
- [x] **U1** — Projekt von "timeseries" auf "market" umbenennen

### DB-Unabhaengigkeit / Schema-Migrationen
- [ ] **DB1** — Flyway einrichten (`spring-boot-starter-flyway`), bestehende Migrationen in `sql/migrations/` als Flyway-Migrationen uebernehmen
- [ ] **DB2** — DB-spezifische Migrationsordner: `db/migration/postgresql/` und `db/migration/oracle/` fuer Dialekt-Unterschiede (Partitioning, Hypertables vs. Oracle Partitions)
- [ ] **DB3** — OracleTimeSeriesProcedures implementieren (PL/SQL, TABLE-Types statt Arrays)
- [ ] **DB4** — jOOQ Oracle-Lizenz evaluieren (Kosten, Bedingungen) wenn Oracle-Support benoetigt wird

### Projekt aufräumen
- [ ] **P1** — Root aufräumen: Lose Skripte (`insert_*.sh`, Logs) in `scripts/` verschieben oder entfernen, klare Ordnerstruktur definieren
- [ ] **P2** — Projektstruktur reviewen: Gehoeren `sql/`, `benchmarks/`, `docs/`, `frontend/` so ins Root oder besser gruppiert?

### Berechtigungssystem (3-Ebenen-Modell) — IN ARBEIT
- [x] **AUTH1** — DB-Migration: 6 Auth-Tabellen (ts_auth_*) + Seed-Daten
- [x] **AUTH2** — Backend: Models, Repositories, PermissionService, UserRegistrationFilter
- [x] **AUTH3** — Backend: AdminController + KeycloakAdminClient
- [x] **AUTH4** — Backend: SecurityConfig umgebaut (nur authenticated, keine Rollen)
- [x] **AUTH5** — Backend: ConfigController Sidebar-Filterung nach Permissions
- [x] **AUTH6** — Frontend: AuthContext (Permissions statt Rollen), client.ts Admin-API
- [x] **AUTH7** — Frontend: 3 Admin-Seiten (Users, Groups, Permission-Matrix)
- [x] **AUTH8** — Frontend: OverviewPage canWrite-Guard, Sidebar Admin-Badge
- [x] **AUTH9** — Keycloak Realm Setup: timeseries-frontend (public) + timeseries-backend (confidential)
- [ ] **AUTH10** — Permission-Checks in ObjectController (Typ-Filterung, can_write/can_delete)
- [ ] **AUTH11** — Permission-Checks in TimeSeriesController (via Objekt-Zuordnung)
- [ ] **AUTH12** — Permission-Checks in Referenzdaten-Controllern (Einheiten, Waehrungen, Objekttypen)
- [ ] **AUTH13** — Frontend: Field-Restriction-Guards im TimeSeriesEditor
- [ ] **AUTH14** — E2E-Test: Neuer User, Gruppe, Rechte, Sichtbarkeit, Schreiben, Loeschen

### TimescaleDB-Optimierung
- [ ] **TS1** — Hash-Partitionierung entfernen (`by_hash('ts_id', 8)`) — auf Single-Node kontraproduktiv, erzeugt 8x mehr Chunks ohne Nutzen, verhindert optimales Chunk-Pruning bei `ANY(array)`-Queries
- [ ] **TS2** — `first_date`/`last_date` Spalten in `ts_header` aufnehmen, beim Schreiben in `PostgresTimeSeriesProcedures` mitpflegen, Overview-CTE (`TimeSeriesOverviewRepository`) auf Header-Spalten umstellen statt UNION ALL ueber alle Werte-Tabellen

### Aggregation Performance-Benchmark
- [ ] **PERF1** — Benchmark fuer alle Aggregations-Ansaetze mit verschiedenen Szenarien (10/100/1k/10k/100k ZR × 3 Tage/1 Monat/1 Jahr/5 Jahre):
  - a) Stored Procedure (`ts_sum_15min`) — LATERAL unnest in DB
  - b) Eine Query + Java-Summierung (`SELECT ts_date, vals ... ANY(?)`)
  - c) Parallele Einzelreads (CompletableFuture)
  - d) Gruppiert nach Dimension (SQL pro Gruppe + Disaggregation)
- [ ] **PERF2** — PostgreSQL-Tuning evaluieren: shared_buffers, work_mem, parallel workers
- [ ] **PERF3** — Ergebnisse dokumentieren und optimalen Ansatz pro Szenario waehlen

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

### Zeitreihen-Uebersicht & Detailmaske
- [x] **ZR1** — Uebersichtsseite: Alle Zeitreihen mit Metadaten, Mehrfachauswahl → Editor oeffnen
- [ ] **ZR2** — Detailmaske: Zeitreihen-Metadaten bearbeiten (Key, Einheit, Waehrung, Objekt-Zuordnung)
- [ ] **ZR3** — Zeitreihe neu anlegen (aus Uebersicht oder Detailmaske)
- [ ] **ZR4** — Zeitreihe loeschen (Einzel + Mehrfachauswahl aus Uebersicht)
- [x] **ZR5** — On-the-fly Summierung (Cross-Dimension + Unit-Konvertierung, parallel, SQL-Shortcut)
- [x] **ZR6** — Diagramm-Ansicht (Recharts, Chart.js, Lightweight Charts zum Vergleich)
- [x] **ZR7** — Multi-Zeitreihen mit unterschiedlichen Laufzeiten (NaN-Auffuellung)
- [ ] **ZR8** — Chart-Bibliothek evaluieren und auf eine reduzieren

### Diagramm
- [ ] **CH1** — Chart-Bibliothek entscheiden (Recharts vs Chart.js vs Lightweight) und andere entfernen

### REST-API Aufräumen
- [ ] **R1** — ObjectController prüfen: Wird er gebraucht? Vermutlich nein → entfernen
- [ ] **R2** — TimeSeriesController prüfen: Nicht jede interne API soll exponiert werden → Endpoints reduzieren

### Stammdaten-Modul (JPA)
- [x] **J1** — JPA/Hibernate einrichten: `spring-boot-starter-data-jpa` Dependency, Konfiguration neben Raw JDBC
- [x] **J2** — Geschaeftspartner Entity + Repository (JpaRepository), REST-Controller
- [x] **J3** — Frontend: Stammdaten-Seite (CRUD-Maske) mit DetailPage-Template

### Public API (Drittsysteme)
- [x] **P1** — Externe REST-API unter `/public-api/...` einrichten: Eigene Controller, eigene DTOs, Basic Auth (HTTPS)
- [x] **P2** — SecurityConfig erweitern: `/public-api/**` mit Basic Auth, getrennt von Keycloak-Auth fuer `/api/**`
- [x] **P3** — Erster externer Endpoint: `POST /public-api/counterparts` (GP anlegen fuer Drittsysteme)
- [ ] **P4** — HTTPS einrichten: TLS-Zertifikat konfigurieren (entweder Spring Boot embedded via `server.ssl.*` oder TLS-Termination am Reverse Proxy)

### Scheduling / Batchplanung
- [x] **SCH1** — Backend: Quartz Scheduler, JobRegistry, AbstractBatchJob, REST-API, Demo-Job
- [x] **SCH2** — Umbau auf Template→Instanz: Job-Katalog (Code), batch_schedule (DB), Planungen + Historie als eigene Seiten, dynamisches Parameterformular, Trigger-Modus
- [ ] **SCH3** — Intervall-Schedule verbessern: Wochentage/Tage auswaehlbar (nicht nur Sekunden), Uhrzeit-Auswahl, menschenlesbare Konfiguration statt reiner Sekundenwert
- [ ] **SCH4** — Gueltigkeitszeitraum fuer Planungen: Erstes Startdatum (ab wann aktiv), Letztes Startdatum (bis wann aktiv, danach automatisch deaktivieren)
- [ ] **SCH5** — Parameter-Labels: Mapping-Tabelle fuer sprechende Feldnamen (excludePattern → "Ausschlussmuster", retentionDays → "Aufbewahrung in Tagen")

### Datenbereinigung
- [x] **B1** — Verwaiste Header loeschen: Jetzt als Batch-Job (CleanupOrphanedHeadersJob) im Scheduling-System

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
- [x] **Zeitreihen-Uebersicht** (2026-03-19): OverviewPage mit allen Metadaten, Mehrfachauswahl, Editor-Anbindung, VirtualTable-Virtualisierung
- [x] **On-the-fly Summierung** (2026-03-19): POST /api/timeseries/aggregate, Cross-Dimension-Disaggregation, Unit-Konvertierung, parallele Reads, SQL-Shortcut fuer Tag/Monat/Jahr
- [x] **Diagramm-Ansicht** (2026-03-19): 3 Chart-Bibliotheken (Recharts, Chart.js, Lightweight Charts) mit Zoom, Legende, Farben
- [x] **Tab-Persistenz** (2026-03-19): sessionStorage fuer offene Tabs bei Browser-Refresh
- [x] **Multi-Zeitreihen Laufzeiten** (2026-03-19): Unterschiedliche Laufzeiten mit NaN-Auffuellung statt Fehler
- [x] **read() fuer alle Dimensionen** (2026-03-19): Service leitet automatisch an read() oder readSimple() weiter
- [x] **Summe im Footer** (2026-03-19): Min/Max/Avg/Sum in der Werte-Tabelle
