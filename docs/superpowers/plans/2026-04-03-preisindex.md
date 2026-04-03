# Preisindex-Modul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eigenständiges Stammdaten-Modul für Preisindices mit automatischer Zeitreihen-Erstellung und eingebettetem Zeitreiheneditor.

**Architecture:** Neues Modul `de.market.index/` nach dem Stammdaten-Pattern (JPA + jOOQ). Ein Index besteht aus drei verknüpften Entitäten: `ts_index` (eigene Tabelle) → `ts_object` (Typ INDEX) → `ts_header` (1:1 Zeitreihe). Frontend: Übersichtsseite mit Kontextmenü + Detailmaske mit eingebettetem `TimeSeriesEditor`.

**Tech Stack:** Spring Boot 3.4 (JPA + jOOQ), React 18, TypeScript, TimescaleDB

---

### Task 1: Datenbank — Tabelle `ts_index` anlegen

**Files:**
- Modify: `sql/schema.sql`

- [ ] **Step 1: Migration-SQL vorbereiten und in schema.sql einfügen**

In `sql/schema.sql` nach dem `ts_header`-Block (nach Zeile ~128, vor den Werte-Tabellen) die neue Tabelle einfügen:

```sql
-- ============================================================
-- Index-Tabelle (Preisindices, Temperaturkurven etc.)
-- ============================================================
CREATE TABLE ts_index (
    index_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_id   BIGINT NOT NULL UNIQUE REFERENCES ts_object(object_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 2: Migration-Datei anlegen**

Erstelle `sql/migrations/XXX_create_ts_index.sql` (nächste freie Nummer) mit dem gleichen CREATE TABLE Statement.

- [ ] **Step 3: Commit**

```bash
git add sql/schema.sql sql/migrations/
git commit -m "feat(index): Tabelle ts_index anlegen"
```

---

### Task 2: jOOQ Codegen ausführen

**Files:**
- Modify: `src/generated/java/de/market/jooq/generated/` (automatisch)

- [ ] **Step 1: Migration auf DB anwenden**

```bash
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/migrations/XXX_create_ts_index.sql
```

- [ ] **Step 2: jOOQ Codegen ausführen**

```bash
./gradlew generateJooq
```

Expected: Neue Klasse `TsIndex` in `src/generated/java/de/market/jooq/generated/tables/`

- [ ] **Step 3: Commit generierte Klassen**

```bash
git add src/generated/
git commit -m "chore: jOOQ Codegen nach ts_index"
```

---

### Task 3: Backend — ObjectType-Enum erweitern

**Files:**
- Modify: `src/main/java/de/market/timeseries/model/ObjectType.java`

- [ ] **Step 1: INDEX zum Enum hinzufügen**

In `ObjectType.java` nach `ANS(4, "Anschluss")` ein Komma setzen und hinzufügen:

```java
ANS(4, "Anschluss"),
INDEX(5, "Index");
```

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/timeseries/model/ObjectType.java
git commit -m "feat(index): ObjectType.INDEX hinzufügen"
```

---

### Task 4: Backend — JPA Entity `IndexEntity`

**Files:**
- Create: `src/main/java/de/market/index/model/IndexEntity.java`

- [ ] **Step 1: Entity anlegen**

```java
package de.market.index.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ts_index")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "index_id")
    private Long id;

    @Column(name = "object_id", nullable = false, unique = true)
    private Long objectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/index/model/IndexEntity.java
git commit -m "feat(index): JPA Entity IndexEntity"
```

---

### Task 5: Backend — JPA Repository

**Files:**
- Create: `src/main/java/de/market/index/repository/IndexJpaRepository.java`

- [ ] **Step 1: Repository anlegen**

```java
package de.market.index.repository;

import de.market.index.model.IndexEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexJpaRepository extends JpaRepository<IndexEntity, Long> {

    boolean existsByObjectId(Long objectId);
}
```

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/index/repository/IndexJpaRepository.java
git commit -m "feat(index): JPA Repository IndexJpaRepository"
```

---

### Task 6: Backend — DTO `IndexDto`

**Files:**
- Create: `src/main/java/de/market/index/rest/dto/IndexDto.java`

- [ ] **Step 1: DTO anlegen**

```java
package de.market.index.rest.dto;

public class IndexDto {
    private Long id;          // index_id
    private String name;      // object_key
    private String description;
    private Integer timeDim;  // 1-5
    private Short unitId;     // nullable
    private Short currencyId; // nullable
    private Long tsId;        // ts_header.ts_id (read-only, wird beim Anlegen gesetzt)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getTimeDim() { return timeDim; }
    public void setTimeDim(Integer timeDim) { this.timeDim = timeDim; }

    public Short getUnitId() { return unitId; }
    public void setUnitId(Short unitId) { this.unitId = unitId; }

    public Short getCurrencyId() { return currencyId; }
    public void setCurrencyId(Short currencyId) { this.currencyId = currencyId; }

    public Long getTsId() { return tsId; }
    public void setTsId(Long tsId) { this.tsId = tsId; }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/de/market/index/rest/dto/IndexDto.java
git commit -m "feat(index): DTO IndexDto"
```

---

### Task 7: Backend — Overview Repository (jOOQ)

**Files:**
- Create: `src/main/java/de/market/index/repository/IndexOverviewRepository.java`

- [ ] **Step 1: Repository anlegen**

```java
package de.market.index.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsIndex.TS_INDEX;
import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static de.market.jooq.generated.tables.TsUnit.TS_UNIT;
import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;

@Repository
public class IndexOverviewRepository extends AbstractOverviewRepository {

    public IndexOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return baseQuery()
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetchMaps();
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return baseQuery()
                .where(condition)
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetchMaps();
    }

    private org.jooq.SelectOnConditionStep<?> baseQuery() {
        return dsl
                .select(
                        TS_INDEX.INDEX_ID.as("id"),
                        TS_OBJECT.OBJECT_KEY.as("name"),
                        TS_OBJECT.DESCRIPTION.as("description"),
                        TS_HEADER.TIME_DIM.as("timeDim"),
                        TS_UNIT.SYMBOL.as("unit"),
                        TS_CURRENCY.ISO_CODE.as("currency"),
                        TS_HEADER.FIRST_DATE.as("firstDate"),
                        TS_HEADER.LAST_DATE.as("lastDate"),
                        TS_INDEX.CREATED_AT.as("createdAt")
                )
                .from(TS_INDEX)
                .join(TS_OBJECT).on(TS_INDEX.OBJECT_ID.eq(TS_OBJECT.OBJECT_ID))
                .join(TS_HEADER).on(TS_HEADER.OBJECT_ID.eq(TS_OBJECT.OBJECT_ID))
                .join(TS_UNIT).on(TS_HEADER.UNIT_ID.eq(TS_UNIT.UNIT_ID))
                .leftJoin(TS_CURRENCY).on(TS_HEADER.CURRENCY_ID.eq(TS_CURRENCY.CURRENCY_ID));
    }
}
```

Hinweis: Die genauen jOOQ-Feldnamen (z.B. `TS_UNIT.SYMBOL`, `TS_CURRENCY.ISO_CODE`) müssen nach dem Codegen geprüft und ggf. angepasst werden. Prüfe die generierten Klassen in `src/generated/java/de/market/jooq/generated/tables/`.

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/index/repository/IndexOverviewRepository.java
git commit -m "feat(index): jOOQ Overview Repository"
```

---

### Task 8: Backend — IndexService

**Files:**
- Create: `src/main/java/de/market/index/service/IndexService.java`

- [ ] **Step 1: Service anlegen**

```java
package de.market.index.service;

import de.market.index.model.IndexEntity;
import de.market.index.repository.IndexJpaRepository;
import de.market.index.repository.IndexOverviewRepository;
import de.market.index.rest.dto.IndexDto;
import de.market.shared.service.AbstractCrudService;
import de.market.timeseries.model.ObjectType;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.Unit;
import de.market.timeseries.model.Currency;
import de.market.timeseries.repository.HeaderRepository;
import de.market.timeseries.repository.ObjectRepository;
import de.market.timeseries.repository.TimeSeriesRepository;
import de.market.timeseries.model.TsObject;

import org.jooq.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class IndexService extends AbstractCrudService<IndexDto, IndexEntity, Long> {

    private final IndexJpaRepository indexRepo;
    private final IndexOverviewRepository overviewRepo;
    private final ObjectRepository objectRepo;
    private final HeaderRepository headerRepo;
    private final TimeSeriesRepository tsRepo;

    public IndexService(IndexJpaRepository indexRepo,
                        IndexOverviewRepository overviewRepo,
                        ObjectRepository objectRepo,
                        HeaderRepository headerRepo,
                        TimeSeriesRepository tsRepo) {
        this.indexRepo = indexRepo;
        this.overviewRepo = overviewRepo;
        this.objectRepo = objectRepo;
        this.headerRepo = headerRepo;
        this.tsRepo = tsRepo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() {
        return overviewRepo.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return overviewRepo.findFiltered(condition);
    }

    @Transactional(readOnly = true)
    public IndexDto findById(Long id) {
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public IndexDto create(IndexDto dto) {
        validate(dto);

        // 1. ts_object erstellen
        TsObject obj = new TsObject(ObjectType.INDEX, dto.getName(), dto.getDescription());
        long objectId = objectRepo.create(obj);

        // 2. ts_index erstellen
        IndexEntity entity = new IndexEntity();
        entity.setObjectId(objectId);
        entity = indexRepo.save(entity);

        // 3. ts_header erstellen
        TimeDimension dim = TimeDimension.fromCode(dto.getTimeDim());
        Unit unit = dto.getUnitId() != null ? Unit.fromCode(dto.getUnitId()) : Unit.NONE;
        Currency currency = dto.getCurrencyId() != null ? Currency.fromCode(dto.getCurrencyId()) : null;

        String tsKey = "IDX_" + dto.getName() + "_" + dim.name();
        TimeSeriesHeader header = new TimeSeriesHeader(tsKey, dim, unit, currency);
        header.setObjectId(objectId);
        header.setDescription(dto.getDescription());
        headerRepo.create(header);

        return toDto(entity);
    }

    public IndexDto update(Long id, IndexDto dto) {
        validate(dto);
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));

        // Object aktualisieren
        TsObject obj = objectRepo.findById(entity.getObjectId())
                .orElseThrow(() -> new IllegalStateException("Objekt nicht gefunden: objectId=" + entity.getObjectId()));

        // Prüfen ob Name sich geändert hat und neuer Name schon vergeben ist
        if (!obj.getObjectKey().equals(dto.getName())) {
            if (objectRepo.findByKey(dto.getName()).isPresent()) {
                throw new IllegalStateException("Name bereits vergeben: " + dto.getName());
            }
        }

        obj.setObjectKey(dto.getName());
        obj.setDescription(dto.getDescription());
        objectRepo.update(obj);

        return toDto(entity);
    }

    public void delete(Long id) {
        IndexEntity entity = indexRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Index nicht gefunden: id=" + id));

        // 1. Zeitreihen-Werte und Header löschen
        List<TimeSeriesHeader> headers = headerRepo.findByObjectId(entity.getObjectId());
        for (TimeSeriesHeader h : headers) {
            tsRepo.delete(h.getTsId(), h.getTimeDimension());
            headerRepo.delete(h.getTsId());
        }

        // 2. Index löschen (ts_object wird per CASCADE mitgelöscht)
        indexRepo.deleteById(id);
    }

    @Override
    protected void validate(IndexDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
        if (dto.getTimeDim() == null) {
            throw new IllegalArgumentException("Zeitdimension ist ein Pflichtfeld");
        }
        try {
            TimeDimension.fromCode(dto.getTimeDim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungueltige Zeitdimension: " + dto.getTimeDim());
        }
        if (dto.getUnitId() == null && dto.getCurrencyId() == null) {
            throw new IllegalArgumentException("Einheit oder Waehrung muss gesetzt sein");
        }
    }

    @Override
    protected IndexDto toDto(IndexEntity entity) {
        IndexDto dto = new IndexDto();
        dto.setId(entity.getId());

        TsObject obj = objectRepo.findById(entity.getObjectId()).orElse(null);
        if (obj != null) {
            dto.setName(obj.getObjectKey());
            dto.setDescription(obj.getDescription());
        }

        List<TimeSeriesHeader> headers = headerRepo.findByObjectId(entity.getObjectId());
        if (!headers.isEmpty()) {
            TimeSeriesHeader h = headers.get(0);
            dto.setTimeDim(h.getTimeDimension().getCode());
            dto.setUnitId((short) h.getUnit().getCode());
            dto.setCurrencyId(h.getCurrency() != null ? (short) h.getCurrency().getCode() : null);
            dto.setTsId(h.getTsId());
        }

        return dto;
    }

    @Override
    protected IndexEntity toEntity(IndexDto dto) {
        // Nicht direkt verwendet — create/update orchestrieren manuell
        throw new UnsupportedOperationException("Use create() or update() instead");
    }
}
```

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/index/service/IndexService.java
git commit -m "feat(index): IndexService mit Create/Update/Delete-Orchestrierung"
```

---

### Task 9: Backend — IndexController (REST)

**Files:**
- Create: `src/main/java/de/market/index/rest/IndexController.java`

- [ ] **Step 1: Controller anlegen**

```java
package de.market.index.rest;

import de.market.index.rest.dto.IndexDto;
import de.market.index.service.IndexService;
import de.market.shared.dto.ColumnMeta;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.JooqFilterBuilder;
import de.market.shared.dto.TableResponse;
import org.jooq.Condition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/indices")
public class IndexController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "index_id", "NUMBER"),
            new ColumnMeta("name", "Name", "object_key", "TEXT"),
            new ColumnMeta("description", "Beschreibung", "description", "TEXT"),
            new ColumnMeta("timeDim", "Zeitdimension", "time_dim", "NUMBER"),
            new ColumnMeta("unit", "Einheit", "symbol", "TEXT"),
            new ColumnMeta("currency", "Waehrung", "iso_code", "TEXT"),
            new ColumnMeta("firstDate", "Erster Wert", "first_date", "DATE"),
            new ColumnMeta("lastDate", "Letzter Wert", "last_date", "DATE"),
            new ColumnMeta("createdAt", "Erstellt", "created_at", "DATE")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final IndexService service;

    public IndexController(IndexService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            Condition condition = JooqFilterBuilder.build(request.getConditions(), ALLOWED_SQL_COLUMNS);
            data = service.findFiltered(condition);
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndexDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<IndexDto> create(@RequestBody IndexDto dto) {
        IndexDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IndexDto> update(@PathVariable Long id, @RequestBody IndexDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Build prüfen**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/index/rest/IndexController.java
git commit -m "feat(index): REST Controller /api/indices"
```

---

### Task 10: Backend — Kompiliertest und manueller API-Test

- [ ] **Step 1: Build + Start**

```bash
./gradlew build
./gradlew bootRun
```

Expected: Anwendung startet ohne Fehler auf Port 8080.

- [ ] **Step 2: API testen (curl)**

```bash
# Erstellen
curl -s -X POST http://localhost:8080/api/indices \
  -H "Content-Type: application/json" \
  -d '{"name":"HPFC_TEST","timeDim":3,"unitId":2,"currencyId":1}' | python -m json.tool

# Übersicht
curl -s http://localhost:8080/api/indices | python -m json.tool

# Einzeln laden (ID aus create-Response)
curl -s http://localhost:8080/api/indices/1 | python -m json.tool

# Löschen
curl -s -X DELETE http://localhost:8080/api/indices/1
```

- [ ] **Step 3: Commit (falls Fixes nötig waren)**

```bash
git add -u
git commit -m "fix(index): API-Test Korrekturen"
```

---

### Task 11: Frontend — TypeScript-Typen und API-Client

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: IndexDto-Typ in types.ts hinzufügen**

Am Ende der Datei (vor dem `declare module` Block) einfügen:

```typescript
// Index
export interface IndexDto {
  id: number | null;
  name: string;
  description: string | null;
  timeDim: number;
  unitId: number | null;
  currencyId: number | null;
  tsId: number | null;
}
```

- [ ] **Step 2: API-Funktionen in client.ts hinzufügen**

Am Ende der Datei (oder nach dem Currencies-Block) einen neuen Abschnitt einfügen:

```typescript
// ==================== Indices ====================

export async function fetchIndex(id: number, signal?: AbortSignal): Promise<IndexDto> {
  const res = await authFetch(`/api/indices/${id}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function saveIndex(dto: IndexDto): Promise<IndexDto> {
  const isNew = dto.id === null;
  const url = isNew ? '/api/indices' : `/api/indices/${dto.id}`;
  const res = await authFetch(url, {
    method: isNew ? 'POST' : 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function deleteIndex(id: number): Promise<void> {
  const res = await authFetch(`/api/indices/${id}`, { method: 'DELETE' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}
```

- [ ] **Step 3: TypeScript Check**

```bash
cd frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

Expected: Keine Fehler

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/client.ts
git commit -m "feat(index): Frontend API-Client und TypeScript-Typen"
```

---

### Task 12: Frontend — IndicesPage (Übersicht)

**Files:**
- Create: `frontend/src/pages/index/IndicesPage.tsx`

- [ ] **Step 1: Übersichtsseite anlegen**

```tsx
import { useState, useCallback } from 'react';
import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import type { ContextAction } from '../../shared/overview-page/VirtualTable';
import { useTabContext } from '../../shell/TabContext';
import { deleteIndex } from '../../api/client';

const columnOverrides = {
  id: { hidden: true },
  timeDim: {
    header: 'Zeitdimension',
    format: (v: unknown) => {
      const labels: Record<number, string> = { 1: '15 Min', 2: '1 Stunde', 3: 'Tag', 4: 'Monat', 5: 'Jahr' };
      return labels[v as number] ?? String(v);
    },
  },
};

export function IndicesPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
  const [dialogRow, setDialogRow] = useState<Record<string, unknown> | null>(null);

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteIndex(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  const extraContextActions: ContextAction[] = [
    {
      label: 'Zeitreihe anzeigen',
      onClick: (rows) => {
        const row = rows[0];
        openTab('index-detail', {
          mode: 'edit',
          entityId: row.id,
          editorMode: 'view',
        });
      },
    },
    {
      label: 'Zeitreihe bearbeiten',
      onClick: (rows) => {
        setDialogRow(rows[0]);
      },
    },
  ];

  return (
    <>
      <OverviewPage
        pageKey="indices"
        apiUrl="/api/indices"
        tabId={tabId}
        onNew={() => openTab('index-detail', { mode: 'new' })}
        newLabel="Neuer Index"
        columnOverrides={columnOverrides}
        emptyMessage="Keine Indices vorhanden"
        onRowDoubleClick={(row) => openTab('index-detail', { mode: 'edit', entityId: row.id })}
        onDelete={handleDelete}
        extraContextActions={extraContextActions}
      />
      {dialogRow && (
        <DateRangeDialog
          onConfirm={(from, to) => {
            openTab('index-detail', {
              mode: 'edit',
              entityId: dialogRow.id,
              editorMode: 'edit',
              dateFrom: from,
              dateTo: to,
            });
            setDialogRow(null);
          }}
          onCancel={() => setDialogRow(null)}
        />
      )}
    </>
  );
}

function DateRangeDialog({ onConfirm, onCancel }: {
  onConfirm: (from: string, to: string) => void;
  onCancel: () => void;
}) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  return (
    <div style={{
      position: 'fixed', inset: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      backgroundColor: 'rgba(0,0,0,0.4)', zIndex: 9999,
    }} onClick={onCancel}>
      <div style={{
        background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-lg)', minWidth: '320px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
      }} onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: '0 0 var(--space-md)', color: 'var(--color-text-primary)' }}>
          Zeitraum waehlen
        </h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <label style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
            Von
            <input type="date" value={from} onChange={e => setFrom(e.target.value)}
              style={{ display: 'block', width: '100%', marginTop: '4px' }} />
          </label>
          <label style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
            Bis
            <input type="date" value={to} onChange={e => setTo(e.target.value)}
              style={{ display: 'block', width: '100%', marginTop: '4px' }} />
          </label>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-sm)', marginTop: 'var(--space-md)' }}>
          <button onClick={onCancel} style={{ padding: '6px 16px' }}>Abbrechen</button>
          <button onClick={() => { if (from && to) onConfirm(from, to); }}
            disabled={!from || !to}
            style={{ padding: '6px 16px', background: 'var(--color-accent)', color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)' }}>
            Oeffnen
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript Check**

```bash
cd frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/index/IndicesPage.tsx
git commit -m "feat(index): Frontend Uebersichtsseite IndicesPage"
```

---

### Task 13: Frontend — IndexDetailPage (Detailmaske)

**Files:**
- Create: `frontend/src/pages/index/IndexDetailPage.tsx`

- [ ] **Step 1: Detailseite anlegen**

```tsx
import { useState, useCallback, useEffect, useMemo } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { TimeSeriesEditor } from '../../timeseries-editor/TimeSeriesEditor';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchIndex, saveIndex, deleteIndex } from '../../api/client';
import type { IndexDto } from '../../api/types';

const DIM_OPTIONS = [
  { value: 1, label: '15 Minuten' },
  { value: 2, label: '1 Stunde' },
  { value: 3, label: 'Tag' },
  { value: 4, label: 'Monat' },
  { value: 5, label: 'Jahr' },
];

export function IndexDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;
  const editorMode = params?.editorMode as 'view' | 'edit' | undefined;
  const dateFrom = params?.dateFrom as string | undefined;
  const dateTo = params?.dateTo as string | undefined;

  const [data, setData] = useState<IndexDto>({
    id: null,
    name: '',
    description: null,
    timeDim: 3,
    unitId: null,
    currencyId: null,
    tsId: null,
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchIndex(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Index: ${result.name}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const updateField = useCallback((field: keyof IndexDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (!data.timeDim) errors.push({ field: 'timeDim', message: 'Zeitdimension' });
    if (data.unitId == null && data.currencyId == null) {
      errors.push({ field: 'unitId', message: 'Einheit oder Waehrung muss gesetzt sein' });
    }
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveIndex(data);
    setData(saved);
    updateTabLabel(tabId, `Index: ${saved.name}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteIndex(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('index-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';
  const isExisting = data.id !== null;

  // Editor-Zeitraum: bei "bearbeiten" aus Dialog, bei "anzeigen" alles
  const editorStart = dateFrom ?? '2020-01-01';
  const editorEnd = dateTo ?? '2030-12-31';
  const tsIds = useMemo(() => data.tsId ? [data.tsId] : [], [data.tsId]);

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="indices"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
      onDelete={handleDelete}
      onNew={handleNew}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.name}
                  onChange={e => updateField('name', e.target.value)}
                  disabled={isDisabled}
                />
              </FormField>
            </div>
            <FormField label="Zeitdimension">
              <select
                value={data.timeDim ?? ''}
                onChange={e => updateField('timeDim', Number(e.target.value))}
                disabled={isDisabled || isExisting}
              >
                <option value="">-- Waehlen --</option>
                {DIM_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </FormField>
          </div>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <FormField label="Einheit (Unit-ID)">
              <input
                type="number"
                value={data.unitId ?? ''}
                onChange={e => updateField('unitId', e.target.value ? Number(e.target.value) : null)}
                disabled={isDisabled || isExisting}
              />
            </FormField>
            <FormField label="Waehrung (Currency-ID)">
              <input
                type="number"
                value={data.currencyId ?? ''}
                onChange={e => updateField('currencyId', e.target.value ? Number(e.target.value) : null)}
                disabled={isDisabled || isExisting}
              />
            </FormField>
          </div>
          <FormField label="Beschreibung">
            <input
              value={data.description ?? ''}
              onChange={e => updateField('description', e.target.value || null)}
              disabled={isDisabled}
            />
          </FormField>
        </div>
      </Card>

      {isExisting && data.tsId && editorMode && (
        <div style={{ marginTop: 'var(--space-md)' }}>
          <TimeSeriesEditor
            tsIds={tsIds}
            start={editorStart}
            end={editorEnd}
          />
        </div>
      )}
    </DetailPage>
  );
}
```

Hinweis: Einheit und Währung werden erstmal als ID-Eingabe implementiert. In einem Follow-up können Dropdowns mit den verfügbaren Werten aus den APIs `/api/einheiten` bzw. `/api/currencies` geladen werden.

- [ ] **Step 2: TypeScript Check**

```bash
cd frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/index/IndexDetailPage.tsx
git commit -m "feat(index): Frontend Detailmaske IndexDetailPage mit eingebettetem Editor"
```

---

### Task 14: Frontend — Tab-Registry und Sidebar

**Files:**
- Modify: `frontend/src/shell/tabTypes.tsx`
- Modify: `frontend/src/shell/sidebarTree.ts`
- Modify: `src/main/resources/sidebar.xml`

- [ ] **Step 1: Import und Tab-Typen in tabTypes.tsx hinzufügen**

Import hinzufügen (nach den bestehenden Imports):

```typescript
import { IndicesPage } from '../pages/index/IndicesPage';
import { IndexDetailPage } from '../pages/index/IndexDetailPage';
```

Im `tabTypes`-Array (z.B. nach dem `warengruppe-detail` Eintrag) einfügen:

```typescript
  { type: 'indices', label: 'Indices', icon: iconObjects, singleton: true, component: IndicesPage },
  { type: 'index-detail', label: 'Index', icon: iconObjects, component: IndexDetailPage },
```

- [ ] **Step 2: sidebar.xml aktualisieren**

In `src/main/resources/sidebar.xml` im `<folder id="stammdaten">` Block einen neuen Eintrag hinzufügen:

```xml
    <item id="indices" tabType="indices" />
```

- [ ] **Step 3: sidebarTree.ts Fallback aktualisieren**

Im `stammdaten`-children Array in `defaultSidebarTree` hinzufügen:

```typescript
      { id: 'indices', label: 'Indices', tabType: 'indices' },
```

- [ ] **Step 4: TypeScript Check und Build**

```bash
cd frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/shell/tabTypes.tsx frontend/src/shell/sidebarTree.ts src/main/resources/sidebar.xml
git commit -m "feat(index): Tab-Registry + Sidebar-Eintrag fuer Indices"
```

---

### Task 15: Integration — End-to-End-Test

- [ ] **Step 1: Backend starten**

```bash
./gradlew bootRun
```

- [ ] **Step 2: Frontend starten**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Manuell testen**

1. Sidebar → Stammdaten → Indices klicken → Übersichtsseite öffnet sich
2. "Neuer Index" klicken → Detailmaske öffnet sich
3. Name, Zeitdimension, Einheit/Währung ausfüllen → Speichern
4. Zurück zur Übersicht → neuer Eintrag sichtbar
5. Rechtsklick → "Zeitreihe anzeigen" → Detailmaske mit Read-Only-Editor
6. Rechtsklick → "Zeitreihe bearbeiten" → Von/Bis-Dialog → Detailmaske mit editierbarem Editor
7. Werte eingeben → Speichern im Editor
8. Index löschen → Bestätigung → Eintrag und Zeitreihe verschwunden

- [ ] **Step 4: Fixes committen**

```bash
git add -u
git commit -m "fix(index): Integration-Fixes nach E2E-Test"
```

---

### Task 16: Dokumentation aktualisieren

**Files:**
- Modify: `CLAUDE.md`
- Modify: `DONE.md`

- [ ] **Step 1: CLAUDE.md aktualisieren**

Im Abschnitt "Modul-Architektur" hinzufügen:

```
- **`index`** — Stammdaten-Modul: Preisindices (JPA + jOOQ, 1:1 Zeitreihe)
```

Im Abschnitt "REST-API" Tabelle hinzufügen:

```
| Indices | `/api/indices` | 1:1 Zeitreihe, Kontextmenü mit Editor |
```

Im Abschnitt "Projektstruktur" hinzufügen:

```
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
```

- [ ] **Step 2: DONE.md Eintrag**

```markdown
## 2026-04-03: Preisindex-Modul
- Neue Tabelle `ts_index` mit FK auf `ts_object`
- Eigenständiges Backend-Modul (JPA + jOOQ) mit CRUD-API `/api/indices`
- Automatische Erstellung von ts_object (Typ INDEX) + ts_header beim Anlegen
- Kaskadierendes Löschen (Werte → Header → Index → Objekt)
- Frontend: Übersichtsseite mit Kontextmenü (Zeitreihe anzeigen/bearbeiten)
- Frontend: Detailmaske mit eingebettetem TimeSeriesEditor
- Von/Bis-Dialog für Zeitreihen-Bearbeitung
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md DONE.md
git commit -m "docs: Preisindex-Modul in CLAUDE.md und DONE.md dokumentieren"
```
