# TODO - Zeitreihensystem

## Offen

### QueryRegistry
- [x] **Q1** — Migration `007_query_registry.sql` ausfuehren (Docker starten, dann Migration)
- [ ] **Q2** — Bestehende SQL-Statements schrittweise in XML migrieren (Objects, Timeseries etc.)

### Umbenennung
- [x] **U1** — Projekt von "timeseries" auf "market" umbenennen (Package, Verzeichnis, Gradle, Docker, Git-Remote etc.)

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

### Stammdaten-Modul (JPA)
- [x] **J1** — JPA/Hibernate einrichten: `spring-boot-starter-data-jpa` Dependency, Konfiguration neben Raw JDBC
- [x] **J2** — Geschaeftspartner Entity + Repository (JpaRepository), REST-Controller
- [x] **J3** — Frontend: Stammdaten-Seite (CRUD-Maske) mit DetailPage-Template

### Public API (Drittsysteme)
- [ ] **P1** — Externe REST-API unter `/public-api/...` einrichten: Eigene Controller, eigene DTOs, Basic Auth (HTTPS)
- [ ] **P2** — SecurityConfig erweitern: `/public-api/**` mit Basic Auth, getrennt von Keycloak-Auth fuer `/api/**`
- [ ] **P3** — Erster externer Endpoint: `POST /public-api/business-partners` (GP anlegen fuer Drittsysteme)

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
