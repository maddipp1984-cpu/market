# Zeitreihensystem

## Übersicht
Performantes Zeitreihensystem für >10 Mio Zeitreihen mit TimescaleDB (PostgreSQL-Extension).
Spring Boot 3.4.x Anwendung mit dualem Persistenz-Ansatz: Raw JDBC für Zeitreihen, JPA/Hibernate für Stammdaten.

## Tech-Stack
- **Java 17** (LTS), Gradle mit Spring Boot Plugin
- **Spring Boot 3.4.1** (starter-web, starter-jdbc, starter-quartz)
- **TimescaleDB** (PostgreSQL-Extension)
- **Raw JDBC** für Timeseries-Zugriff (Performance) und **alle Übersichts-Abfragen** (auch Stammdaten)
- **JPA/Hibernate** für Stammdaten-CRUD-Einzeloperationen (findById, create, update, delete), `ddl-auto=validate`, `open-in-view=false`
- **Regel**: Übersichtsseiten (Tabellen mit vielen Zeilen) nutzen IMMER Raw JDBC via `DataSource`, niemals JPA `findAll()`
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

### Spring Boot Architektur & Schichten
- **`de.market`** als Basis-Package — `@SpringBootApplication` in `MarketApplication`
- **`shared.dto`** — Gemeinsame DTOs (TableResponse, ColumnMeta, Filter*)
- **`shared.query`** — QueryRegistry, QueryLoader, QueryController
- **`timeseries.rest`** — REST-Controller + DTOs + GlobalExceptionHandler
- **`timeseries.api`** — `@Service` TimeSeriesService (Fassade)
- **`timeseries.repository`** — `@Repository` mit Raw JDBC über `DataSource`
- **`timeseries.client`** — `@Component` TimeSeriesClient (Entwickler-API mit Konvertierung)
- **`timeseries.model`** — POJOs + Enums (keine Spring-Annotationen)
- **`currency`** — Währungs-CRUD (JPA Entity auf `ts_currency`, REST `/api/currencies`)
- **`scheduling`** — Batch-Job-System (Quartz Scheduler, REST `/api/batch-jobs`)
- **Schichten-Regel**: `REST-Controller → Service → Repository`

### REST-API
| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| POST | `/api/timeseries` | Zeitreihe anlegen |
| GET | `/api/timeseries/{tsId}` | Header lesen |
| GET | `/api/timeseries?key=...` | Header per Key |
| POST | `/api/timeseries/{tsId}/values` | Tag schreiben |
| GET | `/api/timeseries/{tsId}/values?start=...&end=...` | Werte lesen |
| DELETE | `/api/timeseries/{tsId}` | Zeitreihe löschen |
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
| GET | `/api/batch-jobs/catalog` | Job-Katalog (verfügbare Job-Typen) |
| GET | `/api/batch-schedules` | Schedule-Übersicht (TableResponse) |
| GET | `/api/batch-schedules/{id}` | Schedule lesen |
| POST | `/api/batch-schedules` | Schedule anlegen |
| PUT | `/api/batch-schedules/{id}` | Schedule aktualisieren |
| DELETE | `/api/batch-schedules/{id}` | Schedule löschen |
| POST | `/api/batch-schedules/{id}/trigger` | Manuell auslösen (opt. Parameter) |
| GET | `/api/batch-history` | Alle Ausführungen (TableResponse) |
| GET | `/api/batch-history/{execId}/log` | Logfile-Inhalt |

### Exception Handling (GlobalExceptionHandler)
- `IllegalArgumentException` → 400 Bad Request
- `IllegalStateException` → 409 Conflict
- `SQLException` → 500 Internal Server Error

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
            FilterQueryBuilder.java        -- WHERE-Clause-Builder
        query/
            QueryRegistry.java             -- @Component, SQL aus DB laden
            QueryLoader.java               -- @Component, XML→DB Sync
            QueryController.java           -- @RestController /api/admin/queries
    currency/                              -- Stammdaten-Modul (JPA)
        model/
            CurrencyEntity.java            -- @Entity auf ts_currency
        repository/
            CurrencyJpaRepository.java     -- JpaRepository
            CurrencyOverviewRepository.java -- Raw JDBC für Übersicht
        service/
            CurrencyService.java           -- @Service, Validierung, DTO-Mapping
        rest/
            CurrencyController.java        -- @RestController /api/currencies
            dto/
                CurrencyDto.java           -- Request/Response DTO
    timeseries/
        api/
            TimeSeriesService.java         -- @Service, öffentliche Fassade
        client/
            TimeSeriesClient.java          -- @Component, Entwickler-API mit Konvertierung
            DimensionConverter.java         -- Aggregation/Disaggregation
            UnitConverter.java             -- Unit-Konvertierung
            AggregationFunction.java       -- Enum: SUM, AVG, MIN, MAX
        model/
            TimeDimension.java             -- Enum mit Tabellen-Mapping
            TimeSeriesHeader.java          -- Metadaten-Modell (inkl. objectId)
            TimeSeriesSlice.java           -- Tages-Slices mit lazy Timestamps
            ObjectType.java                -- Enum: Objekttypen
            TsObject.java                  -- Übergeordnetes Objekt
            Unit.java                      -- Enum: physikalische Einheiten
            Currency.java                  -- Enum: Währungen (Legacy, wird durch CurrencyEntity ersetzt)
        repository/
            HeaderRepository.java          -- @Repository, CRUD ts_header
            ObjectRepository.java          -- @Repository, CRUD ts_object
            TimeSeriesRepository.java      -- @Repository, Lesen/Schreiben/Löschen
        rest/
            TimeSeriesController.java      -- @RestController /api/timeseries
            ObjectController.java          -- @RestController /api/objects
            GlobalExceptionHandler.java    -- @RestControllerAdvice
            dto/                           -- Request/Response DTOs
    businesspartner/                       -- Stammdaten-Modul (JPA)
        model/
            BusinessPartner.java           -- @Entity, @OneToMany cascade ALL
            ContactPerson.java             -- @Entity, @ElementCollection Funktionen
            ContactFunction.java           -- Enum: ABRECHNUNG, BK_VERANTWORTLICHER
        repository/
            BusinessPartnerRepository.java -- JpaRepository (single repo, cascade)
        service/
            BusinessPartnerService.java    -- @Service, Validierung, DTO-Mapping
        rest/
            BusinessPartnerController.java -- @RestController /api/business-partners
            dto/
                BusinessPartnerDto.java    -- Request/Response DTO
                ContactPersonDto.java      -- Ansprechpartner DTO
    scheduling/                           -- Batch-Job-System (Quartz, Template→Instanz)
        config/
            QuartzConfig.java              -- @Configuration, SchedulerFactoryBean
            AutowiringSpringBeanJobFactory.java -- Autowiring in Quartz-Jobs
        model/
            ScheduleType.java              -- Enum: NONE, CRON, INTERVAL
            JobStatus.java                 -- Enum: RUNNING, COMPLETED, FAILED
            JobResult.java                 -- Record: recordsAffected + message
            JobParameterType.java          -- Enum: STRING, INTEGER, BOOLEAN, DATE, ENUM, PATTERN
            JobParameter.java              -- Parameter-Definition für Job-Typen
            BatchScheduleEntity.java       -- @Entity auf batch_schedule (n Planungen pro Job-Typ)
        repository/
            BatchScheduleJpaRepository.java -- JpaRepository (Einzel-CRUD)
            ScheduleOverviewRepository.java -- Raw JDBC für Übersicht
            JobExecutionLogRepository.java -- Raw JDBC für Historie
        service/
            SchedulingService.java         -- @Service, Katalog + Schedule-CRUD + Quartz
            JobRegistry.java               -- @Component, Startup-Sync (nur Validierung)
        rest/
            SchedulingController.java      -- @RestController /api/batch-schedules + /api/batch-history
            dto/
                BatchScheduleDto.java      -- Schedule Request/Response DTO
                JobCatalogDto.java         -- Job-Katalog DTO (mit Parameter-Definitionen)
        jobs/
            AbstractBatchJob.java          -- Abstrakte Basisklasse (mit Parameter-System)
            QuartzJobAdapter.java          -- Quartz→AbstractBatchJob Bridge (mit Parameter-Übergabe)
            CleanupOrphanedHeadersJob.java -- Demo-Job (mit excludePattern + retentionDays)
    benchmark/
        Benchmark.java                     -- Standalone Lese-Benchmark
src/main/resources/
    application.properties                 -- Spring-Config (DB, HikariCP)
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
./gradlew benchmark                    # Standalone Benchmark

# DB-Config via Umgebungsvariablen oder application.properties
TS_JDBC_URL=jdbc:postgresql://localhost:5432/timeseries
TS_DB_USER=postgres
TS_DB_PASSWORD=postgres
```

## Benchmark
- **Code:** `src/main/java/de/market/benchmark/Benchmark.java`
- **Aufruf:** `./gradlew benchmark`
- **Standalone** — erstellt eigenen HikariDataSource, kein Spring-Kontext
- **Testdaten:** 120.000 PERF_TEST-Zeitreihen (PERF_TEST_00001–300000) in der DB, nicht löschen!
- **Ergebnisse:** `benchmarks/` – Dateien nach Schema `YYYY-MM-DD_beschreibung.md`
- Nur Lese-Benchmarks gegen existierende PERF_TEST-Daten (kein Schreiben/Cleanup)

## Dependencies (via Spring Boot BOM)
- Spring Boot 3.4.1 (Web, JDBC, Quartz)
- PostgreSQL JDBC (Version via BOM)
- HikariCP (Version via BOM)
- SLF4J + Logback (via Spring Boot)
- JUnit 5 + Mockito (via spring-boot-starter-test)

## Gradle-Konfiguration
- `gradle.properties`: `org.gradle.java.home` zeigt auf JDK 17 (Foojay-Download unter `~/.gradle/jdks/`)
- Spring Boot Plugin baut Fat-JAR (kein Shadow-Plugin mehr)
