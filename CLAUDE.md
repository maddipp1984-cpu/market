# Zeitreihensystem

## Übersicht
Performantes Zeitreihensystem für >10 Mio Zeitreihen mit TimescaleDB (PostgreSQL-Extension).
Spring Boot 3.4.x Anwendung mit dreifachem Persistenz-Ansatz: jOOQ für Abfragen/Übersichten, JPA/Hibernate für Stammdaten-CRUD, Raw JDBC (via `dsl.connection()`) für Stored Procedures/Arrays.

## Tech-Stack
- **Java 17** (LTS), Gradle mit Spring Boot Plugin
- **Spring Boot 3.4.1** (starter-web, starter-jooq, starter-data-jpa, starter-quartz)
- **jOOQ** für alle DB-Zugriffe (Codegen aus Schema, `DSLContext` als Spring Bean)
- **JPA/Hibernate** nur für Stammdaten-CRUD-Einzeloperationen (findById, create, update, delete), `ddl-auto=validate`, `open-in-view=false`
- **TimescaleDB** (PostgreSQL-Extension)
- **Regel**: Übersichtsseiten nutzen jOOQ `fetchMaps()`, Detailmasken nutzen JPA
- **Regel**: Stored Procedures und Array-Parameter nutzen `dsl.connection()` für direkten Connection-Zugriff
- **HikariCP** (via Spring auto-config)

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
- **Header-Tabelle** (`ts_header`): Metadaten (Key, Dimension, Einheit, optional Objekt-Zuordnung)
- **Objekt-Tabellen** (`ts_object`, `ts_object_type`): Übergeordnete Objekte mit 1:n zu Zeitreihen
- **Separate Werte-Tabellen** pro Dimension: Unterschiedliche Chunk-Größen/Kompression
- **TimescaleDB Hypertables** für 15min, 1h, Tag, Monat (nicht für Jahr)
- **Hash-Partitionierung** auf `ts_id` für schnellen Einzelreihen-Zugriff

### Modul-Architektur
- **`de.market`** als Basis-Package — `@SpringBootApplication` in `MarketApplication`
- **`shared`** — Gemeinsame Infrastruktur (DTOs, Services, Utilities, GlobalExceptionHandler)
- **`security`** — Auth & Berechtigungen (Keycloak, RBAC, Admin-API)
- **`config`** — Anwendungskonfiguration (Sidebar)
- **`timeseries`** — Kern: Zeitreihen-CRUD, Aggregation, Übersichten
- **`currency`** — Stammdaten-Modul: Währungen (JPA)
- **`businesspartner`** — Stammdaten-Modul: Geschäftspartner (JPA)
- **`filterpreset`** — Seitenübergreifende Filter-Presets (jOOQ, JSONB)
- **`scheduling`** — Batch-Job-System (Quartz)
- **`benchmark`** — Standalone Lese-Benchmark
- **Schichten-Regel**: `REST-Controller → Service → Repository`
- **Neue Module** folgen dem Pattern: `modul/model/`, `modul/repository/`, `modul/service/`, `modul/rest/dto/`

### Persistenz-Schichten

| Schicht | Tool | Verwendung |
|---------|------|------------|
| Stammdaten-CRUD (Detailmasken) | JPA/Hibernate | BusinessPartner, Currency, BatchSchedule |
| Übersichten + Abfragen | jOOQ DSL | Alle Repositories mit `DSLContext` |
| Stored Procedures + Arrays | Raw JDBC via `dsl.connection()` | TimeSeriesRepository (Write/Read/Aggregation) |
| Codegen | jOOQ Codegen (Gradle Plugin) | `src/generated/java/de/market/jooq/generated/` |

### Stammdaten-Services
- Alle Stammdaten-Services erben von `AbstractCrudService<D, E, ID>` (in `shared.service`)
- Erzwingt `validate(D dto)`, `toDto(E entity)`, `toEntity(D dto)` als abstrakte Methoden
- Neue Stammdaten-Module müssen dieses Pattern übernehmen

### REST-API
| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| POST | `/api/timeseries` | Zeitreihe anlegen |
| GET | `/api/timeseries/{tsId}` | Header lesen |
| GET | `/api/timeseries?key=...` | Header per Key |
| POST | `/api/timeseries/{tsId}/values` | Tag schreiben (QH/H Array) |
| POST | `/api/timeseries/{tsId}/value` | Einzelwert schreiben (Tag/Monat/Jahr) |
| GET | `/api/timeseries/{tsId}/values?start=...&end=...` | Werte lesen |
| DELETE | `/api/timeseries/{tsId}` | Zeitreihe löschen |
| POST | `/api/timeseries/aggregate` | Zeitreihen summieren (Cross-Dim/Unit) |
| GET | `/api/timeseries-overview` | Zeitreihen-Übersicht (TableResponse) |
| POST | `/api/timeseries-overview/query` | Zeitreihen filtern |
| POST | `/api/objects` | Objekt anlegen |
| GET | `/api/objects/{objectId}` | Objekt lesen |
| PUT | `/api/objects/{objectId}/timeseries/{tsId}` | Zuordnung |
| DELETE | `/api/objects/{objectId}` | Objekt löschen |
| GET | `/api/config/sidebar` | Sidebar-Baumstruktur (aus XML) |
| GET | `/api/business-partners` | GP-Liste (TableResponse) |
| GET | `/api/business-partners/{id}` | GP lesen (mit Ansprechpartnern) |
| POST | `/api/business-partners` | GP anlegen |
| PUT | `/api/business-partners/{id}` | GP aktualisieren |
| DELETE | `/api/business-partners/{id}` | GP löschen |
| GET | `/api/currencies` | Währungsliste (TableResponse) |
| GET | `/api/currencies/{id}` | Währung lesen |
| POST | `/api/currencies` | Währung anlegen |
| PUT | `/api/currencies/{id}` | Währung aktualisieren |
| DELETE | `/api/currencies/{id}` | Währung löschen |
| POST | `/api/currencies/query` | Währungen filtern |
| GET | `/api/filter-presets?pageKey=...` | Filter-Presets laden |
| POST | `/api/filter-presets` | Preset anlegen |
| PUT | `/api/filter-presets/{id}` | Preset aktualisieren |
| DELETE | `/api/filter-presets/{id}` | Preset löschen |
| PUT | `/api/filter-presets/{id}/default` | Als Standard setzen |
| GET | `/api/batch-jobs/catalog` | Job-Katalog (verfügbare Job-Typen) |
| GET | `/api/batch-schedules` | Schedule-Übersicht (TableResponse) |
| GET | `/api/batch-schedules/{id}` | Schedule lesen |
| POST | `/api/batch-schedules` | Schedule anlegen |
| PUT | `/api/batch-schedules/{id}` | Schedule aktualisieren |
| DELETE | `/api/batch-schedules/{id}` | Schedule löschen |
| POST | `/api/batch-schedules/{id}/trigger` | Manuell auslösen (opt. Parameter) |
| GET | `/api/batch-history` | Alle Ausführungen (TableResponse) |
| GET | `/api/batch-history/{execId}/log` | Logfile-Inhalt |
| GET | `/api/permissions/me` | Eigene effektive Berechtigungen |
| GET | `/api/admin/users` | Benutzerliste (Keycloak) |
| POST | `/api/admin/users` | Benutzer anlegen |
| PUT | `/api/admin/users/{id}/admin` | Admin-Status setzen |
| PUT | `/api/admin/users/{id}/enabled` | Benutzer aktivieren/deaktivieren |
| PUT | `/api/admin/users/{id}/password` | Passwort setzen |
| GET | `/api/admin/users/{id}/effective` | Effektive Berechtigungen eines Users |
| GET | `/api/admin/groups` | Gruppen-Liste |
| GET | `/api/admin/groups/{id}` | Gruppe lesen (mit Mitgliedern + Permissions) |
| POST | `/api/admin/groups` | Gruppe anlegen |
| PUT | `/api/admin/groups/{id}` | Gruppe aktualisieren |
| DELETE | `/api/admin/groups/{id}` | Gruppe löschen |
| POST | `/api/admin/groups/{id}/members` | Mitglied hinzufügen |
| DELETE | `/api/admin/groups/{id}/members/{userId}` | Mitglied entfernen |
| PUT | `/api/admin/groups/{id}/permissions` | Gruppen-Berechtigungen setzen |
| PUT | `/api/admin/groups/{id}/field-restrictions` | Feld-Restriktionen setzen |
| GET | `/api/admin/resources` | Verfügbare Ressourcen-Definitionen |

### Exception Handling (shared.rest.GlobalExceptionHandler)
- `IllegalArgumentException` → 400 Bad Request
- `IllegalStateException` → 409 Conflict
- `DataAccessException` (jOOQ) → 500 Internal Server Error
- `SQLException` → 500 Internal Server Error (Fallback)

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
- **HikariCP**: Pool-Size 30 (PostgreSQL max_connections=100)
- **Aggregation gleiche Dim+Unit**: PL/pgSQL Stored Procedures (`ts_sum_15min`, `ts_sum_1h`) — Summierung komplett in DB
- **Aggregation Cross-Dim/Unit**: Parallele Java-Reads mit dediziertem ExecutorService (nicht ForkJoinPool!)
- **Hypertable Hash-Partitionierung**: `ANY(array)` ist langsamer als parallele Einzelabfragen für QH/H
- **Bulk-Header**: `HeaderRepository.findByIds()` statt N Einzelabfragen bei Aggregation
- **VirtualTable**: TanStack Virtual für Übersichtsseiten (nur sichtbare Zeilen im DOM)

## Projektstruktur
```
src/main/java/de/market/
    MarketApplication.java                 -- @SpringBootApplication
    shared/
        dto/
            TableResponse.java             -- Generische Tabellenantwort
            ColumnMeta.java                -- Spalten-Metadaten
            FilterCondition.java           -- Filter-Bedingung
            FilterRequest.java             -- Filter-Request
            JooqFilterBuilder.java         -- jOOQ Condition-Builder (typsicher)
        service/
            AbstractCrudService.java       -- Basis für Stammdaten-Services
        rest/
            GlobalExceptionHandler.java    -- @RestControllerAdvice
        EnumParser.java                    -- Utility: String→Enum Parsing
    security/                              -- Auth & Berechtigungen (eigenständiges Modul)
        SecurityConfig.java                -- @Configuration, Spring Security + Keycloak
        KeycloakAdminClient.java           -- Keycloak Admin REST API Client
        AdminController.java               -- @RestController /api/admin
        PermissionController.java          -- @RestController /api/permissions
        PermissionService.java             -- @Service, RBAC-Logik
        UserRegistrationFilter.java        -- Auto-Registrierung bei erstem Login
        UserSessionLogFilter.java          -- Session-Logging
        SecurityUtils.java                 -- Hilfsmethoden (aktueller User etc.)
        AuthUser.java                      -- POJO: Benutzer
        AuthGroup.java                     -- POJO: Gruppe
        AuthPermission.java                -- POJO: Berechtigung
        AuthFieldRestriction.java          -- POJO: Feld-Restriktion
        AuthResource.java                  -- POJO: Ressourcen-Definition
        EffectivePermission.java           -- Berechnete Berechtigungen
        AuthUserRepository.java            -- @Repository (jOOQ)
        AuthGroupRepository.java           -- @Repository (jOOQ)
        AuthPermissionRepository.java      -- @Repository (jOOQ)
        AuthResourceRepository.java        -- @Repository (jOOQ)
    config/
        ConfigController.java              -- @RestController /api/config (Sidebar)
    currency/                              -- Stammdaten-Modul (JPA + jOOQ)
        model/
            CurrencyEntity.java            -- @Entity auf ts_currency
        repository/
            CurrencyJpaRepository.java     -- JpaRepository (Einzel-CRUD)
            CurrencyOverviewRepository.java -- jOOQ für Übersicht
        service/
            CurrencyService.java           -- @Service extends AbstractCrudService
        rest/
            CurrencyController.java        -- @RestController /api/currencies
            dto/
                CurrencyDto.java           -- Request/Response DTO
    businesspartner/                       -- Stammdaten-Modul (JPA + jOOQ)
        model/
            BusinessPartner.java           -- @Entity, @OneToMany cascade ALL
            ContactPerson.java             -- @Entity, @ElementCollection Funktionen
            ContactFunction.java           -- Enum: ABRECHNUNG, BK_VERANTWORTLICHER
        repository/
            BusinessPartnerRepository.java -- JpaRepository (Einzel-CRUD)
            BusinessPartnerOverviewRepository.java -- jOOQ für Übersicht
        service/
            BusinessPartnerService.java    -- @Service extends AbstractCrudService
        rest/
            BusinessPartnerController.java -- @RestController /api/business-partners
            dto/
                BusinessPartnerDto.java    -- Request/Response DTO
                ContactPersonDto.java      -- Ansprechpartner DTO
    filterpreset/                          -- Filter-Presets (seitenübergreifend, jOOQ + JSONB)
        model/
            FilterPreset.java              -- POJO
        repository/
            FilterPresetRepository.java    -- @Repository (jOOQ, JSONB)
        rest/
            FilterPresetController.java    -- @RestController /api/filter-presets
            dto/
                CreateFilterPresetRequest.java
                FilterPresetResponse.java
    timeseries/                            -- Kern: Zeitreihen (jOOQ + Stored Procedures)
        api/
            TimeSeriesService.java         -- @Service, öffentliche Fassade
            AggregationService.java        -- @Service, Summierung/Konvertierung
        client/
            TimeSeriesClient.java          -- @Component, Entwickler-API mit Konvertierung
            DimensionConverter.java        -- Aggregation/Disaggregation
            UnitConverter.java             -- Unit-Konvertierung
            AggregationFunction.java       -- Enum: SUM, AVG, MIN, MAX
        model/
            TimeDimension.java             -- Enum mit Tabellen-Mapping
            TimeSeriesHeader.java          -- Metadaten-Modell (inkl. objectId)
            TimeSeriesSlice.java           -- Tages-Slices mit lazy Timestamps
            ObjectType.java                -- Enum: Objekttypen
            TsObject.java                  -- Übergeordnetes Objekt
            Unit.java                      -- Enum: physikalische Einheiten
            Currency.java                  -- Enum: Währungen (Legacy)
        repository/
            HeaderRepository.java          -- @Repository (jOOQ), CRUD ts_header
            ObjectRepository.java          -- @Repository (jOOQ), CRUD ts_object
            TimeSeriesRepository.java      -- @Repository (jOOQ + dsl.connection()), Lesen/Schreiben/Löschen
            TimeSeriesOverviewRepository.java -- @Repository (jOOQ), Übersicht
        rest/
            TimeSeriesController.java      -- @RestController /api/timeseries + /aggregate
            TimeSeriesOverviewController.java -- @RestController /api/timeseries-overview
            ObjectController.java          -- @RestController /api/objects
            dto/                           -- Request/Response DTOs
    scheduling/                            -- Batch-Job-System (Quartz + jOOQ)
        config/
            QuartzConfig.java              -- @Configuration, SchedulerFactoryBean
            AutowiringSpringBeanJobFactory.java -- Autowiring in Quartz-Jobs
        model/
            ScheduleType.java              -- Enum: NONE, CRON, INTERVAL
            JobStatus.java                 -- Enum: RUNNING, COMPLETED, FAILED
            JobResult.java                 -- Record: recordsAffected + message
            JobParameterType.java          -- Enum: STRING, INTEGER, BOOLEAN, DATE, ENUM, PATTERN
            JobParameter.java              -- Parameter-Definition für Job-Typen
            BatchScheduleEntity.java       -- @Entity auf batch_schedule
        repository/
            BatchScheduleJpaRepository.java -- JpaRepository (Einzel-CRUD)
            ScheduleOverviewRepository.java -- jOOQ für Übersicht
            JobExecutionLogRepository.java -- jOOQ für Historie
        service/
            SchedulingService.java         -- @Service, Katalog + Schedule-CRUD + Quartz
            JobRegistry.java               -- @Component, Startup-Sync
        rest/
            SchedulingController.java      -- @RestController /api/batch-schedules + /api/batch-history
            dto/
                BatchScheduleDto.java      -- Schedule Request/Response DTO
                JobCatalogDto.java         -- Job-Katalog DTO
        jobs/
            AbstractBatchJob.java          -- Abstrakte Basisklasse (mit Parameter-System)
            QuartzJobAdapter.java          -- Quartz→AbstractBatchJob Bridge
            CleanupOrphanedHeadersJob.java -- Demo-Job
    benchmark/
        Benchmark.java                     -- Standalone Lese-Benchmark
src/generated/java/de/market/jooq/generated/ -- jOOQ Codegen (aus DB-Schema)
    tables/                                -- Table-Referenzen
    tables/records/                        -- Record-Typen
    routines/                              -- Stored Procedures
    Keys.java, Indexes.java, Public.java   -- Schema-Metadaten
src/main/resources/
    application.properties                 -- Spring-Config (DB, HikariCP, jOOQ-Dialekt)
sql/
    schema.sql                         -- Komplettes DB-Schema
    procedures/                        -- Stored Procedures
    migrations/                        -- Nummerierte Schema-Migrationen
benchmarks/
    YYYY-MM-DD_beschreibung.md         -- Benchmark-Ergebnisse
```

## Changelog
- Nach jedem abgeschlossenen Feature/Umbau: Eintrag in `DONE.md` mit Datum und Zusammenfassung

## Berechtigungen
- Alle `./gradlew`-Befehle dürfen ohne Rückfrage ausgeführt werden
- Alle `git`-Befehle (add, commit, push, status, diff, log etc.) dürfen ohne Rückfrage ausgeführt werden
- Alle `bash`-Befehle (Scripts, Shell-Kommandos) dürfen ohne Rückfrage ausgeführt werden
- Alle `docker exec`-Befehle (DB-Zugriff, Container-Kommandos) dürfen ohne Rückfrage ausgeführt werden

## Build & Run
```bash
./gradlew build                        # Kompilieren + Tests
./gradlew bootRun                      # Spring Boot starten (Port 8080)
./gradlew bootJar                      # Fat-JAR erstellen
./gradlew generateJooq                 # jOOQ Codegen (nach Schema-Änderungen)
./gradlew benchmark                    # Standalone Benchmark

# DB-Config via Umgebungsvariablen oder application.properties
TS_JDBC_URL=jdbc:postgresql://localhost:5432/timeseries
TS_DB_USER=postgres
TS_DB_PASSWORD=postgres
```

## jOOQ
- **Codegen**: Gradle Plugin `nu.studer.jooq`, generiert nach `src/generated/java/`
- **Dialekt**: `spring.jooq.sql-dialect=POSTGRES` (für Oracle: `ORACLE`)
- **Spring-Integration**: `spring-boot-starter-jooq` liefert `DSLContext`-Bean + Transaction-Integration
- **Generierte Klassen**: Werden committed (kein DB-Container für Build nötig)
- **Filter**: `JooqFilterBuilder.build(conditions, allowedColumns)` → jOOQ `Condition`
- **Stored Procedures**: Aufrufe via `dsl.connection(conn -> { ... })` für direkten Connection-Zugriff

## Benchmark
- **Code:** `src/main/java/de/market/benchmark/Benchmark.java`
- **Aufruf:** `./gradlew benchmark`
- **Standalone** — erstellt eigenen HikariDataSource, kein Spring-Kontext
- **Testdaten:** 120.000 PERF_TEST-Zeitreihen (PERF_TEST_00001–300000) in der DB, nicht löschen!
- **Ergebnisse:** `benchmarks/` – Dateien nach Schema `YYYY-MM-DD_beschreibung.md`
- Nur Lese-Benchmarks gegen existierende PERF_TEST-Daten (kein Schreiben/Cleanup)

## Dependencies (via Spring Boot BOM)
- Spring Boot 3.4.1 (Web, jOOQ, JPA, JDBC, Quartz, Security, OAuth2)
- jOOQ (Version via Spring Boot BOM)
- PostgreSQL JDBC (Version via BOM)
- HikariCP (Version via BOM)
- SLF4J + Logback (via Spring Boot)
- JUnit 5 + Mockito (via spring-boot-starter-test)

## Frontend-Features
- **Tab-Persistenz**: Offene Tabs werden in `sessionStorage` gespeichert, überleben Browser-Refresh (F5)
- **Diagramme**: 3 Chart-Bibliotheken zum Vergleich (Recharts, Chart.js, Lightweight Charts), umschaltbar per Dropdown
- **Chart-Downsampling**: Gemeinsame Utility `chart/chartUtils.ts` (max 5000 Punkte), Typen/Farben in `chart/chartTypes.ts`
- **Zeitreihen-Übersicht**: OverviewPage mit Mehrfachauswahl → Editor öffnen oder Summieren
- **Aggregation**: Kontextaktion "Summieren" öffnet Editor mit on-the-fly summierter Zeitreihe (read-only)
- **Multi-Zeitreihen**: Unterschiedliche Laufzeiten werden mit NaN aufgefüllt (kein Fehler mehr)

## Gradle-Konfiguration
- `gradle.properties`: `org.gradle.java.home` zeigt auf JDK 21 (unter `~/.jdks/jdk-21.0.10+7`)
- Spring Boot Plugin baut Fat-JAR (kein Shadow-Plugin mehr)
- jOOQ Codegen Plugin: `nu.studer.jooq` v9.0

## Gotchas
- **Git Bash + Docker**: `MSYS_NO_PATHCONV=1` vor `docker exec` setzen, sonst werden Unix-Pfade in Windows-Pfade konvertiert
- **TreeView (Headless Tree)**: `useTree` reagiert nicht auf Datenänderungen — `tree.rebuildTree()` aufrufen, nicht remounten
- **Sidebar-Fallback**: `sidebarTree.ts` (Frontend) muss manuell mit `sidebar.xml` (Backend) synchron gehalten werden
- **jOOQ Codegen**: Nach Schema-Änderungen `./gradlew generateJooq` ausführen und generierte Klassen committen
- **Stored Procedures in TimeSeriesRepository**: Nutzen `dsl.connection()` für Array-Parameter — nicht auf jOOQ DSL umstellbar (PostgreSQL-spezifisch)
