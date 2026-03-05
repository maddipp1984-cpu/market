# Zeitreihen-Viewer (Frontend)

## Übersicht
React-SPA zum Anzeigen, Vergleichen und Editieren von Zeitreihen. Kommuniziert mit dem Spring Boot Backend via REST-API.

## Tech-Stack
- **React 18** + TypeScript
- **Vite 4** (Dev-Server + Build)
- **TanStack Table** (`@tanstack/react-table`) — Headless-Table mit Sortierung
- **TanStack Virtual** (`@tanstack/react-virtual`) — Virtualisierung für 100k+ Zeilen
- **Node.js** Pfad: `C:\tools\nodejs\node.exe`

## Build & Dev
```bash
# Dev-Server (Port 5173, Proxy auf Backend 8080)
npm run dev

# Production Build
npm run build

# TypeScript Check
node node_modules/typescript/lib/tsc.js --noEmit
```

## Projektstruktur (Feature-based)
```
frontend/
  src/
    api/                              -- Shared: REST-Client + Typen
      client.ts                       -- fetchHeader, fetchValues, writeDay
      types.ts                        -- Dimension, MultiSeriesRow, etc.
    timeseries-editor/                -- Feature: Zeitreihen-Editor
      TimeSeriesEditor.tsx            -- Hauptkomponente (Filter, Aggregation, Save)
      data/
        useMultiTimeSeries.ts         -- Hook: Laden, Editieren, Speichern (1-N Serien)
        aggregation.ts                -- Dimension-Aggregation, dimensionLabels
        timestampCalculator.ts        -- Timestamp-Berechnung aus Start+Dimension+Index
      table/
        ValuesTable.tsx               -- Virtualisierte Tabelle mit Inline-Editing
    App.tsx                           -- Root: ID-Eingabe, Start/Ende, lädt Editor
    App.css                           -- Globale Styles
    main.tsx                          -- React-Einstiegspunkt
```

### Konvention: Feature-Ordner
Jedes Feature bekommt einen eigenen Ordner mit Unterstruktur (`data/`, `table/`, etc.). Zusammengehörige Dateien (Komponente, Hook, Hilfsfunktionen) liegen beieinander. Shared Code (API, Typen) bleibt auf oberster Ebene.

## Architektur-Entscheidungen

### Multi-Zeitreihen
- N Zeitreihen nebeneinander mit gemeinsamer Datum-Spalte
- Nur gleiche Dimension erlaubt (Validierung beim Laden)
- `MultiSeriesRow.values: number[]` — Index = Position in `headers[]`
- Speichereffizient: primitive Arrays statt Wrapper-Objekte

### Edit-Tracking
- `Map<string, number>` mit Key `"seriesIdx:rowIndex"`
- Edits werden beim Speichern als Snapshot genommen (Race-Condition-sicher)
- Save: geänderte Tage identifizieren → `writeDay()` parallel pro Tag+Serie

### Aggregation
- Dimension-Wechsel (QH→Stunde→Tag→Monat→Jahr) via `aggregateMultiRows`
- Aggregation-Modus pro Serie: `sum` (Mengen) vs `avg` (Preise mit Währung+Einheit)
- Aggregierte Ansicht ist read-only
- Zeitzone immer `Europe/Berlin` (konsistent mit Backend)

### Performance
- Virtualisierung: nur sichtbare Zeilen im DOM (TanStack Virtual, overscan=20)
- `columns` useMemo ist stabil (keine `edits`-Dependency) — verhindert Table-Rebuild bei Edits
- `editsRef` Pattern: Ref für aktuelle Edits, damit Column-Definitionen stabil bleiben
- Zahlenformatierung: `toLocaleString('de-DE')` mit konfigurierbaren Nachkommastellen

### Copy/Paste
- **Ctrl+C**: Kopiert alle Serien als TSV (Header + Datum + Werte)
- **Ctrl+V**: Fügt in die fokussierte Spalte ein (erkennt `data-series-idx`)
- Deutsche Zahlenformate: Punkte als Tausender-Trennzeichen entfernen, Komma→Punkt

## API-Endpunkte (genutzt)
| Funktion | Methode | Pfad |
|----------|---------|------|
| Header lesen | GET | `/api/timeseries/{tsId}` |
| Werte lesen | GET | `/api/timeseries/{tsId}/values?start=...&end=...` |
| Tag schreiben | POST | `/api/timeseries/{tsId}/values` |

## Dependencies
- react, react-dom: ^18.3
- @tanstack/react-table: ^8.20
- @tanstack/react-virtual: ^3.11
- vite: ^4.5, typescript: ~5.6, @vitejs/plugin-react: ^4.3
