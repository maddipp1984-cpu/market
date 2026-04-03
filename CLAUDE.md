# Zeitreihensystem

## Übersicht
Performantes Zeitreihensystem für >10 Mio Zeitreihen mit TimescaleDB (PostgreSQL-Extension).
Spring Boot 3.4.x Anwendung mit dreifachem Persistenz-Ansatz: jOOQ für Abfragen/Übersichten, JPA/Hibernate für Stammdaten-CRUD, Raw JDBC (via `dsl.connection()`) für Stored Procedures/Arrays.

### Deployment
- **On-Premise** beim Kunden, innerhalb des internen Netzwerks — nicht aus dem Internet erreichbar
- **Interne API** (`/api/**`): Frontend-zu-Backend, Keycloak OAuth2
- **Public API** (`/public-api/**`): Für andere interne Anwendungen des Kunden, Basic Auth über HTTPS
- HTTPS wird empfohlen (auch intern), ist auf Anwendungsebene transparent (Reverse Proxy / TLS-Termination)

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
- **TimescaleDB Hypertables** für 15min, 1h, Tag, Monat (nicht für Jahr), Range-only auf `ts_date`

### Modul-Architektur
- **`de.market`** als Basis-Package — `@SpringBootApplication` in `MarketApplication`
- **`shared`** — Gemeinsame Infrastruktur (DTOs, Services, Utilities, GlobalExceptionHandler)
- **`security`** — Auth & Berechtigungen (Keycloak, RBAC, Admin-API)
- **`config`** — Anwendungskonfiguration (Sidebar)
- **`timeseries`** — Kern: Zeitreihen-CRUD, Aggregation, Übersichten
- **`currency`** — Stammdaten-Modul: Währungen (JPA)
- **`seriestype`** — Stammdaten-Modul: Reihenarten (JPA, Kategorie: Finanziell/Physikalisch)
- **`commoditygroup`** — Stammdaten-Modul: Warengruppen (JPA)
- **`businesspartner`** — Stammdaten-Modul: Geschäftspartner (JPA)
- **`filterpreset`** — Seitenübergreifende Filter-Presets (jOOQ, JSONB)
- **`scheduling`** — Batch-Job-System (Quartz)
- **`publicapi`** — Public REST-API für Drittsysteme (Basic Auth, eigene DTOs)
- **`index`** — Stammdaten-Modul: Preisindices (JPA + jOOQ, 1:1 Zeitreihe)
- **`benchmark`** — Standalone Lese-Benchmark
- **Schichten-Regel**: `REST-Controller → Service → Repository`
- **Neue Module** folgen dem Pattern: `modul/model/`, `modul/repository/`, `modul/service/`, `modul/rest/dto/`

### Persistenz-Schichten

| Schicht | Tool | Verwendung |
|---------|------|------------|
| Stammdaten-CRUD (Detailmasken) | JPA/Hibernate | BusinessPartner, Currency, SeriesType, CommodityGroup, BatchSchedule |
| Übersichten + Abfragen | jOOQ DSL | Alle Repositories mit `DSLContext` |
| Stored Procedures + Arrays | Raw JDBC via `dsl.connection()` | TimeSeriesRepository (Write/Read/Aggregation) |
| Codegen | jOOQ Codegen (Gradle Plugin) | `src/generated/java/de/market/jooq/generated/` |

### Stammdaten-Services
- Alle Stammdaten-Services erben von `AbstractCrudService<D, E, ID>` (in `shared.service`)
- Erzwingt `validate(D dto)`, `toDto(E entity)`, `toEntity(D dto)` als abstrakte Methoden
- Neue Stammdaten-Module müssen dieses Pattern übernehmen

### REST-API
Alle Endpoints folgen dem Pattern `/api/{modul}` mit Standard-CRUD (GET, POST, PUT, DELETE).

| Modul | Basis-Pfad | Besonderheiten |
|-------|-----------|----------------|
| Zeitreihen | `/api/timeseries` | `/{tsId}/values` (Array), `/{tsId}/value` (Einzelwert), `/aggregate` |
| Zeitreihen-Übersicht | `/api/timeseries-overview` | `/query` (POST mit Filter) |
| Objekte | `/api/objects` | `/{objectId}/timeseries/{tsId}` (Zuordnung) |
| Geschäftspartner | `/api/business-partners` | Standard-CRUD |
| Währungen | `/api/currencies` | `/query` (POST mit Filter) |
| Reihenarten | `/api/series-types` | `/query` (POST mit Filter), Kategorie: 1=Finanziell, 2=Physikalisch |
| Warengruppen | `/api/commodity-groups` | `/query` (POST mit Filter) |
| Indices | `/api/indices` | `/query` (POST mit Filter), 1:1 Zeitreihe, Kontextmenü mit Editor |
| Filter-Presets | `/api/filter-presets` | `/{id}/default` (PUT) |
| Batch-Schedules | `/api/batch-schedules` | `/{id}/trigger` (POST, manuell auslösen) |
| Batch-Historie | `/api/batch-history` | `/{execId}/log` (Logfile) |
| Job-Katalog | `/api/batch-jobs/catalog` | Nur GET (verfügbare Job-Typen) |
| Config | `/api/config/sidebar` | Sidebar-Baumstruktur (aus XML) |
| Berechtigungen | `/api/permissions/me` | Eigene effektive Berechtigungen |
| Admin (Users) | `/api/admin/users` | `/{id}/admin`, `/{id}/enabled`, `/{id}/password`, `/{id}/effective` |
| Admin (Groups) | `/api/admin/groups` | `/{id}/members`, `/{id}/permissions`, `/{id}/field-restrictions` |
| Admin (Resources) | `/api/admin/resources` | Nur GET (Ressourcen-Definitionen) |
| **Public API** | `/public-api/counterparts` | POST (Basic Auth, für Drittsysteme) |

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
- **Hypertable Range-Only**: Hash-Partitionierung entfernt (Single-Node), nur Range auf ts_date
- **Overview-Performance**: first_date/last_date direkt in ts_header statt UNION ALL CTE
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
        repository/
            AbstractOverviewRepository.java -- Basis für Übersichts-Repositories (jOOQ)
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
    seriestype/                            -- Stammdaten-Modul: Reihenarten (JPA + jOOQ)
        model/
            SeriesTypeEntity.java          -- @Entity auf ts_series_type
            SeriesCategory.java            -- Enum: FINANCIAL, PHYSICAL
        repository/
            SeriesTypeJpaRepository.java   -- JpaRepository (Einzel-CRUD)
            SeriesTypeOverviewRepository.java -- jOOQ für Übersicht (Kategorie-Label per CASE)
        service/
            SeriesTypeService.java         -- @Service extends AbstractCrudService
        rest/
            SeriesTypeController.java      -- @RestController /api/series-types
            dto/
                SeriesTypeDto.java         -- Request/Response DTO
    commoditygroup/                        -- Stammdaten-Modul: Warengruppen (JPA + jOOQ)
        model/
            CommodityGroupEntity.java      -- @Entity auf ts_commodity_group
        repository/
            CommodityGroupJpaRepository.java -- JpaRepository (Einzel-CRUD)
            CommodityGroupOverviewRepository.java -- jOOQ für Übersicht
        service/
            CommodityGroupService.java     -- @Service extends AbstractCrudService
        rest/
            CommodityGroupController.java  -- @RestController /api/commodity-groups
            dto/
                CommodityGroupDto.java     -- Request/Response DTO
    businesspartner/                       -- Stammdaten-Modul (JPA + jOOQ)
        model/
            BusinessPartner.java           -- @Entity, @OneToMany cascade ALL
            ContactPerson.java             -- @Entity, @ElementCollection Funktionen
            ContactFunction.java           -- Enum: ABRECHNUNG, BK_VERANTWORTLICHER
            SystemCompanyEntry.java        -- Record: Systemfirma-Cache-Eintrag
        repository/
            BusinessPartnerRepository.java -- JpaRepository (Einzel-CRUD)
            BusinessPartnerOverviewRepository.java -- jOOQ für Übersicht (inkl. system_rank)
        service/
            BusinessPartnerService.java    -- @Service extends AbstractCrudService
            SystemCompanyService.java      -- @Service, Startup-Cache für Systemfirmen (@PostConstruct)
        rest/
            BusinessPartnerController.java -- @RestController /api/business-partners
            dto/
                BusinessPartnerDto.java    -- Request/Response DTO
                ContactPersonDto.java      -- Ansprechpartner DTO
    index/                                 -- Stammdaten-Modul: Preisindices (JPA + jOOQ)
        model/
            IndexEntity.java               -- @Entity auf ts_index
        repository/
            IndexJpaRepository.java        -- JpaRepository (Einzel-CRUD)
            IndexOverviewRepository.java   -- jOOQ für Übersicht (JOIN ts_index + ts_object + ts_header)
        service/
            IndexService.java              -- @Service extends AbstractCrudService
        rest/
            IndexController.java           -- @RestController /api/indices
            dto/
                IndexDto.java              -- Request/Response DTO
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
            TimeSeriesRepository.java      -- @Repository (jOOQ DSL), delegiert DB-spezifisches an Procedures
            TimeSeriesProcedures.java      -- Interface: DB-spezifische Operationen (Procedures, Arrays)
            PostgresTimeSeriesProcedures.java -- @Profile("!oracle"), PL/pgSQL + PostgreSQL-Arrays
            OracleTimeSeriesProcedures.java -- @Profile("oracle"), PL/SQL (Stub)
            TimeSeriesOverviewRepository.java -- @Repository (jOOQ DSL), Übersicht mit CTE
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
    publicapi/                             -- Public REST-API für Drittsysteme
        PublicApiProperties.java           -- @ConfigurationProperties (Credentials)
        PublicApiSecurityConfig.java       -- @Configuration, Basic Auth FilterChain
        PublicApiExceptionHandler.java     -- @RestControllerAdvice (eigenes Error-Format)
        counterpart/
            CounterpartController.java     -- @RestController /public-api/counterparts
            dto/
                CreateCounterpartRequest.java
                CounterpartResponse.java
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
- **DB-Abstraktion**: `TimeSeriesProcedures` Interface + `@Profile`-Implementierungen (PostgreSQL aktiv, Oracle Stub)
- **SQL→jOOQ Workflow**: Query im SQL Developer entwickeln → auf https://www.jooq.org/translate/ übersetzen → jOOQ DSL ins Repository einfügen
- **Raw SQL Escape-Hatch**: Jederzeit möglich via `dsl.resultQuery("SELECT ...", params)` oder `dsl.connection(conn -> { ... })` für JDBC-Zugriff

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

## Frontend
Siehe `frontend/CLAUDE.md` für vollständige Frontend-Dokumentation (React 18, Tab-System, Templates, Konventionen).

Wichtigste Features: Tab-Persistenz (sessionStorage), 3 Chart-Bibliotheken (umschaltbar), OverviewPage mit Mehrfachauswahl + Aggregation, VirtualTable für 100k+ Zeilen.

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
