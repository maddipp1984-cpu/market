# Systemfirma-Konzept Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Systemfirma-Konzept mit DB-Spalte, Backend-Cache und visueller Markierung in der GP-Uebersicht.

**Architecture:** Neue Spalte `system_rank` in `business_partner`, `SystemCompanyService` als Startup-Cache mit `@PostConstruct`, OverviewRepository + Frontend erweitert fuer Badge-Anzeige.

**Tech Stack:** Java 17, Spring Boot 3.4, jOOQ, React 18, TypeScript

---

## File Structure

### Backend (neu erstellen)
- `src/main/java/de/market/businesspartner/service/SystemCompanyService.java` — Startup-Cache
- `src/main/java/de/market/businesspartner/model/SystemCompanyEntry.java` — Record
- `sql/migrations/012_system_rank.sql` — DB-Migration

### Bestehende Dateien (modifizieren)
- `src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java` — system_rank Spalte
- `src/main/java/de/market/businesspartner/rest/BusinessPartnerController.java` — ColumnMeta erweitern
- `frontend/src/pages/BusinessPartnerPage.tsx` — columnOverrides fuer Badge

---

### Task 1: DB-Migration

**Files:**
- Create: `sql/migrations/012_system_rank.sql`

- [ ] **Step 1: Migration schreiben**

```sql
-- 012_system_rank.sql
-- Systemfirma-Rang: NULL = normaler Partner, 1 = fuehrend, 2+ = Tochter

ALTER TABLE business_partner ADD COLUMN system_rank SMALLINT UNIQUE;

COMMENT ON COLUMN business_partner.system_rank IS 'Systemfirma-Rang: 1=fuehrend, 2+=Tochter, NULL=normaler Partner';
```

- [ ] **Step 2: Migration ausfuehren**

```bash
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/migrations/012_system_rank.sql
```

Expected: `ALTER TABLE`, `COMMENT` — keine Fehler.

- [ ] **Step 3: jOOQ Codegen ausfuehren**

```bash
./gradlew generateJooq
```

Expected: `BusinessPartner` jOOQ-Klasse bekommt neues Feld `SYSTEM_RANK`.

- [ ] **Step 4: Commit**

```bash
git add sql/migrations/012_system_rank.sql src/generated/
git commit -m "feat: DB-Migration system_rank in business_partner"
```

---

### Task 2: Backend — SystemCompanyEntry + SystemCompanyService

**Files:**
- Create: `src/main/java/de/market/businesspartner/model/SystemCompanyEntry.java`
- Create: `src/main/java/de/market/businesspartner/service/SystemCompanyService.java`

- [ ] **Step 1: SystemCompanyEntry Record erstellen**

```java
package de.market.businesspartner.model;

public record SystemCompanyEntry(Long partnerId, String shortName, String name, int rank) {}
```

- [ ] **Step 2: SystemCompanyService erstellen**

```java
package de.market.businesspartner.service;

import de.market.businesspartner.model.SystemCompanyEntry;
import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static de.market.jooq.generated.tables.BusinessPartner.BUSINESS_PARTNER;

@Service
public class SystemCompanyService {

    private static final Logger log = LoggerFactory.getLogger(SystemCompanyService.class);

    private final DSLContext dsl;
    private volatile List<SystemCompanyEntry> cache = List.of();

    public SystemCompanyService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostConstruct
    void loadOnStartup() {
        reload();
    }

    void reload() {
        cache = dsl
                .select(
                        BUSINESS_PARTNER.ID,
                        BUSINESS_PARTNER.SHORT_NAME,
                        BUSINESS_PARTNER.NAME,
                        BUSINESS_PARTNER.SYSTEM_RANK
                )
                .from(BUSINESS_PARTNER)
                .where(BUSINESS_PARTNER.SYSTEM_RANK.isNotNull())
                .orderBy(BUSINESS_PARTNER.SYSTEM_RANK)
                .fetch(r -> new SystemCompanyEntry(
                        r.get(BUSINESS_PARTNER.ID),
                        r.get(BUSINESS_PARTNER.SHORT_NAME),
                        r.get(BUSINESS_PARTNER.NAME),
                        r.get(BUSINESS_PARTNER.SYSTEM_RANK)
                ));
        log.info("Systemfirmen geladen: {} Eintraege", cache.size());
        if (!cache.isEmpty()) {
            log.info("Fuehrende Systemfirma: {} ({})", cache.get(0).shortName(), cache.get(0).name());
        }
    }

    public Optional<SystemCompanyEntry> getPrimary() {
        return cache.stream().filter(e -> e.rank() == 1).findFirst();
    }

    public List<SystemCompanyEntry> getAll() {
        return cache;
    }

    public boolean isSystemCompany(Long partnerId) {
        return cache.stream().anyMatch(e -> e.partnerId().equals(partnerId));
    }
}
```

WICHTIG: Pruefe die generierten jOOQ-Feldnamen in `src/generated/java/de/market/jooq/generated/tables/BusinessPartner.java`. Das Feld heisst wahrscheinlich `SYSTEM_RANK` (SMALLINT). Falls die ID-Spalte `ID` heisst (nicht `BUSINESS_PARTNER_ID`), passe den Select entsprechend an.

- [ ] **Step 3: Build pruefen**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/market/businesspartner/model/SystemCompanyEntry.java src/main/java/de/market/businesspartner/service/SystemCompanyService.java
git commit -m "feat: SystemCompanyService mit Startup-Cache"
```

---

### Task 3: Backend — OverviewRepository + Controller erweitern

**Files:**
- Modify: `src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java`
- Modify: `src/main/java/de/market/businesspartner/rest/BusinessPartnerController.java`

- [ ] **Step 1: OverviewRepository um system_rank erweitern**

In `BusinessPartnerOverviewRepository.java`, in beiden Methoden `findAllAsRows()` und `findFiltered()` die Select-Liste erweitern. Fuege nach `BUSINESS_PARTNER.NAME.as("name")` hinzu:

```java
                        BUSINESS_PARTNER.SYSTEM_RANK.as("systemRank")
```

Die vollstaendige `findAllAsRows()` sieht dann so aus:

```java
    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        BUSINESS_PARTNER.ID.as("id"),
                        BUSINESS_PARTNER.SHORT_NAME.as("shortName"),
                        BUSINESS_PARTNER.NAME.as("name"),
                        BUSINESS_PARTNER.SYSTEM_RANK.as("systemRank")
                )
                .from(BUSINESS_PARTNER)
                .orderBy(BUSINESS_PARTNER.SHORT_NAME)
                .fetchMaps();
    }
```

Analog fuer `findFiltered()`.

- [ ] **Step 2: Controller ColumnMeta erweitern**

In `BusinessPartnerController.java`, neuen Eintrag in der `COLUMNS`-Liste nach dem name-Eintrag:

```java
    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "bp.id", "NUMBER"),
            new ColumnMeta("shortName", "Kurzbezeichnung", "bp.short_name", "TEXT"),
            new ColumnMeta("name", "Name", "bp.name", "TEXT"),
            new ColumnMeta("systemRank", "Systemfirma", "bp.system_rank", "NUMBER")
    );
```

- [ ] **Step 3: Build pruefen**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/market/businesspartner/repository/BusinessPartnerOverviewRepository.java src/main/java/de/market/businesspartner/rest/BusinessPartnerController.java
git commit -m "feat: system_rank in GP-Uebersicht (Backend)"
```

---

### Task 4: Frontend — Badge in GP-Uebersicht

**Files:**
- Modify: `frontend/src/pages/BusinessPartnerPage.tsx`

- [ ] **Step 1: columnOverrides erweitern**

Ersetze die bestehende `columnOverrides`-Konstante:

```tsx
const columnOverrides = {
  id: { hidden: true },
  systemRank: {
    header: 'Systemfirma',
    format: (value: unknown) => {
      if (value == null) return '';
      const rank = value as number;
      return rank === 1 ? 'Fuehrend' : `Tochter (${rank})`;
    },
  },
};
```

- [ ] **Step 2: TypeScript-Check**

```bash
cd E:/projekte/market/frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

Expected: Keine Fehler.

- [ ] **Step 3: Commit**

```bash
cd E:/projekte/market
git add frontend/src/pages/BusinessPartnerPage.tsx
git commit -m "feat: Systemfirma-Badge in GP-Uebersicht"
```

---

### Task 5: Build + Docs

- [ ] **Step 1: Vollstaendiger Build**

```bash
cd E:/projekte/market && ./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: CLAUDE.md aktualisieren**

In `CLAUDE.md`:
- Im `businesspartner/`-Block in der Projektstruktur ergaenzen:
  - `SystemCompanyEntry.java` unter `model/`
  - `SystemCompanyService.java` unter `service/`

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md um Systemfirma-Konzept erweitert"
```
