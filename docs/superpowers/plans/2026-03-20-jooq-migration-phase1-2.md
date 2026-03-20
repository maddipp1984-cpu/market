# jOOQ Migration Phase 1+2: Setup & Übersichts-Repositories

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jOOQ einrichten (Dependency, Codegen, Spring-Integration) und die 3 Übersichts-Repositories + FilterQueryBuilder von Raw JDBC auf jOOQ umstellen. QueryRegistry/QueryLoader/QueryController + ts_query-Tabelle entfernen.

**Architecture:** `spring-boot-starter-jooq` liefert den `DSLContext`-Bean. jOOQ Codegen generiert typsichere Klassen aus dem DB-Schema nach `src/generated/java`. Die Übersichts-Repositories ersetzen `queryRegistry.get()` + manuelles ResultSet-Mapping durch jOOQ DSL + `fetchMaps()`. FilterQueryBuilder wird durch jOOQ `Condition`-Builder ersetzt.

**Tech Stack:** Spring Boot 3.4.1, jOOQ 3.19.x (via Spring Boot BOM), nu.studer.jooq Gradle Plugin 9.0, PostgreSQL

**Spec:** `docs/superpowers/specs/2026-03-20-jooq-migration-design.md`

---

## Task 1: jOOQ Dependencies und Gradle-Config

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: spring-boot-starter-jooq und Codegen-Plugin hinzufügen**

```groovy
// build.gradle — Änderungen:

plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'info.solidsoft.pitest' version '1.15.0'
    id 'nu.studer.jooq' version '9.0'           // NEU
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-quartz'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-jooq'  // NEU

    runtimeOnly 'org.postgresql:postgresql'

    jooqGenerator 'org.postgresql:postgresql'    // NEU

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

// NEU: Generiertes Source-Set
sourceSets {
    main {
        java {
            srcDirs 'src/main/java', 'src/generated/java'
        }
    }
}

// NEU: jOOQ Codegen Konfiguration
jooq {
    configurations {
        main {
            generationTool {
                jdbc {
                    driver = 'org.postgresql.Driver'
                    url = 'jdbc:postgresql://localhost:5432/timeseries'
                    user = 'postgres'
                    password = 'postgres'
                }
                generator {
                    database {
                        inputSchema = 'public'
                        // TimescaleDB-interne Tabellen ausschließen
                        excludes = '_timescaledb.*'
                    }
                    generate {
                        records = true
                        pojos = false
                        daos = false
                    }
                    target {
                        packageName = 'de.market.jooq.generated'
                        directory = 'src/generated/java'
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Kompilierung prüfen (ohne Codegen)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL — jOOQ-Dependency wird aufgelöst, aber noch keine generierten Klassen

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: jOOQ Dependency und Codegen-Plugin einrichten"
```

---

## Task 2: jOOQ Codegen ausführen

**Files:**
- Create: `src/generated/java/de/market/jooq/generated/` (automatisch generiert)

- [ ] **Step 1: DB muss laufen — prüfen**

Run: `docker exec -e PGPASSWORD=postgres timescaledb psql -U postgres -d timeseries -c "SELECT 1"`
Expected: Ausgabe mit `1`

- [ ] **Step 2: Codegen ausführen**

Run: `./gradlew generateJooq`
Expected: BUILD SUCCESSFUL — generierte Klassen unter `src/generated/java/de/market/jooq/generated/`

- [ ] **Step 3: Generierte Klassen prüfen**

Run: `ls src/generated/java/de/market/jooq/generated/tables/`
Expected: Java-Dateien für alle DB-Tabellen (TsHeader, TsCurrency, TsObject, TsQuery, BatchSchedule, etc.)

- [ ] **Step 4: Kompilierung mit generierten Klassen**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Bestehende Tests laufen noch**

Run: `./gradlew test`
Expected: Alle Tests PASS (jOOQ-Addition hat keinen Einfluss auf bestehenden Code)

- [ ] **Step 6: Commit generierte Klassen**

```bash
git add src/generated/java/
git commit -m "build: jOOQ Codegen — generierte Klassen aus DB-Schema"
```

---

## Task 3: Spring-Integration prüfen

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: jOOQ SQL-Dialekt konfigurieren**

In `application.properties` hinzufügen:
```properties
spring.jooq.sql-dialect=POSTGRES
```

- [ ] **Step 2: Anwendung starten und prüfen**

Run: `./gradlew bootRun`
Expected: Startet ohne Fehler. Im Log kein jOOQ-bezogener Fehler. `DSLContext`-Bean wird automatisch von spring-boot-starter-jooq erstellt.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "config: jOOQ SQL-Dialekt auf POSTGRES setzen"
```

---

## Task 4: CurrencyOverviewRepository auf jOOQ umstellen

**Files:**
- Modify: `src/main/java/de/market/currency/repository/CurrencyOverviewRepository.java`
- Modify: `src/main/java/de/market/currency/service/CurrencyService.java`

- [ ] **Step 1: Aktuelle Implementierung lesen**

Read: `src/main/java/de/market/currency/repository/CurrencyOverviewRepository.java`
Read: `src/main/resources/queries/currency.xml` (enthält die SQL-Query die ersetzt wird)

Aktuelle Query (aus XML):
```sql
SELECT currency_id, iso_code, description
FROM ts_currency
ORDER BY iso_code
```

- [ ] **Step 2: CurrencyOverviewRepository auf jOOQ umstellen**

```java
package de.market.currency.repository;

import de.market.shared.dto.ColumnMeta;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;

@Repository
public class CurrencyOverviewRepository {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "number"),
            new ColumnMeta("isoCode", "ISO-Code", "text"),
            new ColumnMeta("description", "Bezeichnung", "text")
    );

    private final DSLContext dsl;

    public CurrencyOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ColumnMeta> getColumns() {
        return COLUMNS;
    }

    public List<Map<String, Object>> findAllAsRows() {
        return dsl.select(
                    TS_CURRENCY.CURRENCY_ID.as("id"),
                    TS_CURRENCY.ISO_CODE.as("isoCode"),
                    TS_CURRENCY.DESCRIPTION.as("description"))
                .from(TS_CURRENCY)
                .orderBy(TS_CURRENCY.ISO_CODE)
                .fetchMaps();
    }

    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl.select(
                    TS_CURRENCY.CURRENCY_ID.as("id"),
                    TS_CURRENCY.ISO_CODE.as("isoCode"),
                    TS_CURRENCY.DESCRIPTION.as("description"))
                .from(TS_CURRENCY)
                .where(condition)
                .orderBy(TS_CURRENCY.ISO_CODE)
                .fetchMaps();
    }
}
```

**Hinweis:** Die Signatur von `findFiltered` ändert sich von `(String whereSql, List<Object> params)` zu `(Condition condition)`. Der CurrencyService und CurrencyController müssen entsprechend angepasst werden — der FilterQueryBuilder-Ersatz kommt in Task 6.

- [ ] **Step 3: CurrencyService anpassen (findFiltered Signatur)**

Die `findFiltered`-Methode im Service muss die neue Signatur durchreichen. Da der FilterQueryBuilder noch nicht ersetzt ist, wird `findFiltered` temporär deaktiviert (auskommentiert) und in Task 6 wieder aktiviert.

- [ ] **Step 4: Kompilierung prüfen**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Anwendung starten und GET /api/currencies testen**

Run: `./gradlew bootRun` (im Hintergrund)
Run: `curl -s http://localhost:8080/api/currencies -H "Authorization: Bearer $TOKEN" | head -c 500`
Expected: JSON-Response mit Währungsliste — gleiche Daten wie vorher

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/market/currency/repository/CurrencyOverviewRepository.java
git add src/main/java/de/market/currency/service/CurrencyService.java
git commit -m "refactor: CurrencyOverviewRepository von Raw JDBC auf jOOQ umstellen"
```

---

## Task 5: BusinessPartnerOverviewRepository auf jOOQ umstellen

**Files:**
- Modify: `src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java`

- [ ] **Step 1: Aktuelle Implementierung und zugehörige Query lesen**

Read: `src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java`
Read: `src/main/resources/queries/businesspartner.xml`

- [ ] **Step 2: BusinessPartnerOverviewRepository auf jOOQ umstellen**

Gleicher Ansatz wie Task 4: `DSLContext` injizieren, `queryRegistry` entfernen, `fetchMaps()` nutzen. Die konkreten Spalten und Joins aus der XML-Query übernehmen, aber als jOOQ DSL.

- [ ] **Step 3: Kompilierung prüfen**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: GET /api/business-partners testen**

Run: `curl -s http://localhost:8080/api/business-partners -H "Authorization: Bearer $TOKEN" | head -c 500`
Expected: JSON-Response mit GP-Liste — gleiche Daten wie vorher

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java
git commit -m "refactor: BusinessPartnerOverviewRepository von Raw JDBC auf jOOQ umstellen"
```

---

## Task 6: TimeSeriesOverviewRepository auf jOOQ umstellen + FilterQueryBuilder ersetzen

**Files:**
- Modify: `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java`
- Modify: `src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java`
- Create: `src/main/java/de/market/shared/dto/JooqFilterBuilder.java` (Ersatz für FilterQueryBuilder)
- Modify: `src/main/java/de/market/currency/rest/CurrencyController.java` (Filter-Signatur anpassen)

- [ ] **Step 1: Aktuelle Implementierung und Query lesen**

Read: `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java`
Read: `src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java`
Read: `src/main/java/de/market/shared/dto/FilterQueryBuilder.java`
Read: `src/main/resources/queries/timeseries.xml`

- [ ] **Step 2: JooqFilterBuilder erstellen**

```java
package de.market.shared.dto;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;

/**
 * Baut jOOQ Conditions aus FilterConditions.
 * Ersetzt den String-basierten FilterQueryBuilder.
 */
public class JooqFilterBuilder {

    private static final int MAX_VALUE_LENGTH = 1000;

    public static Condition build(List<FilterCondition> filters, Set<String> allowedColumns) {
        if (filters == null || filters.isEmpty()) {
            return DSL.noCondition();
        }

        Condition condition = DSL.noCondition();
        for (FilterCondition fc : filters) {
            if (!allowedColumns.contains(fc.getSqlColumn())) {
                throw new IllegalArgumentException("Ungueltige Spalte: " + fc.getSqlColumn());
            }
            String value = fc.getValue();
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Wert zu lang (max " + MAX_VALUE_LENGTH + " Zeichen)");
            }
            condition = condition.and(toCondition(fc));
        }
        return condition;
    }

    private static Condition toCondition(FilterCondition fc) {
        Field<String> col = field(fc.getSqlColumn(), String.class);
        String value = fc.getValue();

        return switch (fc.getOperator().toUpperCase()) {
            case "=" -> col.equalIgnoreCase(value);
            case "!=" -> col.notEqualIgnoreCase(value);
            case "LIKE" -> col.containsIgnoreCase(value);
            case ">" -> col.greaterThan(value);
            case ">=" -> col.greaterOrEqual(value);
            case "<" -> col.lessThan(value);
            case "<=" -> col.lessOrEqual(value);
            case "IS NULL" -> col.isNull();
            case "IS NOT NULL" -> col.isNotNull();
            case "IN" -> {
                String[] parts = value.split(",");
                yield col.in((Object[]) parts);
            }
            case "BETWEEN" -> col.between(value, fc.getValue2());
            default -> throw new IllegalArgumentException("Unbekannter Operator: " + fc.getOperator());
        };
    }
}
```

**Hinweis:** Die exakte Implementierung muss nach dem Lesen des bestehenden FilterQueryBuilder verfeinert werden — insbesondere das Case-Insensitive-Handling und numerische/Datum-Vergleiche. Der Code oben ist die Grundstruktur.

- [ ] **Step 3: TimeSeriesOverviewRepository auf jOOQ umstellen**

Die komplexe Query aus `timeseries.xml` (mit JOINs auf ts_header, ts_unit, ts_currency, ts_object, value_ranges) in jOOQ DSL übersetzen. `queryRegistry` entfernen, `DSLContext` injizieren.

- [ ] **Step 4: TimeSeriesOverviewController anpassen**

`FilterQueryBuilder.build()` durch `JooqFilterBuilder.build()` ersetzen. Die Signatur der Repository-Methode `findFiltered` von `(String sql, List<Object> params)` auf `(Condition condition)` ändern.

- [ ] **Step 5: CurrencyController Filter-Aufruf anpassen**

Den in Task 4 temporär deaktivierten `findFiltered`-Aufruf mit `JooqFilterBuilder` reaktivieren.

- [ ] **Step 6: Kompilierung prüfen**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Alle Übersichten testen**

```bash
# Zeitreihen-Übersicht
curl -s http://localhost:8080/api/timeseries-overview -H "Authorization: Bearer $TOKEN" | head -c 500
# Währungen filtern
curl -s -X POST http://localhost:8080/api/currencies/query -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"filters":[{"sqlColumn":"iso_code","operator":"LIKE","value":"EUR"}]}' | head -c 500
```
Expected: Gleiche Daten wie vorher

- [ ] **Step 8: Commit**

```bash
git add src/main/java/de/market/shared/dto/JooqFilterBuilder.java
git add src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java
git add src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java
git add src/main/java/de/market/currency/rest/CurrencyController.java
git commit -m "refactor: TimeSeriesOverviewRepository auf jOOQ + JooqFilterBuilder als Ersatz fuer FilterQueryBuilder"
```

---

## Task 7: QueryRegistry, QueryLoader, QueryController und ts_query entfernen

**Files:**
- Delete: `src/main/java/de/market/shared/query/QueryRegistry.java`
- Delete: `src/main/java/de/market/shared/query/QueryLoader.java`
- Delete: `src/main/java/de/market/shared/query/QueryController.java`
- Delete: `src/main/resources/queries/currency.xml`
- Delete: `src/main/resources/queries/businesspartner.xml`
- Delete: `src/main/resources/queries/timeseries.xml`
- Delete: `src/main/resources/queries/header.xml` (falls nicht mehr referenziert)
- Delete: `src/main/resources/queries/object.xml` (falls nicht mehr referenziert)
- Delete: `src/main/resources/queries/filterpreset.xml` (falls nicht mehr referenziert)
- Delete: `src/main/java/de/market/shared/dto/FilterQueryBuilder.java`
- Modify: `sql/migrations/` — Migration-Script für DROP TABLE ts_query

**Achtung:** Vor dem Löschen prüfen, ob `QueryRegistry` noch von anderen Stellen referenziert wird (Grep nach `queryRegistry` und `QueryRegistry`). Ebenso prüfen, ob `FilterQueryBuilder` noch referenziert wird (sollte nach Task 6 nicht mehr der Fall sein).

- [ ] **Step 1: Prüfen ob QueryRegistry noch referenziert wird**

Run: Grep nach `QueryRegistry` und `queryRegistry` in `src/main/java/`
Expected: Nur Referenzen in den 3 bereits umgestellten Overview-Repositories (die jetzt DSLContext nutzen) und in QueryLoader/QueryController selbst

- [ ] **Step 2: Prüfen ob FilterQueryBuilder noch referenziert wird**

Run: Grep nach `FilterQueryBuilder` in `src/main/java/`
Expected: Keine Referenzen mehr (alle durch JooqFilterBuilder ersetzt)

- [ ] **Step 3: Prüfen welche XML-Dateien noch von anderen Repos referenziert werden**

Die XML-Dateien `header.xml`, `object.xml`, `filterpreset.xml` könnten von Repositories genutzt werden, die erst in späteren Phasen migriert werden. In dem Fall bleiben sie vorerst — aber da kein QueryRegistry mehr existiert, müssten diese Repositories ebenfalls schon umgestellt sein oder die Queries müssen temporär inline bleiben.

**Kritische Entscheidung:** Wenn `header.xml`, `object.xml` oder `filterpreset.xml` noch benötigt werden, QueryRegistry NICHT löschen sondern nur die 3 Overview-XMLs entfernen. In dem Fall Task 7 anpassen.

- [ ] **Step 4: Dateien löschen (nur wenn sicher keine Referenzen)**

```bash
# Nur löschen was sicher unreferenziert ist
rm src/main/java/de/market/shared/query/QueryRegistry.java
rm src/main/java/de/market/shared/query/QueryLoader.java
rm src/main/java/de/market/shared/query/QueryController.java
rm src/main/java/de/market/shared/dto/FilterQueryBuilder.java
rm src/main/resources/queries/currency.xml
rm src/main/resources/queries/businesspartner.xml
rm src/main/resources/queries/timeseries.xml
# Weitere XMLs nur wenn unreferenziert
```

- [ ] **Step 5: Migration-Script für ts_query erstellen**

Nächste freie Migrationsnummer ermitteln und SQL-Script anlegen:
```sql
-- sql/migrations/NNNN_drop_ts_query.sql
DROP TABLE IF EXISTS ts_query;
```

- [ ] **Step 6: Kompilierung und Tests**

Run: `./gradlew compileJava && ./gradlew test`
Expected: BUILD SUCCESSFUL, alle Tests PASS

- [ ] **Step 7: Anwendung starten und Smoke-Test**

Run: `./gradlew bootRun`
Expected: Startet ohne Fehler. Keine Logs über "QueryRegistry" oder "QueryLoader" mehr.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: QueryRegistry, QueryLoader, FilterQueryBuilder und XML-Queries entfernen

jOOQ ersetzt das bisherige Query-Management komplett.
DB-Tabelle ts_query wird per Migration entfernt."
```

---

## Verifizierung nach Phase 1+2

Nach Abschluss aller Tasks:

- [ ] `./gradlew compileJava` → BUILD SUCCESSFUL
- [ ] `./gradlew test` → Alle Tests PASS
- [ ] `./gradlew bootRun` → Startet ohne Fehler
- [ ] GET /api/currencies → Gleiche Daten wie vorher
- [ ] GET /api/business-partners → Gleiche Daten wie vorher
- [ ] GET /api/timeseries-overview → Gleiche Daten wie vorher
- [ ] POST /api/currencies/query mit Filter → Gleiche Ergebnisse wie vorher
- [ ] POST /api/timeseries-overview/query mit Filter → Gleiche Ergebnisse wie vorher
