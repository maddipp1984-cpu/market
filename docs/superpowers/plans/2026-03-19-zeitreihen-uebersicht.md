# Zeitreihen-Uebersichtsseite Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neue Uebersichtsseite fuer Zeitreihen nach dem OverviewPage-Pattern mit Mehrfachauswahl zum Oeffnen im Editor.

**Architecture:** Backend liefert alle ts_header-Eintraege mit gejointen Metadaten als TableResponse via Raw JDBC + QueryRegistry. Frontend nutzt das bestehende OverviewPage-Template. Der bestehende Tab-Typ `zeitreihen` wird zur Uebersicht, der Editor bekommt den neuen Typ `timeseries-editor`. Mehrfachauswahl oeffnet selektierte Zeitreihen zusammen im Editor.

**Tech Stack:** Spring Boot (Raw JDBC, QueryRegistry), React (OverviewPage-Template, TabContext)

---

## File Structure

### Backend (neu)
- `src/main/resources/queries/timeseries.xml` — SQL-Query fuer Uebersicht
- `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java` — Raw JDBC
- `src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java` — REST-Endpunkte

### Backend (modifiziert)
- `src/main/java/de/market/timeseries/api/TimeSeriesService.java` — Overview-Methoden ergaenzen

### Frontend (neu)
- `frontend/src/pages/ZeitreihenPage.tsx` — Uebersichtsseite

### Frontend (modifiziert)
- `frontend/src/shell/tabTypes.tsx` — Tab-Typen umbauen
- `frontend/src/timeseries-editor/TimeSeriesEditorPage.tsx` — tsIds aus Tab-Params lesen

---

## Task 1: SQL-Query anlegen

**Files:**
- Create: `src/main/resources/queries/timeseries.xml`

- [ ] **Step 1: Query-XML erstellen**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<queries module="timeseries">
    <query key="timeseries/overview" name="Zeitreihen Uebersicht">
        <![CDATA[
        SELECT h.ts_id,
               h.ts_key,
               CASE h.time_dim
                   WHEN 1 THEN '15 Minuten'
                   WHEN 2 THEN '1 Stunde'
                   WHEN 3 THEN 'Tag'
                   WHEN 4 THEN 'Monat'
                   WHEN 5 THEN 'Jahr'
               END AS dimension,
               u.symbol,
               c.iso_code,
               o.object_key,
               h.description,
               h.created_at,
               h.updated_at
        FROM ts_header h
        JOIN ts_unit u ON u.unit_id = h.unit_id
        LEFT JOIN ts_currency c ON c.currency_id = h.currency_id
        LEFT JOIN ts_object o ON o.object_id = h.object_id
        ORDER BY h.ts_key
        ]]>
    </query>
</queries>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/queries/timeseries.xml
git commit -m "feat: SQL-Query fuer Zeitreihen-Uebersicht"
```

---

## Task 2: Backend — OverviewRepository + Controller

**Files:**
- Create: `src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java`
- Create: `src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java`
- Modify: `src/main/java/de/market/timeseries/api/TimeSeriesService.java`

- [ ] **Step 1: TimeSeriesOverviewRepository erstellen**

Pattern wie `CurrencyOverviewRepository`:

```java
package de.market.timeseries.repository;

import de.market.shared.query.QueryRegistry;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class TimeSeriesOverviewRepository {

    private final DataSource dataSource;
    private final QueryRegistry queryRegistry;

    public TimeSeriesOverviewRepository(DataSource dataSource, QueryRegistry queryRegistry) {
        this.dataSource = dataSource;
        this.queryRegistry = queryRegistry;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        String sql = queryRegistry.get("timeseries/overview");
        return executeQuery(sql, List.of());
    }

    public List<Map<String, Object>> findFiltered(String whereSql, List<Object> params) throws SQLException {
        String baseSql = queryRegistry.get("timeseries/overview");
        String sql = baseSql.replaceFirst("(?i)ORDER BY", "WHERE " + whereSql + " ORDER BY");
        return executeQuery(sql, params);
    }

    private List<Map<String, Object>> executeQuery(String sql, List<Object> params) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("ts_id"));
                    row.put("key", rs.getString("ts_key"));
                    row.put("dimension", rs.getString("dimension"));
                    row.put("unit", rs.getString("symbol"));
                    row.put("currency", rs.getString("iso_code"));
                    row.put("object", rs.getString("object_key"));
                    row.put("description", rs.getString("description"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    row.put("updatedAt", rs.getTimestamp("updated_at"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
```

- [ ] **Step 2: Overview-Methoden in TimeSeriesService ergaenzen**

Am Ende der Klasse `TimeSeriesService.java` hinzufuegen:

```java
private final TimeSeriesOverviewRepository overviewRepository;

// Constructor erweitern um overviewRepository Parameter

@Transactional(readOnly = true)
public List<Map<String, Object>> findAllAsRows() throws SQLException {
    return overviewRepository.findAllAsRows();
}

@Transactional(readOnly = true)
public List<Map<String, Object>> findFiltered(String whereSql, List<Object> params) throws SQLException {
    return overviewRepository.findFiltered(whereSql, params);
}
```

- [ ] **Step 3: TimeSeriesOverviewController erstellen**

```java
package de.market.timeseries.rest;

import de.market.shared.dto.*;
import de.market.timeseries.api.TimeSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timeseries-overview")
public class TimeSeriesOverviewController {

    private static final List<ColumnMeta> COLUMNS = List.of(
        new ColumnMeta("id", "ID", "h.ts_id", "NUMBER"),
        new ColumnMeta("key", "Schluessel", "h.ts_key", "TEXT"),
        new ColumnMeta("dimension", "Dimension", "dimension", "TEXT"),
        new ColumnMeta("unit", "Einheit", "u.symbol", "TEXT"),
        new ColumnMeta("currency", "Waehrung", "c.iso_code", "TEXT"),
        new ColumnMeta("object", "Objekt", "o.object_key", "TEXT"),
        new ColumnMeta("description", "Beschreibung", "h.description", "TEXT"),
        new ColumnMeta("createdAt", "Erstellt", "h.created_at", "TIMESTAMP"),
        new ColumnMeta("updatedAt", "Geaendert", "h.updated_at", "TIMESTAMP")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = COLUMNS.stream()
        .map(ColumnMeta::getSqlColumn)
        .collect(Collectors.toSet());

    private final TimeSeriesService service;

    public TimeSeriesOverviewController(TimeSeriesService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = service.findAllAsRows();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) throws SQLException {
        List<Map<String, Object>> data;
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            FilterQueryBuilder.WhereClause where = FilterQueryBuilder.build(
                request.getConditions(), ALLOWED_SQL_COLUMNS);
            data = service.findFiltered(where.getSql(), where.getParams());
        } else {
            data = service.findAllAsRows();
        }
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }
}
```

- [ ] **Step 4: Backend kompilieren und pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/market/timeseries/repository/TimeSeriesOverviewRepository.java
git add src/main/java/de/market/timeseries/rest/TimeSeriesOverviewController.java
git add src/main/java/de/market/timeseries/api/TimeSeriesService.java
git commit -m "feat: Backend fuer Zeitreihen-Uebersicht (Repository + Controller)"
```

---

## Task 3: Frontend — ZeitreihenPage + Tab-Umbau

**Files:**
- Create: `frontend/src/pages/ZeitreihenPage.tsx`
- Modify: `frontend/src/shell/tabTypes.tsx`
- Modify: `frontend/src/timeseries-editor/TimeSeriesEditorPage.tsx`

- [ ] **Step 1: ZeitreihenPage.tsx erstellen**

```tsx
import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import type { ContextAction } from '../shared/overview-page/VirtualTable';

const columnOverrides = { id: { hidden: true } };

const iconEditor = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

export function ZeitreihenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const openInEditor = useCallback((rows: Record<string, unknown>[]) => {
    const tsIds = rows.map(r => r.id as number);
    openTab('timeseries-editor', { tsIds });
  }, [openTab]);

  const extraActions: ContextAction[] = [
    {
      label: 'Im Editor oeffnen',
      icon: iconEditor,
      onClick: openInEditor,
      multi: true,
    },
  ];

  return (
    <OverviewPage
      pageKey="zeitreihen"
      apiUrl="/api/timeseries-overview"
      tabId={tabId}
      columnOverrides={columnOverrides}
      emptyMessage="Keine Zeitreihen vorhanden"
      onRowDoubleClick={(row) => openTab('timeseries-editor', { tsIds: [row.id as number] })}
      extraContextActions={extraActions}
    />
  );
}
```

- [ ] **Step 2: tabTypes.tsx umbauen**

Aenderungen:
1. Import `ZeitreihenPage` hinzufuegen
2. Tab-Typ `zeitreihen` auf `ZeitreihenPage` aendern (singleton: true)
3. Tab-Typ `timeseries-editor` fuer den Editor hinzufuegen

```tsx
// Import ergaenzen:
import { ZeitreihenPage } from '../pages/ZeitreihenPage';

// tabTypes Array anpassen:
// ALT:  { type: 'zeitreihen', label: 'Zeitreihen', icon: iconTimeSeries, component: TimeSeriesEditorPage },
// NEU:
{ type: 'zeitreihen', label: 'Zeitreihen', icon: iconTimeSeries, singleton: true, component: ZeitreihenPage },
{ type: 'timeseries-editor', label: 'Zeitreihen-Editor', icon: iconTimeSeries, component: TimeSeriesEditorPage },
```

- [ ] **Step 3: TimeSeriesEditorPage.tsx anpassen — tsIds aus Tab-Params lesen**

```tsx
// Import ergaenzen:
import { useTabContext } from '../shell/TabContext';

// In der Komponente:
export function TimeSeriesEditorPage({ tabId }: { tabId: string }) {
  const { updateTabLabel, getTabParams } = useTabContext();
  const params = getTabParams(tabId);
  const initialTsIds = params?.tsIds as number[] | undefined;

  const [tsIds, setTsIds] = useState(initialTsIds ? initialTsIds.join(', ') : '');
  // ... rest bleibt gleich

  // Falls tsIds aus Params kommen, beim Mount direkt laden:
  useEffect(() => {
    if (initialTsIds && initialTsIds.length > 0) {
      setActiveTs(prev => ({ tsIds: initialTsIds, start, end, seq: prev.seq + 1 }));
      updateTabLabel(tabId, 'ZR ' + initialTsIds.join(', '));
    }
  }, []); // nur einmal beim Mount
```

- [ ] **Step 4: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: Keine Fehler

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/ZeitreihenPage.tsx
git add frontend/src/shell/tabTypes.tsx
git add frontend/src/timeseries-editor/TimeSeriesEditorPage.tsx
git commit -m "feat: Zeitreihen-Uebersichtsseite mit Mehrfachauswahl + Editor-Anbindung"
```

---

## Task 4: Manueller Test

- [ ] **Step 1: Backend starten**

Run: `./gradlew bootRun`

- [ ] **Step 2: API pruefen**

Run: `curl http://localhost:8080/api/timeseries-overview`
Expected: JSON mit columns + data Array

- [ ] **Step 3: Frontend pruefen**

- Sidebar-Eintrag "Zeitreihen" oeffnet Uebersichtstabelle
- Spalten: Schluessel, Dimension (lesbarer Text), Einheit, Waehrung, Objekt, Beschreibung, Erstellt, Geaendert
- Doppelklick auf Zeile oeffnet Editor mit dieser Zeitreihe
- Mehrere Zeilen selektieren → Kontextmenu "Im Editor oeffnen" → Editor mit allen selektierten Zeitreihen
- Filter funktioniert (z.B. nach Schluessel oder Dimension filtern)
