# Design-Spec: Migration Raw JDBC → jOOQ

**Datum:** 2026-03-20
**Status:** Entwurf
**Ziel:** DB-Unabhängigkeit (PostgreSQL + Oracle) und bessere Wartbarkeit durch Ablösung von Raw JDBC mit jOOQ

---

## 1. Ausgangslage

### Aktueller Persistenz-Ansatz (Dual)
- **JPA/Hibernate** für Stammdaten-CRUD (BusinessPartner, Currency, BatchSchedule)
- **Raw JDBC** über `DataSource` für Übersichten, Zeitreihen, Auth — insgesamt **13 Repositories**
- **QueryRegistry** + **QueryLoader** für 3 Übersichts-Repositories (SQL in XML → DB-Tabelle → ConcurrentHashMap)
- **Stored Procedures** (PL/pgSQL) für performante Zeitreihen-Operationen

### PostgreSQL-spezifische Konstrukte im Code
| Konstrukt | Vorkommen | jOOQ-Äquivalent |
|-----------|-----------|-----------------|
| `RETURNING ts_id` | 5 Repos (Header, Object, AuthGroup, FilterPreset, JobExecutionLog) | `.returning(FIELD).fetchOne()` |
| `ON CONFLICT DO UPDATE/NOTHING` | 4 Repos (AuthUser, AuthGroup, TimeSeries, QueryLoader) | `.onDuplicateKeyUpdate()` / `.onDuplicateKeyIgnore()` |
| `::text`, `::date`, `::jsonb` Casts | FilterQueryBuilder, FilterPreset, TimeSeries | `.cast(SQLDataType.VARCHAR)` / `.cast(SQLDataType.JSON)` |
| `ANY(array)` | TimeSeriesRepository | `.in(list)` — jOOQ übersetzt pro Dialekt |
| `SELECT function(...)` (Procedure-Aufruf) | TimeSeriesRepository | `dsl.select(function(...))` oder `dsl.call(Routine)` |

### Probleme
- SQL-Dialekt hardcoded (siehe Tabelle oben)
- Manuelles ResultSet-Mapping in jedem Repository (fehleranfällig)
- QueryRegistry-Umweg über DB-Tabelle `ts_query` ohne Mehrwert
- Kein Compile-Time-Schutz bei Schema-Änderungen

---

## 2. Zielarchitektur

### Persistenz-Schichten nach Migration

| Schicht | Tool | Repositories |
|---------|------|-------------|
| Stammdaten-CRUD (Detailmasken) | JPA/Hibernate | BusinessPartnerRepository, CurrencyJpaRepository, BatchScheduleJpaRepository |
| Übersichten + Abfragen | jOOQ | Alle bisherigen Raw-JDBC-Repositories (13 Stück) |
| Stored Procedures | Native SQL, pro DB gepflegt | ts_sum_15min, ts_sum_1h, ts_write_*, ts_delete_* |

### Prinzipien
- **JPA** bleibt für Detailmasken — kein Umbau nötig
- **jOOQ** ersetzt Raw JDBC vollständig (Übersichten, Header, Object, Auth, Filter, Scheduling)
- **Stored Procedures** bleiben DB-spezifisch — jOOQ ruft sie auf, übersetzt aber nicht den Body
- **jOOQ Codegen** generiert typsichere Java-Klassen aus dem DB-Schema

---

## 3. Was entfällt

| Komponente | Pfad | Grund |
|------------|------|-------|
| QueryRegistry | `shared/query/QueryRegistry.java` | Ersetzt durch jOOQ DSL |
| QueryLoader | `shared/query/QueryLoader.java` | XML→DB-Sync entfällt |
| QueryController | `shared/query/QueryController.java` | Admin-API für Queries entfällt |
| DB-Tabelle `ts_query` | `sql/schema.sql` | Nicht mehr benötigt |
| XML-Query-Dateien | `resources/queries/*.xml` | Nicht mehr benötigt |
| FilterQueryBuilder | `shared/dto/FilterQueryBuilder.java` | Ersetzt durch jOOQ Conditions |

---

## 4. Was sich ändert (pro Repository-Typ)

### Betroffene Repositories (13 Stück)
- **Übersichten (3):** TimeSeriesOverviewRepository, CurrencyOverviewRepository, BusinessPartnerOverviewRepository
- **Timeseries (3):** HeaderRepository, ObjectRepository, FilterPresetRepository
- **Auth (4):** AuthUserRepository, AuthGroupRepository, AuthPermissionRepository, AuthResourceRepository
- **Scheduling (2):** ScheduleOverviewRepository, JobExecutionLogRepository
- **Komplex (1):** TimeSeriesRepository

### 4.1 Übersichts-Repositories (3 Stück)

**Betroffen:** TimeSeriesOverviewRepository, CurrencyOverviewRepository, BusinessPartnerOverviewRepository

**Vorher:**
```java
String sql = queryRegistry.get("currency/overview");
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    while (rs.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        // manuelles Mapping...
    }
}
```

**Nachher:**
```java
public List<Map<String, Object>> findAllAsRows() {
    return dsl.select(CURRENCY.CURRENCY_ID, CURRENCY.ISO_CODE, CURRENCY.DESCRIPTION)
              .from(CURRENCY)
              .orderBy(CURRENCY.ISO_CODE)
              .fetchMaps();
}
```

**FilterQueryBuilder → jOOQ Conditions (wird zusammen mit Übersichten migriert):**

```java
// Vorher (String-basiert):
FilterQueryBuilder.build(filters, allowedColumns);
// → "WHERE col1 = ? AND col2 LIKE ?" + List<Object> params

// Nachher (typsicher):
public Condition buildCondition(List<FilterCondition> filters) {
    Condition condition = DSL.noCondition();
    for (FilterCondition f : filters) {
        // Kein ::text Cast mehr nötig — jOOQ übernimmt Typ-Konvertierung
        Field<String> col = field(name(f.getSqlColumn()), String.class);
        condition = switch (f.getOperator()) {
            case "=" -> condition.and(col.eq(f.getValue()));
            case "LIKE" -> condition.and(col.containsIgnoreCase(f.getValue()));
            case ">" -> condition.and(col.greaterThan(f.getValue()));
            default -> condition;
        };
    }
    return condition;
}
```

### 4.2 CRUD-Repositories ohne JPA (7 Stück)

**Betroffen:** HeaderRepository, ObjectRepository, AuthUserRepository, AuthGroupRepository, AuthPermissionRepository, AuthResourceRepository, JobExecutionLogRepository

**Vorher:**
```java
PreparedStatement ps = conn.prepareStatement("SELECT * FROM ts_header WHERE ts_id = ?");
ps.setLong(1, tsId);
ResultSet rs = ps.executeQuery();
if (rs.next()) {
    return new TimeSeriesHeader(rs.getLong("ts_id"), rs.getString("ts_key"), ...);
}
```

**Nachher:**
```java
public TimeSeriesHeader findById(long tsId) {
    return dsl.selectFrom(TS_HEADER)
              .where(TS_HEADER.TS_ID.eq(tsId))
              .fetchOneInto(TimeSeriesHeader.class);
}
```

**RETURNING (PostgreSQL-spezifisch → jOOQ portabel):**
```java
// Vorher: INSERT INTO ts_header (...) VALUES (?) RETURNING ts_id
// Nachher:
long tsId = dsl.insertInto(TS_HEADER, TS_HEADER.TS_KEY, TS_HEADER.TIME_DIM, TS_HEADER.UNIT)
               .values(key, dimension, unit)
               .returning(TS_HEADER.TS_ID)
               .fetchOne()
               .getTsId();
// jOOQ übersetzt RETURNING für PostgreSQL und Oracle automatisch
```

**ON CONFLICT / UPSERT (PostgreSQL-spezifisch → jOOQ portabel):**
```java
// Vorher: INSERT INTO ts_auth_user (...) ON CONFLICT (user_id) DO UPDATE SET ...
// Nachher:
dsl.insertInto(TS_AUTH_USER, TS_AUTH_USER.USER_ID, TS_AUTH_USER.USERNAME, TS_AUTH_USER.EMAIL)
   .values(userId, username, email)
   .onDuplicateKeyUpdate()
   .set(TS_AUTH_USER.USERNAME, username)
   .set(TS_AUTH_USER.EMAIL, email)
   .execute();
// jOOQ generiert ON CONFLICT (PostgreSQL) oder MERGE (Oracle) automatisch
```

**Bulk-Operationen (z.B. HeaderRepository.findByIds):**
```java
public List<TimeSeriesHeader> findByIds(List<Long> ids) {
    return dsl.selectFrom(TS_HEADER)
              .where(TS_HEADER.TS_ID.in(ids))
              .fetchInto(TimeSeriesHeader.class);
}
```

### 4.3 FilterPresetRepository (1 Stück — JSONB-Handling)

```java
// Vorher: ps.setString(3, filtersJson); + "?::jsonb" im SQL
// Nachher:
dsl.insertInto(TS_FILTER_PRESET)
   .set(TS_FILTER_PRESET.FILTERS, JSONB.valueOf(filtersJson))
   .execute();
// jOOQ nutzt JSONB-Typ für PostgreSQL, JSON für Oracle
```

### 4.4 ScheduleOverviewRepository + JobExecutionLogRepository (2 Stück)

Gleicher Umbau wie 4.2 — Inline-SQL wird durch jOOQ DSL ersetzt. Joins werden typsicher:

```java
public List<Map<String, Object>> findAllAsRows() {
    return dsl.select(
            BATCH_SCHEDULE.ID,
            BATCH_SCHEDULE.JOB_KEY,
            BATCH_SCHEDULE.SCHEDULE_TYPE,
            // Subquery für letzten Status
            select(BATCH_JOB_EXECUTION_LOG.STATUS)
                .from(BATCH_JOB_EXECUTION_LOG)
                .where(BATCH_JOB_EXECUTION_LOG.SCHEDULE_ID.eq(BATCH_SCHEDULE.ID))
                .orderBy(BATCH_JOB_EXECUTION_LOG.STARTED_AT.desc())
                .limit(1).asField("last_status"))
        .from(BATCH_SCHEDULE)
        .fetchMaps();
}
```

### 4.5 TimeSeriesRepository (1 Stück — komplexester Fall)

**Lesen (dynamischer Tabellenname + fetchSize):**
```java
public List<TimeSeriesSlice> read(long tsId, TimeDimension dim, LocalDate start, LocalDate end) {
    Table<?> table = table(name(dim.getTableName()));
    return dsl.select(field("ts_date"), field("vals"))
              .from(table)
              .where(field("ts_id", Long.class).eq(tsId))
              .and(field("ts_date").between(start, end))
              .orderBy(field("ts_date"))
              .fetchSize(10_000)   // Performance: wie bisher
              .fetch(r -> new TimeSeriesSlice(...));
}
```

**Stored Procedure Aufrufe:**

Die aktuellen Procedures werden in PostgreSQL als Funktionen aufgerufen (`SELECT ts_write_15min_day(...)`), nicht als `CALL`. Oracle nutzt `CALL` oder anonyme PL/SQL-Blöcke. jOOQ abstrahiert beides:

```java
// Option A: jOOQ Routine (wenn Codegen die Procedures erkennt)
dsl.select(TsWrite15minDay.call(tsId, date, valuesArray)).execute();

// Option B: Dialektabhängiger Raw-Aufruf via Interface
// → Siehe Section 6
```

**Array-Parameter (PostgreSQL-spezifisch):**

Der aktuelle Code nutzt `conn.createArrayOf("float8", values)` für Write-Procedures. Oracle hat kein direktes Äquivalent — hier werden Oracle TABLE-Types benötigt. Dieses Mapping muss pro DB in der Procedure-Abstraktionsschicht (Section 6) gelöst werden.

---

## 5. jOOQ Setup

### 5.1 Dependencies (build.gradle)

```groovy
dependencies {
    // Spring Boot Starter — liefert DSLContext-Bean, Transaction-Integration, Exception-Translation
    implementation 'org.springframework.boot:spring-boot-starter-jooq'
    // Codegen
    jooqGenerator 'org.postgresql:postgresql'
}

plugins {
    id 'nu.studer.jooq' version '9.0'
}
```

### 5.2 Spring-Integration

Kein manueller `JooqConfig` nötig — `spring-boot-starter-jooq` liefert alles:

```properties
# application.properties
spring.jooq.sql-dialect=POSTGRES
# Für Oracle: spring.jooq.sql-dialect=ORACLE
```

Spring Boot erstellt automatisch:
- `DSLContext`-Bean (injizierbar in alle Repositories)
- Transaction-Integration (jOOQ nutzt Spring `@Transactional`)
- Exception-Translation (SQL-Exceptions → Spring DataAccessException)

### 5.3 Codegen-Ausgabe

Generierter Code wird in separatem Source-Set abgelegt:

```
src/generated/java/de/market/jooq/generated/
    tables/
        TsHeader.java          -- Table-Referenz
        TsCurrency.java
        TsObject.java
        BatchSchedule.java
        ...
    tables/records/
        TsHeaderRecord.java    -- Row-Typ
        ...
    Keys.java                  -- Primary/Foreign Keys
    Indexes.java               -- Indizes
    Public.java                -- Schema-Referenz
```

```groovy
// build.gradle — generiertes Verzeichnis als Source-Set
sourceSets {
    main {
        java {
            srcDirs 'src/main/java', 'src/generated/java'
        }
    }
}
```

**Entscheidung:** Generierte Klassen werden committet (kein DB-Container für Build nötig). Codegen läuft manuell nach Schema-Änderungen.

### 5.4 Codegen vs. Runtime-Dialekt

Codegen läuft immer gegen PostgreSQL (Entwicklungs-DB). Die generierten Klassen sind **dialekt-neutral** — sie beschreiben nur das Schema (Tabellen, Spalten, Typen). Der Runtime-Dialekt (`spring.jooq.sql-dialect`) bestimmt das generierte SQL.

---

## 6. Stored Procedures — DB-spezifisch

Procedures bleiben pro Datenbank manuell gepflegt:

```
sql/
    procedures/
        postgresql/
            ts_sum_15min.sql
            ts_sum_1h.sql
            ts_write_15min_day.sql
            ts_write_1h_day.sql
            ts_delete_15min.sql
            ts_delete_1h.sql
        oracle/
            ts_sum_15min.sql    -- PL/SQL Äquivalent
            ts_sum_1h.sql
            ...
```

### Aufruf-Abstraktion

Da PostgreSQL Funktionen mit `SELECT func(...)` aufruft und Oracle `CALL proc(...)` nutzt, wird der Aufruf hinter einem Interface abstrahiert:

```java
public interface TimeSeriesProcedures {
    void writeDay(long tsId, LocalDate date, double[] values);
    List<TimeSeriesSlice> readSum(List<Long> tsIds, LocalDate start, LocalDate end);
    void delete(long tsId);
}

@Component
@Profile("postgresql")
public class PostgresTimeSeriesProcedures implements TimeSeriesProcedures {
    // SELECT ts_write_15min_day(?, ?, ?) — PostgreSQL-Syntax
    // conn.createArrayOf("float8", values) — PostgreSQL Array-Typ
}

@Component
@Profile("oracle")
public class OracleTimeSeriesProcedures implements TimeSeriesProcedures {
    // CALL ts_write_15min_day(?, ?, ?) — Oracle-Syntax
    // Oracle TABLE-Type statt Array
}
```

---

## 7. Migrationsreihenfolge

| Phase | Scope | Aufwand | Risiko |
|-------|-------|---------|--------|
| 1 | jOOQ Dependency (spring-boot-starter-jooq) + Codegen + `src/generated/java` einrichten | Klein | Niedrig |
| 2 | Übersichts-Repos (3) + FilterQueryBuilder → jOOQ Conditions; QueryRegistry/Loader/Controller + ts_query entfernen | Mittel | Niedrig |
| 3 | Auth-Repos (4: User, Group, Permission, Resource) | Mittel | Niedrig |
| 4 | Header + Object Repos (2) | Mittel | Mittel |
| 5 | Scheduling-Repos (2: ScheduleOverview, JobExecutionLog) | Mittel | Niedrig |
| 6 | FilterPresetRepository (1, JSONB-Handling) | Mittel | Mittel |
| 7 | TimeSeriesRepository (1, dynamische Tabellen + Procedure-Abstraktion) | Hoch | Mittel |

**Jede Phase ist unabhängig deploybar** — JPA, jOOQ und verbleibendes Raw JDBC können koexistieren.

---

## 8. Teststrategie

| Was | Wie |
|-----|-----|
| Regressionstests | Bestehende Tests laufen vor und nach jeder Phase — Verhalten muss identisch bleiben |
| jOOQ-Queries testen | Gegen echte PostgreSQL-DB (Testcontainers oder lokale Dev-DB) — kein H2 |
| Performance | Benchmark (`./gradlew benchmark`) vor/nach Phase 4 und 7 vergleichen |
| fetchSize-Verifizierung | Manuell prüfen: große Resultsets (>10.000 Zeilen) dürfen nicht in OutOfMemory laufen |

---

## 9. DB-Unabhängigkeit nach Migration

| Bereich | DB-unabhängig? | Wie |
|---------|:-:|---|
| JPA/Hibernate CRUD | Ja | Hibernate-Dialekt |
| jOOQ Queries | Ja | jOOQ-Dialekt (`spring.jooq.sql-dialect`) |
| Stored Procedures | Nein | Pro DB gepflegt + Interface-Abstraktion (Section 6) |
| Schema-Migrationen | Nein | Flyway/Liquibase als Ergänzung empfohlen |
| TimescaleDB-Features | Nein | PostgreSQL-only (Hypertables, Compression) |

**Ergebnis:** ~90% DB-unabhängig. Die verbleibenden 10% (Procedures + TimescaleDB) sind DB-spezifisch und müssen pro Zielplattform gepflegt werden.

---

## 10. Risiken und Offene Punkte

| Risiko | Mitigation |
|--------|-----------|
| jOOQ Codegen braucht laufende DB | Generierte Klassen committen; Codegen nur nach Schema-Änderungen |
| JSONB-Handling (FilterPresetRepository) | jOOQ JSONB-Typ für PostgreSQL; JSON-Typ für Oracle |
| Array-Parameter bei Write-Procedures | Interface-Abstraktion (Section 6): PostgreSQL-Arrays vs. Oracle TABLE-Types |
| Performance-Regression | Benchmarks vor/nach Phase 4 und 7 vergleichen |
| TimescaleDB-spezifische Aufrufe | Als optionales Feature hinter Interface kapseln |
| jOOQ-Lizenz | Open Source (Apache 2.0) für PostgreSQL; **Oracle-Dialekt erfordert kommerzielle Lizenz** |
| fetchSize bei großen Resultsets | `fetchSize()` in jOOQ explizit setzen (wie bisher in Raw JDBC) |
| Benchmark.java nutzt eigenen DataSource | Benchmark bleibt Raw JDBC (unabhängige Performance-Referenz) |
