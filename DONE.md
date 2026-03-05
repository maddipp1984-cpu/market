# Changelog

## 2026-03-05 — Frontend: Sidebar Tree-Navigation + Wiederverwendbare Tree-Komponente
- **TreeView** (`shared/TreeView.tsx`): Wiederverwendbare Baum-Komponente mit Headless Tree (`@headless-tree/core` + `@headless-tree/react`), dark/light Variante, expand/collapse, renderNode-Callback
- **Sidebar-Baumnavigation**: Hierarchische Navigation mit klappbaren Sections (Daten, Stammdaten, System) und verschachtelten Eintraegen (z.B. Objekte > Objekttypen/Einheiten/Waehrungen)
- **sidebarTree.ts**: Statische Sidebar-Baumdaten entkoppelt von Tab-Typ-Registry
- **Neue Platzhalter-Seiten**: Objekttypen, Einheiten, Waehrungen (als Tab-Typen in tabTypes.tsx registriert)
- **Design-Tokens**: `--tree-indent` und `--tree-node-height` in tokens.css

## 2026-03-05 — Frontend: Tab-System (Browser-artige Tabs)
- **TabContext**: Zentraler State fuer Tabs (open, close, setActive, updateLabel)
- **TabBar**: Horizontale Tab-Leiste mit Icons, Labels, Close-Buttons
- **tabTypes**: Registry ersetzt routes.tsx — Tab-Typ-Definition mit Komponenten-Referenz
- **Sidebar**: Klick oeffnet neuen Tab (kein Router-NavLink mehr)
- **Inaktive Tabs bleiben gemountet** (display: none) — State bleibt erhalten
- **Dashboard = Singleton** (nur 1 Instanz), alle anderen Tab-Typen mehrfach oeffbar
- **Tab-Labels aktualisierbar**: Zeitreihen-Tab zeigt "ZR 15201" nach Laden
- React Router nicht mehr fuer Navigation genutzt (Dependency bleibt installiert)

## 2026-03-05 — Frontend: App-Layout mit Sidebar-Navigation
- **App-Shell**: Sidebar (dunkel, Slate-900) + Content-Area mit React Router
- **Routing**: Zentrale Routen-Definition in `shell/routes.tsx` — automatisch in Nav + Router
- **Sidebar**: Sections (Daten/Stammdaten/System), aktive Route mit blauem Akzent, Inline-SVG-Icons
- **Neue Shared Components**: FormField (Label+Input), FilterBar (horizontale Filterzeile), Chip/ChipGroup (Info-Badges), DataPage (Standard-Template)
- **TimeSeriesEditorPage**: Route-Seite extrahiert (Formular + Editor), nutzt DataPage
- **TimeSeriesEditor refactored**: Nutzt FilterBar + FormField statt eigener Styles
- **Platzhalter-Seiten**: Dashboard (KPI-Cards), Objekte, Einstellungen
- **PageLayout**: max-width entfernt (App-Shell steuert), optionaler maxWidth-Prop
- **App.css entfernt**: Form-Styles durch FormField-Komponente ersetzt
- **Konsistenz-Mechanismus**: 3 Ebenen (App-Shell strukturell, DataPage als Template, Konventionen in CLAUDE.md)

## 2026-03-05 — Frontend: Design-System
- **Design Tokens**: `styles/tokens.css` mit CSS Custom Properties (Farben, Spacing, Radien, Typografie, Transitions)
- **Basis-Styles**: `styles/base.css` (Reset, Font-Import, Body-Gradient)
- **Shared Components**: `Button` (primary/success/ghost), `Card`, `PageLayout`, `StatusMessage`
- **CSS aufgeteilt**: Feature-spezifische Styles aus `App.css` extrahiert nach `TimeSeriesEditor.css` und `ValuesTable.css`
- **Migration**: App.tsx nutzt `<PageLayout>`, `<Card>`, `<Button>`; TimeSeriesEditor nutzt `<Button>`, `<StatusMessage>`
- App.css von 479 Zeilen auf nur noch Form-Styles reduziert
- Alle Farbwerte durch `var(--color-...)` ersetzt — zentral änderbar

## 2026-03-05 — Spring Boot Migration
- **Phase 0**: Source-Layout auf Maven-Standard (`src/main/java/`, `src/test/java/`), Java 17
- **Phase 1**: Spring Boot 3.4.1 mit `starter-web`, `starter-jdbc` — Shadow-Plugin durch Spring Boot Plugin ersetzt
- **Phase 2**: Repositories als `@Repository` mit `DataSource`, Service als `@Service`, Client als `@Component`
- **Phase 2**: `ConnectionPool.java`, `Main.java`, `EnvUtil.java` gelöscht
- **Phase 3**: REST-Controller (`/api/timeseries`, `/api/objects`) + DTOs + `GlobalExceptionHandler`
- **Phase 4**: Benchmark auf standalone HikariDataSource umgestellt (kein Spring-Kontext)
- `application.properties` für DB-Config mit Spring-Properties
- `gradle.properties`: Gradle läuft auf JDK 17 (Foojay auto-provisioned)

## 2026-03-04 — Übergeordnete Objekte (ts_object)
- `ts_object_type` + `ts_object` Tabellen: Zeitreihen können Objekten zugeordnet werden (1:n)
- `ObjectType` Enum, `TsObject` Model, `ObjectRepository` CRUD
- `TimeSeriesHeader` um `objectId` (nullable) erweitert
- `HeaderRepository`: `object_id` in INSERT/UPDATE/SELECT, neue `findByObjectId()`
- `TimeSeriesService`: Objekt-Operationen (create, get, assign, remove)
- Migration 003, schema.sql aktualisiert
- 4 initiale Objekttypen: CONTRACT_VHP, CONTRACT, CONTRACT_VERANS, ANS

## 2026-03-04 — Write-API im TimeSeriesClient
- `TimeSeriesClient.write(tsId, slice)`: Schreibt Zeitreihen mit automatischer Dimensionskonvertierung
- Subdaily (QH/H): Tageweiser Split mit DST-aware intervalsPerDay
- DAY/MONTH/YEAR: Einzelwerte über `writeSimple`
- `TimeSeriesService.writeSimple()`: Zwei Overloads (LocalDate + int year)
- Aggregation (QH→Tag) und Disaggregation (Tag→QH, Gleichverteilung) beim Schreiben
- 12 Tests: Gleiche Dimension, Disaggregation (inkl. DST Frühling/Herbst), Aggregation, NaN, Fehlerfälle

## 2026-03-04 — Package-Struktur für Monolith-Architektur
- Package-Struktur umgebaut: `de.projekt` als Basis, `common.db` für Infrastruktur, `timeseries.api` als Domänen-Fassade, `timeseries.repository` statt `db`
- `benchmark` auf `de.projekt.benchmark` verschoben
- `build.gradle` mainClass angepasst

## 2026-03-04 — TimeSeriesSlice statt DataPoint
- `TimeSeriesSlice` eingeführt: `LocalDateTime start/end` + flaches `double[]` + lazy `getTimestamp(index)`
- DB liefert nur Werte-Arrays (`SELECT vals ...`), kein Datum in der Rückgabe
- Aufrufer übergibt `LocalDateTime start` und `LocalDateTime end`
- `DataPoint` entfernt — kein Objekt-Overhead mehr pro Messwert
- `readExpanded()` + `readRaw()` entfernt — nur noch `read(tsId, dim, start, end)`
- Read-Stored-Procedures nicht mehr nötig (direktes SQL auf Tabelle)
- `timezone` aus `TimeSeriesHeader` und Repository entfernt — `Europe/Berlin` als Konstante
- Benchmark vereinfacht: eine `benchmarkRead()`-Methode für beliebige Zeitbereiche
- Speicherverbrauch pro Zeitreihe/Jahr: ~274 KB statt ~4,3 MB

## 2026-03-04 — Code-Review Fixes
- **K-1**: NPE-Fix in `readRaw()` — NULL-Werte im Array werden zu `Double.NaN`
- **K-3**: Dimensions-Validierung in `writeDay`, `writeYear`, `readExpanded`, `readRaw`
- **E-2**: Toter Code in `count()` entfernt (identische if/else-Zweige)
- **E-4**: Bug in `delete()` für YEAR-Dimension behoben (`ts_year` statt `ts_date`)
- **E-6**: Benchmark-Pfad in `Main.main()` in try-catch eingeschlossen
- **E-7**: HikariCP Pool-Name gesetzt (`ts-pool`)
- **H-4**: `getEnvOrDefault()` in `EnvUtil` ausgelagert (Deduplizierung)
- Ungenutzte Klassen entfernt: `BulkWriter`, `DstAwareTimeGenerator`
