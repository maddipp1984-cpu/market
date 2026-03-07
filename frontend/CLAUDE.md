# Zeitreihen-Viewer (Frontend)

## Uebersicht
React-SPA zum Anzeigen, Vergleichen und Editieren von Zeitreihen. Kommuniziert mit dem Spring Boot Backend via REST-API.

## Tech-Stack
- **React 18** + TypeScript
- **Tab-System** (eigener TabContext) — IDE-artige Tabs statt URL-Routing
- **Vite 4** (Dev-Server + Build)
- **TanStack Table** (`@tanstack/react-table`) — Headless-Table mit Sortierung
- **TanStack Virtual** (`@tanstack/react-virtual`) — Virtualisierung fuer 100k+ Zeilen
- **Headless Tree** (`@headless-tree/core` + `@headless-tree/react`) — Baumnavigation (Sidebar + wiederverwendbar)
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
    styles/                           -- Design-System
      tokens.css                      -- CSS Custom Properties (Farben, Spacing, Radien, Typo, Sidebar)
      base.css                        -- Reset, Font-Import, Body-Styling
    shell/                            -- App-Shell (Layout + Navigation + Tabs)
      AppShell.tsx + AppShell.css     -- Sidebar + TabBar + Tab-Content-Area + MessageBar
      Sidebar.tsx + Sidebar.css       -- Baumnavigation (Headless Tree, dark-Variante)
      sidebarTree.ts                  -- Statische Sidebar-Baumdaten (SidebarNode[])
      TabContext.tsx                   -- Tab-State (Provider + useTabContext Hook, params, Close-Guard)
      TabBar.tsx + TabBar.css         -- Horizontale Tab-Leiste mit Close-Buttons
      tabTypes.tsx                    -- Tab-Typ-Registry (Typ, Label, Icon, Komponente)
      MessageBarContext.tsx            -- MessageBar-State (Provider + useMessageBar Hook)
      MessageBar.tsx + MessageBar.css -- Globale Statusleiste (success/info/error)
    shared/                           -- Wiederverwendbare UI-Komponenten
      TreeView.tsx + TreeView.css     -- Wiederverwendbare Tree-Komponente (Headless Tree)
      Button.tsx + Button.css         -- Button (primary/success/ghost)
      Card.tsx + Card.css             -- Card-Wrapper (Surface, Border, Shadow)
      Chip.tsx + Chip.css             -- Info-Badges (Chip + ChipGroup)
      DataPage.tsx + DataPage.css     -- Standard-Template fuer Daten-Masken
      detail-page/
        DetailPage.tsx + DetailPage.css  -- Template fuer Detailmasken (Toolbar, Modi, Validierung, Dirty-Guard)
      FilterBar.tsx + FilterBar.css   -- Horizontale Filterzeile mit Actions
      FormField.tsx + FormField.css   -- Label + Input/Select (compact-Variante)
      PageLayout.tsx + PageLayout.css -- Seiten-Layout (optionaler maxWidth, Titel)
      StatusMessage.tsx + .css        -- Error/Info-Meldungen
    api/                              -- Shared: REST-Client + Typen
      client.ts                       -- fetchHeader, fetchValues, writeDay
      types.ts                        -- Dimension, MultiSeriesRow, etc.
    timeseries-editor/                -- Feature: Zeitreihen-Editor
      TimeSeriesEditorPage.tsx        -- Tab-Seite (Formular + Editor, tabId Prop)
      TimeSeriesEditor.tsx            -- Hauptkomponente (Filter, Aggregation, Save)
      TimeSeriesEditor.css            -- Feature-spezifische Styles (Header-Info)
      data/
        useMultiTimeSeries.ts         -- Hook: Laden, Editieren, Speichern (1-N Serien)
        aggregation.ts                -- Dimension-Aggregation, dimensionLabels
        timestampCalculator.ts        -- Timestamp-Berechnung aus Start+Dimension+Index
      table/
        ValuesTable.tsx               -- Virtualisierte Tabelle mit Inline-Editing
        ValuesTable.css               -- Grid-Table-Styles (Tabelle, Zellen, Editing)
    pages/                            -- Weitere Seiten (Platzhalter)
      DashboardPage.tsx               -- KPI-Uebersicht
      ObjectsPage.tsx                 -- Objekt-Verwaltung (Platzhalter)
      ObjekttypenPage.tsx             -- Objekttypen (Platzhalter)
      EinheitenPage.tsx               -- Einheiten (Platzhalter)
      WaehrungenPage.tsx              -- Waehrungen (Platzhalter)
      SettingsPage.tsx                -- Einstellungen (Platzhalter)
    App.tsx                           -- TabProvider + AppShell (kein Router)
    main.tsx                          -- React-Einstiegspunkt (tokens, base)
```

### Konvention: Tab-System
- **Neue Seite = Eintrag in `shell/tabTypes.tsx`** + optional Eintrag in `shell/sidebarTree.ts`
- Tab-Typen in `tabTypes.tsx` (Typ, Label, Icon, Komponente), Sidebar-Baum in `sidebarTree.ts` (Hierarchie)
- Jede Seiten-Komponente erhaelt `tabId: string` als Prop und nutzt `useMessageBar()` fuer Benutzer-Feedback
- Sidebar-Klick oeffnet immer neuen Tab (ausser Singletons wie Dashboard)
- Inaktive Tabs bleiben gemountet (`display: none`) — State bleibt erhalten
- Tab-Labels koennen per `updateTabLabel(tabId, label)` aktualisiert werden
- **Tab-Parameter**: `openTab(type, params)` uebergibt optionale Parameter (z.B. `{ mode: 'edit', entityId: 42 }`), `getTabParams(tabId)` liest sie
- **Close-Guard**: `registerCloseGuard(tabId, guard)` registriert eine Funktion die vor dem Tab-Schliessen geprueft wird (z.B. Dirty-Warnung)

### Konvention: Sidebar-Baumnavigation
- **Sidebar-Struktur** wird per XML konfiguriert (`src/main/resources/sidebar.xml` im Backend)
- Backend liefert die Struktur als JSON via `GET /api/config/sidebar`
- **Fallback**: Bei Backend-Fehler greift `defaultSidebarTree` aus `shell/sidebarTree.ts`
- **Labels** fuer Items kommen aus `tabTypes.tsx` (nicht aus der XML), Labels fuer Ordner aus der XML
- **Icons** werden aus `tabTypes.tsx` referenziert (kein Duplizieren)
- **Validierung**: Unbekannte `tabType`-Werte in der XML werden mit `console.warn` uebersprungen
- **TreeView** (`shared/TreeView.tsx`) — generische, wiederverwendbare Baum-Komponente mit Headless Tree
- TreeView hat `dark`/`light` Variante, `defaultExpanded`, `onSelect`, `renderNode`
- **Neuen Sidebar-Eintrag**: 1) Tab-Typ in `tabTypes.tsx` anlegen, 2) Item in `sidebar.xml` ergaenzen, 3) Optional: `defaultSidebarTree` in `sidebarTree.ts` als Fallback aktualisieren
- **Ordner vs. Blatt**: `children` → Ordner (Klick klappt auf/zu, kein Tab). `tabType` → Blatt (Klick oeffnet Tab). Beides gleichzeitig: `tabType` wird ignoriert. Guard in `Sidebar.tsx` erzwingt dies.

### Konvention: Templates
- **Uebersichtsseiten** (Tabellen mit Toolbar) nutzen `<OverviewPage>` (`shared/overview-page/`) — baut intern auf `<DataPage>` auf, liefert Aktualisieren/Neu-Buttons, Loading/Error-States und Footer automatisch. Jede neue Entitaets-Uebersicht (Objekte, Zeitreihen, Einheiten etc.) MUSS dieses Template verwenden.
- **Detailmasken** (Einzelobjekt anzeigen/bearbeiten/neu) nutzen `<DetailPage>` (`shared/detail-page/`) — Standard-Toolbar (Neu/Speichern/Speichern&Schliessen/Loeschen), Modi (view/edit/new), Validierung vor Speichern (`validate()` Pflicht-Prop), Dirty-Warnung bei Tab-Schliessen, Berechtigungspruefung. Modus wird beim Tab-Oeffnen ueber params festgelegt. Jede Detailseite implementiert `validate()` und steuert `dirty` selbst.
- **Daten-Masken** (Editoren, sonstige Detail-Ansichten) nutzen `<DataPage>` als Template
- **Sonstige Seiten** (Einstellungen, Info) nutzen `<PageLayout>`
- **Formularfelder** nutzen `<FormField>` (mit `compact` fuer Filter-Bars)
- **Filter-Zeilen** nutzen `<FilterBar>` (mit `actions`-Prop fuer Buttons rechts)

### Konvention: Feature-Ordner
Jedes Feature bekommt einen eigenen Ordner mit Unterstruktur (`data/`, `table/`, etc.). Zusammengehoerige Dateien (Komponente, Hook, Hilfsfunktionen, CSS) liegen beieinander. Shared Code (API, Typen, UI-Komponenten) bleibt auf oberster Ebene.

### Konvention: Design-System
- **Design Tokens** in `styles/tokens.css` — alle Farben, Abstaende, Radien, Schriftgroessen als CSS Custom Properties
- **Shared Components** in `shared/` — Button, Card, Chip, DataPage, FilterBar, FormField, PageLayout, StatusMessage
- **Keine hardcodierten Farbwerte** — immer `var(--color-...)`, `var(--space-...)` etc. verwenden
- **Feature-CSS** liegt beim Feature, nicht global — jede Komponente importiert ihr eigenes CSS

### Konsistenz-Mechanismus (3 Ebenen)
1. **Strukturell**: App-Shell umschliesst alles — Sidebar + Content-Area sind immer da
2. **Templates**: DataPage/PageLayout — einfacher zu nutzen als eigenes Layout
3. **Konventionen**: Tab-Typen via tabTypes.tsx, Design-Tokens statt Hardcoding

## Architektur-Entscheidungen

### App-Shell + Tab-System
- `AppShell` = Sidebar + TabBar + Tab-Content-Area
- Flex-Kette: AppShell → app-shell-main → app-shell-content → tab-panel → DataPage (durchgaengig `flex: 1` + `min-height: 0`)
- Sidebar: Dunkler Hintergrund (Slate-900), Baumnavigation mit klappbaren Sections und verschachtelten Eintraegen
- TabBar: Dunkler Hintergrund, aktiver Tab mit Content-BG + blauem Akzent unten
- Alle Tabs gemountet, inaktive mit `display: none`
- Icons als Inline-SVGs (kein Icon-Framework)
- Kein React Router — Navigation rein ueber Tab-State (react-router-dom bleibt als Dependency installiert)

### Multi-Zeitreihen
- N Zeitreihen nebeneinander mit gemeinsamer Datum-Spalte
- Nur gleiche Dimension erlaubt (Validierung beim Laden)
- `MultiSeriesRow.values: number[]` — Index = Position in `headers[]`
- Speichereffizient: primitive Arrays statt Wrapper-Objekte

### Edit-Tracking
- `Map<string, number>` mit Key `"seriesIdx:rowIndex"`
- Edits werden beim Speichern als Snapshot genommen (Race-Condition-sicher)
- Save: geaenderte Tage identifizieren → `writeDay()` parallel pro Tag+Serie

### Aggregation
- Dimension-Wechsel (QH→Stunde→Tag→Monat→Jahr) via `aggregateMultiRows`
- Aggregation-Modus pro Serie: `sum` (Mengen) vs `avg` (Preise mit Waehrung+Einheit)
- Aggregierte Ansicht ist read-only
- Zeitzone immer `Europe/Berlin` (konsistent mit Backend)

### Performance
- Virtualisierung: nur sichtbare Zeilen im DOM (TanStack Virtual, overscan=20)
- `columns` useMemo ist stabil (keine `edits`-Dependency) — verhindert Table-Rebuild bei Edits
- `editsRef` Pattern: Ref fuer aktuelle Edits, damit Column-Definitionen stabil bleiben
- Zahlenformatierung: `toLocaleString('de-DE')` mit konfigurierbaren Nachkommastellen

### Copy/Paste
- **Ctrl+C**: Kopiert alle Serien als TSV (Header + Datum + Werte)
- **Ctrl+V**: Fuegt in die fokussierte Spalte ein (erkennt `data-series-idx`)
- Deutsche Zahlenformate: Punkte als Tausender-Trennzeichen entfernen, Komma→Punkt

## API-Endpunkte (genutzt)
| Funktion | Methode | Pfad |
|----------|---------|------|
| Header lesen | GET | `/api/timeseries/{tsId}` |
| Werte lesen | GET | `/api/timeseries/{tsId}/values?start=...&end=...` |
| Tag schreiben | POST | `/api/timeseries/{tsId}/values` |

## Dependencies
- react, react-dom: ^18.3
- react-router-dom: ^7 (installiert, aber nicht aktiv genutzt)
- @tanstack/react-table: ^8.20
- @tanstack/react-virtual: ^3.11
- @headless-tree/core + @headless-tree/react: Baumnavigation
- vite: ^4.5, typescript: ~5.6, @vitejs/plugin-react: ^4.3
