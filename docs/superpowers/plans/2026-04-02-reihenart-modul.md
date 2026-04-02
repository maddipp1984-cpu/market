# Reihenart-Modul (Series Type) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neues Stammdaten-Modul "Reihenart" mit Uebersichtsseite, Detailmaske und Sidebar-Eintrag — analog zum Currency-Modul.

**Architecture:** JPA-Entity + jOOQ-Overview-Repository + AbstractCrudService + REST-Controller (Backend). OverviewPage + DetailPage mit Combobox fuer Kategorie (Frontend). DB-Migration fuer neue Tabelle + FK in ts_header.

**Tech Stack:** Java 17, Spring Boot 3.4, jOOQ, JPA/Hibernate, React 18, TypeScript, Vite

---

## File Structure

### Backend (neu erstellen)
- `src/main/java/de/market/seriestype/model/SeriesTypeEntity.java` — JPA Entity
- `src/main/java/de/market/seriestype/model/SeriesCategory.java` — Enum (FINANCIAL, PHYSICAL)
- `src/main/java/de/market/seriestype/repository/SeriesTypeJpaRepository.java` — JpaRepository
- `src/main/java/de/market/seriestype/repository/SeriesTypeOverviewRepository.java` — jOOQ Overview
- `src/main/java/de/market/seriestype/service/SeriesTypeService.java` — AbstractCrudService
- `src/main/java/de/market/seriestype/rest/SeriesTypeController.java` — REST Controller
- `src/main/java/de/market/seriestype/rest/dto/SeriesTypeDto.java` — DTO
- `sql/migrations/011_series_type.sql` — DB-Migration

### Frontend (neu erstellen)
- `frontend/src/pages/ReihenartenPage.tsx` — Uebersichtsseite
- `frontend/src/pages/reihenart/ReihenartDetailPage.tsx` — Detailmaske

### Bestehende Dateien (modifizieren)
- `frontend/src/api/types.ts` — SeriesTypeDto Interface
- `frontend/src/api/client.ts` — fetch/save/delete Funktionen
- `frontend/src/shell/tabTypes.tsx` — Tab-Registrierung
- `frontend/src/shell/sidebarTree.ts` — Fallback-Sidebar
- `src/main/resources/sidebar.xml` — Sidebar-Konfiguration

### Nach jOOQ-Codegen (automatisch generiert)
- `src/generated/java/de/market/jooq/generated/tables/TsSeriesType.java`

---

### Task 1: DB-Migration

**Files:**
- Create: `sql/migrations/011_series_type.sql`

- [ ] **Step 1: Migration schreiben**

```sql
-- 011_series_type.sql
-- Neue Tabelle: Reihenarten (Series Types)

CREATE TABLE ts_series_type (
    series_type_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    category        SMALLINT NOT NULL CHECK (category IN (1, 2))
);

COMMENT ON TABLE ts_series_type IS 'Reihenarten: Klassifikation von Zeitreihen';
COMMENT ON COLUMN ts_series_type.category IS '1=Finanziell, 2=Physikalisch';

-- FK in ts_header (nullable zunaechst, da bestehende Daten keinen Wert haben)
ALTER TABLE ts_header ADD COLUMN series_type_id SMALLINT REFERENCES ts_series_type(series_type_id);
CREATE INDEX idx_header_series_type ON ts_header (series_type_id);
```

Hinweis: Die Spalte wird nullable angelegt, weil bestehende Header-Zeilen noch keinen Wert haben. NOT NULL kann spaeter per Migration gesetzt werden, sobald alle Zeitreihen eine Reihenart zugewiesen bekommen haben.

- [ ] **Step 2: Migration ausfuehren**

```bash
MSYS_NO_PATHCONV=1 docker exec -i timescaledb psql -U postgres -d timeseries < sql/migrations/011_series_type.sql
```

Expected: `CREATE TABLE`, `COMMENT`, `ALTER TABLE`, `CREATE INDEX` — keine Fehler.

- [ ] **Step 3: jOOQ Codegen ausfuehren**

```bash
./gradlew generateJooq
```

Expected: Neue Datei `src/generated/java/de/market/jooq/generated/tables/TsSeriesType.java` wird generiert. `TsHeader` bekommt das neue Feld `SERIES_TYPE_ID`.

- [ ] **Step 4: Commit**

```bash
git add sql/migrations/011_series_type.sql src/generated/
git commit -m "feat: DB-Migration fuer ts_series_type + FK in ts_header"
```

---

### Task 2: Backend — Model + Enum

**Files:**
- Create: `src/main/java/de/market/seriestype/model/SeriesCategory.java`
- Create: `src/main/java/de/market/seriestype/model/SeriesTypeEntity.java`

- [ ] **Step 1: SeriesCategory Enum erstellen**

```java
package de.market.seriestype.model;

public enum SeriesCategory {
    FINANCIAL(1, "Finanziell"),
    PHYSICAL(2, "Physikalisch");

    private final int code;
    private final String label;

    SeriesCategory(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public static SeriesCategory fromCode(int code) {
        for (SeriesCategory c : values()) {
            if (c.code == code) return c;
        }
        throw new IllegalArgumentException("Unbekannte Kategorie: " + code);
    }
}
```

- [ ] **Step 2: SeriesTypeEntity erstellen**

```java
package de.market.seriestype.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ts_series_type")
public class SeriesTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "series_type_id")
    private Short id;

    @Column(name = "code", nullable = false, unique = true, columnDefinition = "TEXT")
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "category", nullable = false)
    private Short category;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Short getCategory() { return category; }
    public void setCategory(Short category) { this.category = category; }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/seriestype/
git commit -m "feat: SeriesCategory Enum + SeriesTypeEntity"
```

---

### Task 3: Backend — DTO + Repository

**Files:**
- Create: `src/main/java/de/market/seriestype/rest/dto/SeriesTypeDto.java`
- Create: `src/main/java/de/market/seriestype/repository/SeriesTypeJpaRepository.java`
- Create: `src/main/java/de/market/seriestype/repository/SeriesTypeOverviewRepository.java`

- [ ] **Step 1: SeriesTypeDto erstellen**

```java
package de.market.seriestype.rest.dto;

public class SeriesTypeDto {
    private Short id;
    private String code;
    private String name;
    private int category;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCategory() { return category; }
    public void setCategory(int category) { this.category = category; }
}
```

- [ ] **Step 2: SeriesTypeJpaRepository erstellen**

```java
package de.market.seriestype.repository;

import de.market.seriestype.model.SeriesTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesTypeJpaRepository extends JpaRepository<SeriesTypeEntity, Short> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Short id);
}
```

- [ ] **Step 3: SeriesTypeOverviewRepository erstellen**

```java
package de.market.seriestype.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsSeriesType.TS_SERIES_TYPE;
import static org.jooq.impl.DSL.*;

@Repository
public class SeriesTypeOverviewRepository extends AbstractOverviewRepository {

    public SeriesTypeOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        TS_SERIES_TYPE.SERIES_TYPE_ID.as("id"),
                        TS_SERIES_TYPE.CODE.as("code"),
                        TS_SERIES_TYPE.NAME.as("name"),
                        categoryLabel().as("category")
                )
                .from(TS_SERIES_TYPE)
                .orderBy(TS_SERIES_TYPE.CODE)
                .fetchMaps();
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl
                .select(
                        TS_SERIES_TYPE.SERIES_TYPE_ID.as("id"),
                        TS_SERIES_TYPE.CODE.as("code"),
                        TS_SERIES_TYPE.NAME.as("name"),
                        categoryLabel().as("category")
                )
                .from(TS_SERIES_TYPE)
                .where(condition)
                .orderBy(TS_SERIES_TYPE.CODE)
                .fetchMaps();
    }

    private static org.jooq.Field<String> categoryLabel() {
        return when(TS_SERIES_TYPE.CATEGORY.eq((short) 1), inline("Finanziell"))
                .when(TS_SERIES_TYPE.CATEGORY.eq((short) 2), inline("Physikalisch"))
                .otherwise(inline(""));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/market/seriestype/
git commit -m "feat: SeriesTypeDto + JpaRepository + OverviewRepository"
```

---

### Task 4: Backend — Service

**Files:**
- Create: `src/main/java/de/market/seriestype/service/SeriesTypeService.java`

- [ ] **Step 1: SeriesTypeService erstellen**

```java
package de.market.seriestype.service;

import de.market.seriestype.model.SeriesCategory;
import de.market.seriestype.model.SeriesTypeEntity;
import de.market.seriestype.repository.SeriesTypeJpaRepository;
import de.market.seriestype.repository.SeriesTypeOverviewRepository;
import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.shared.service.AbstractCrudService;
import org.jooq.Condition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SeriesTypeService extends AbstractCrudService<SeriesTypeDto, SeriesTypeEntity, Short> {

    private final SeriesTypeJpaRepository repository;
    private final SeriesTypeOverviewRepository overviewRepository;

    public SeriesTypeService(SeriesTypeJpaRepository repository, SeriesTypeOverviewRepository overviewRepository) {
        this.repository = repository;
        this.overviewRepository = overviewRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() {
        return overviewRepository.findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return overviewRepository.findFiltered(condition);
    }

    @Transactional(readOnly = true)
    public SeriesTypeDto findById(Short id) {
        SeriesTypeEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reihenart nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public SeriesTypeDto create(SeriesTypeDto dto) {
        validate(dto);
        if (repository.existsByCode(dto.getCode())) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }
        SeriesTypeEntity entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public SeriesTypeDto update(Short id, SeriesTypeDto dto) {
        validate(dto);
        SeriesTypeEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reihenart nicht gefunden: id=" + id));

        if (repository.existsByCodeAndIdNot(dto.getCode(), id)) {
            throw new IllegalStateException("Kuerzel bereits vergeben: " + dto.getCode());
        }

        existing.setCode(dto.getCode());
        existing.setName(dto.getName());
        existing.setCategory((short) dto.getCategory());
        return toDto(repository.save(existing));
    }

    public void delete(Short id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Reihenart nicht gefunden: id=" + id);
        }
        try {
            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Reihenart wird noch von Zeitreihen referenziert und kann nicht geloescht werden");
        }
    }

    @Override
    protected SeriesTypeDto toDto(SeriesTypeEntity entity) {
        SeriesTypeDto dto = new SeriesTypeDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setCategory(entity.getCategory());
        return dto;
    }

    @Override
    protected SeriesTypeEntity toEntity(SeriesTypeDto dto) {
        SeriesTypeEntity entity = new SeriesTypeEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setCategory((short) dto.getCategory());
        return entity;
    }

    @Override
    protected void validate(SeriesTypeDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Kuerzel ist ein Pflichtfeld");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name ist ein Pflichtfeld");
        }
        // Validiert, dass der Code gueltig ist (wirft IllegalArgumentException wenn nicht)
        SeriesCategory.fromCode(dto.getCategory());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/de/market/seriestype/service/
git commit -m "feat: SeriesTypeService mit CRUD + Validierung"
```

---

### Task 5: Backend — REST Controller

**Files:**
- Create: `src/main/java/de/market/seriestype/rest/SeriesTypeController.java`

- [ ] **Step 1: SeriesTypeController erstellen**

```java
package de.market.seriestype.rest;

import de.market.seriestype.rest.dto.SeriesTypeDto;
import de.market.seriestype.service.SeriesTypeService;
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
@RequestMapping("/api/series-types")
public class SeriesTypeController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "series_type_id", "NUMBER"),
            new ColumnMeta("code", "Kuerzel", "code", "TEXT"),
            new ColumnMeta("name", "Name", "name", "TEXT"),
            new ColumnMeta("category", "Kategorie", "category", "TEXT")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final SeriesTypeService service;

    public SeriesTypeController(SeriesTypeService service) {
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
    public ResponseEntity<SeriesTypeDto> getById(@PathVariable Short id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<SeriesTypeDto> create(@RequestBody SeriesTypeDto dto) {
        SeriesTypeDto created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeriesTypeDto> update(@PathVariable Short id, @RequestBody SeriesTypeDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Short id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Backend kompilieren**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/market/seriestype/rest/
git commit -m "feat: SeriesTypeController REST-API /api/series-types"
```

---

### Task 6: Frontend — TypeScript-Typen + API-Client

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: SeriesTypeDto in types.ts hinzufuegen**

Am Ende von `frontend/src/api/types.ts` anfuegen:

```typescript
export interface SeriesTypeDto {
  id: number | null;
  code: string;
  name: string;
  category: number;
}
```

- [ ] **Step 2: API-Funktionen in client.ts hinzufuegen**

Am Ende von `frontend/src/api/client.ts` anfuegen (nach dem letzten Export-Block):

```typescript
// ==================== Series Types ====================

export async function fetchSeriesType(id: number, signal?: AbortSignal): Promise<SeriesTypeDto> {
  const res = await authFetch(`/api/series-types/${id}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function saveSeriesType(dto: SeriesTypeDto): Promise<SeriesTypeDto> {
  const isNew = dto.id === null;
  const url = isNew ? '/api/series-types' : `/api/series-types/${dto.id}`;
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

export async function deleteSeriesType(id: number): Promise<void> {
  const res = await authFetch(`/api/series-types/${id}`, { method: 'DELETE' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}
```

Wichtig: `SeriesTypeDto` muss im Import-Block von `client.ts` importiert werden:

```typescript
import type { ..., SeriesTypeDto } from './types';
```

- [ ] **Step 3: Commit**

```bash
cd frontend && git add src/api/types.ts src/api/client.ts
git commit -m "feat: SeriesTypeDto + API-Client fuer Reihenarten"
```

---

### Task 7: Frontend — Uebersichtsseite

**Files:**
- Create: `frontend/src/pages/ReihenartenPage.tsx`

- [ ] **Step 1: ReihenartenPage erstellen**

```tsx
import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import { deleteSeriesType } from '../api/client';

const columnOverrides = { id: { hidden: true } };

export function ReihenartenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteSeriesType(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  return (
    <OverviewPage
      pageKey="series-types"
      apiUrl="/api/series-types"
      tabId={tabId}
      onNew={() => openTab('reihenart-detail', { mode: 'new' })}
      newLabel="Neue Reihenart"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Reihenarten vorhanden"
      onRowDoubleClick={(row) => openTab('reihenart-detail', { mode: 'edit', entityId: row.id })}
      onDelete={handleDelete}
    />
  );
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/pages/ReihenartenPage.tsx
git commit -m "feat: ReihenartenPage (Uebersicht)"
```

---

### Task 8: Frontend — Detailmaske

**Files:**
- Create: `frontend/src/pages/reihenart/ReihenartDetailPage.tsx`

- [ ] **Step 1: ReihenartDetailPage erstellen**

```tsx
import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchSeriesType, saveSeriesType, deleteSeriesType } from '../../api/client';
import type { SeriesTypeDto } from '../../api/types';

const CATEGORIES = [
  { value: 1, label: 'Finanziell' },
  { value: 2, label: 'Physikalisch' },
];

export function ReihenartDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<SeriesTypeDto>({
    id: null,
    code: '',
    name: '',
    category: 1,
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchSeriesType(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Reihenart: ${result.code}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel, showMessage]);

  const updateField = useCallback((field: keyof SeriesTypeDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.code.trim()) errors.push({ field: 'code', message: 'Kuerzel' });
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveSeriesType(data);
    setData(saved);
    updateTabLabel(tabId, `Reihenart: ${saved.code}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteSeriesType(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('reihenart-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="series-types"
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
            <FormField label="Kuerzel">
              <input
                value={data.code}
                onChange={e => updateField('code', e.target.value)}
                disabled={isDisabled}
              />
            </FormField>
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.name}
                  onChange={e => updateField('name', e.target.value)}
                  disabled={isDisabled}
                />
              </FormField>
            </div>
            <FormField label="Kategorie">
              <select
                value={data.category}
                onChange={e => updateField('category', Number(e.target.value))}
                disabled={isDisabled}
              >
                {CATEGORIES.map(c => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </FormField>
          </div>
        </div>
      </Card>
    </DetailPage>
  );
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && git add src/pages/reihenart/
git commit -m "feat: ReihenartDetailPage mit Kategorie-Combobox"
```

---

### Task 9: Frontend — Tab-Registrierung + Sidebar

**Files:**
- Modify: `frontend/src/shell/tabTypes.tsx`
- Modify: `frontend/src/shell/sidebarTree.ts`
- Modify: `src/main/resources/sidebar.xml`

- [ ] **Step 1: Tab-Typen registrieren**

In `frontend/src/shell/tabTypes.tsx`:

Import hinzufuegen (bei den anderen Page-Imports):

```typescript
import { ReihenartenPage } from '../pages/ReihenartenPage';
import { ReihenartDetailPage } from '../pages/reihenart/ReihenartDetailPage';
```

Neue Eintraege im `tabTypes` Array (nach dem `currency-detail` Eintrag):

```typescript
  { type: 'reihenarten', label: 'Reihenarten', icon: iconObjects, singleton: true, component: ReihenartenPage },
  { type: 'reihenart-detail', label: 'Reihenart', icon: iconObjects, component: ReihenartDetailPage },
```

- [ ] **Step 2: sidebar.xml erweitern**

In `src/main/resources/sidebar.xml`, im `<folder id="stammdaten">` Block, nach `<item id="waehrungen">`:

```xml
    <item id="reihenarten" tabType="reihenarten" />
```

- [ ] **Step 3: Fallback-Sidebar aktualisieren**

In `frontend/src/shell/sidebarTree.ts`, im `stammdaten` children-Array, nach dem waehrungen-Eintrag:

```typescript
      { id: 'reihenarten', label: 'Reihenarten', tabType: 'reihenarten' },
```

- [ ] **Step 4: TypeScript-Check**

```bash
cd frontend && node node_modules/typescript/lib/tsc.js --noEmit
```

Expected: Keine Fehler.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/shell/tabTypes.tsx frontend/src/shell/sidebarTree.ts src/main/resources/sidebar.xml
git commit -m "feat: Reihenarten in Tab-System + Sidebar registriert"
```

---

### Task 10: Backend kompilieren + manueller Smoke-Test

- [ ] **Step 1: Vollstaendiger Build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: CLAUDE.md aktualisieren**

In `CLAUDE.md` die Projektstruktur und REST-API-Tabelle um das neue Modul erweitern:

- Neuer Block `seriestype/` in der Projektstruktur (analog zu `currency/`)
- Neuer Eintrag in der REST-API-Tabelle: `Reihenarten | /api/series-types | Standard-CRUD`

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md um Reihenart-Modul erweitert"
```
