# Projekt-Dokumentation (HTML) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine vollständige Projektdokumentation als einzelne HTML-Datei erstellen, die im Browser lesbar ist und per Print-to-PDF exportiert werden kann.

**Architecture:** Einzelne `docs/documentation.html` mit eingebettetem CSS (print-optimiert), semantischem HTML, Table of Contents mit Sprungmarken. Kein Build-Schritt, kein JavaScript nötig — pure HTML+CSS. Print-Stylesheet für saubere PDF-Erzeugung (Seitenumbrüche, keine Navigation im Druck).

**Tech Stack:** HTML5, CSS3 (embedded `<style>`), @media print

---

## Dateistruktur

**Eine Datei:**
- `docs/documentation.html` — Komplette Dokumentation

**Warum eine Datei?**
- Einfach im Browser öffnen (Doppelklick)
- Print-to-PDF ohne Abhängigkeiten
- Keine Build-Chain, kein Server nötig
- Versionierbar in Git

---

## Dokumentations-Gliederung

### Teil 1: Technische Dokumentation (für Entwickler + Stakeholder)
1. **Projektübersicht** — Was ist das System, wofür wird es eingesetzt
2. **Tech-Stack** — Backend (Java 17, Spring Boot, TimescaleDB) + Frontend (React, Vite, TanStack)
3. **Architektur-Übersicht** — Schichten, Datenfluss, Persistenz-Strategie (Raw JDBC vs JPA)
4. **Datenbank-Design** — TimescaleDB, Zeitdimensionen, Hypertables, Partitionierung
5. **Backend-Patterns** — Overview-Pattern (QueryRegistry → TableResponse), Detail-Pattern (JPA CRUD), Service-Schicht
6. **Frontend-Patterns** — OverviewPage-Template, DetailPage-Template, Tab-System, Sidebar
7. **Security-Modell** — Keycloak, RBAC, Gruppen/Permissions, Field-Restrictions
8. **REST-API-Referenz** — Alle Endpoints tabellarisch
9. **Shared Components** — FilterQueryBuilder, QueryRegistry, VirtualTable, FormField
10. **Build & Deployment** — Gradle, Vite, Konfiguration

### Teil 2: Use Cases (für Stakeholder + Entwickler)
1. **Zeitreihen verwalten** — Übersicht, Filtern, Editor öffnen, Werte bearbeiten
2. **Zeitreihen aggregieren** — Mehrfachauswahl → Summieren (Cross-Dimension, Unit-Konvertierung)
3. **Diagramme** — 3 Chart-Bibliotheken, Downsampling, Zoom
4. **Geschäftspartner** — CRUD mit Ansprechpartnern
5. **Währungen** — CRUD
6. **Batchplanung** — Job-Katalog, Schedule anlegen, manuell triggern, Historie
7. **Benutzerverwaltung** — Users, Gruppen, Berechtigungen (Admin)
8. **Sidebar-Navigation** — Baumstruktur, Konfiguration per XML
9. **Filter-System** — FilterBuilder, Presets, Operatoren

---

## Tasks

### Task 1: HTML-Grundgerüst + CSS

**Files:**
- Create: `docs/documentation.html`

- [ ] **Step 1: HTML-Grundstruktur anlegen**

```html
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Zeitreihensystem — Dokumentation</title>
    <style>
        /* === PRINT === */
        @media print {
            nav, .no-print { display: none !important; }
            body { font-size: 11pt; color: #000; }
            h1, h2, h3 { page-break-after: avoid; }
            table, figure, pre { page-break-inside: avoid; }
            a { color: #000; text-decoration: none; }
            .page-break { page-break-before: always; }
        }

        /* === SCREEN === */
        @media screen {
            nav {
                position: fixed; top: 0; left: 0; bottom: 0;
                width: 280px; overflow-y: auto;
                background: #1e293b; color: #e2e8f0;
                padding: 24px 16px; font-size: 14px;
            }
            nav a { color: #93c5fd; text-decoration: none; display: block; padding: 4px 0; }
            nav a:hover { color: #fff; }
            nav ul { list-style: none; padding-left: 16px; margin: 0; }
            nav > ul { padding-left: 0; }
            nav .nav-section { font-weight: 700; margin-top: 16px; color: #f1f5f9; font-size: 13px; text-transform: uppercase; letter-spacing: 0.05em; }
            main { margin-left: 300px; max-width: 900px; padding: 40px 32px; }
        }

        /* === COMMON === */
        * { box-sizing: border-box; }
        body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; line-height: 1.6; color: #1e293b; margin: 0; }
        h1 { font-size: 2em; border-bottom: 3px solid #2563eb; padding-bottom: 8px; margin-top: 0; }
        h2 { font-size: 1.5em; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px; margin-top: 48px; color: #1e40af; }
        h3 { font-size: 1.2em; margin-top: 32px; color: #334155; }
        h4 { font-size: 1em; margin-top: 24px; color: #475569; }
        table { border-collapse: collapse; width: 100%; margin: 16px 0; font-size: 14px; }
        th, td { border: 1px solid #e2e8f0; padding: 8px 12px; text-align: left; }
        th { background: #f1f5f9; font-weight: 600; color: #334155; }
        tr:nth-child(even) { background: #f8fafc; }
        code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; font-family: 'Cascadia Code', 'Fira Code', monospace; }
        pre { background: #1e293b; color: #e2e8f0; padding: 16px; border-radius: 8px; overflow-x: auto; font-size: 13px; line-height: 1.5; }
        pre code { background: none; padding: 0; color: inherit; }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
        .badge-get { background: #dcfce7; color: #166534; }
        .badge-post { background: #dbeafe; color: #1e40af; }
        .badge-put { background: #fef3c7; color: #92400e; }
        .badge-delete { background: #fee2e2; color: #991b1b; }
        .info-box { background: #eff6ff; border-left: 4px solid #2563eb; padding: 12px 16px; margin: 16px 0; border-radius: 0 8px 8px 0; }
        .warn-box { background: #fffbeb; border-left: 4px solid #d97706; padding: 12px 16px; margin: 16px 0; border-radius: 0 8px 8px 0; }
        .diagram { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px; margin: 16px 0; font-family: monospace; white-space: pre; line-height: 1.4; font-size: 13px; }
    </style>
</head>
<body>
    <nav class="no-print">
        <!-- Table of Contents -->
    </nav>
    <main>
        <!-- Content -->
    </main>
</body>
</html>
```

- [ ] **Step 2: Datei speichern und im Browser testen**

Datei `docs/documentation.html` im Browser öffnen, Layout prüfen (Sidebar links, Content rechts). Print-Preview prüfen (Ctrl+P): Sidebar verschwindet, Content füllt Seite.

- [ ] **Step 3: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: HTML-Grundgerüst mit Screen+Print CSS"
```

---

### Task 2: Navigation (Table of Contents)

**Files:**
- Modify: `docs/documentation.html` — `<nav>` Bereich

- [ ] **Step 1: TOC mit allen Abschnitten einfügen**

```html
<nav class="no-print">
    <div style="font-size: 18px; font-weight: 700; margin-bottom: 20px; color: #fff;">
        Zeitreihensystem
    </div>

    <div class="nav-section">Technische Dokumentation</div>
    <ul>
        <li><a href="#uebersicht">Projektübersicht</a></li>
        <li><a href="#tech-stack">Tech-Stack</a></li>
        <li><a href="#architektur">Architektur</a></li>
        <li><a href="#datenbank">Datenbank-Design</a></li>
        <li><a href="#backend-patterns">Backend-Patterns</a></li>
        <li><a href="#frontend-patterns">Frontend-Patterns</a></li>
        <li><a href="#security">Security-Modell</a></li>
        <li><a href="#rest-api">REST-API-Referenz</a></li>
        <li><a href="#shared-components">Shared Components</a></li>
        <li><a href="#build">Build &amp; Deployment</a></li>
    </ul>

    <div class="nav-section">Use Cases</div>
    <ul>
        <li><a href="#uc-zeitreihen">Zeitreihen verwalten</a></li>
        <li><a href="#uc-aggregation">Aggregation</a></li>
        <li><a href="#uc-diagramme">Diagramme</a></li>
        <li><a href="#uc-geschaeftspartner">Geschäftspartner</a></li>
        <li><a href="#uc-waehrungen">Währungen</a></li>
        <li><a href="#uc-batchplanung">Batchplanung</a></li>
        <li><a href="#uc-benutzerverwaltung">Benutzerverwaltung</a></li>
        <li><a href="#uc-sidebar">Sidebar-Navigation</a></li>
        <li><a href="#uc-filter">Filter-System</a></li>
    </ul>
</nav>
```

- [ ] **Step 2: Testen — alle Links springen zu existierenden IDs**

- [ ] **Step 3: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Table of Contents mit Sprungmarken"
```

---

### Task 3: Teil 1 — Projektübersicht + Tech-Stack

**Files:**
- Modify: `docs/documentation.html` — `<main>` Bereich

- [ ] **Step 1: Projektübersicht schreiben**

Inhalt:
- Was ist das Zeitreihensystem (>10 Mio Zeitreihen, TimescaleDB)
- Einsatzzweck (Energiemarkt-Daten: Lastprofile, Preise, Mengen)
- Kernfähigkeiten: Lesen/Schreiben/Aggregieren von Zeitreihen, Stammdatenverwaltung, Batchplanung, RBAC

```html
<section id="uebersicht">
    <h1>Zeitreihensystem — Dokumentation</h1>
    <p>Performantes Zeitreihensystem für die Verwaltung von über 10 Millionen Zeitreihen.
       Gebaut auf TimescaleDB (PostgreSQL-Extension) mit Spring Boot Backend und React Frontend.</p>

    <h3>Kernfähigkeiten</h3>
    <ul>
        <li><strong>Zeitreihen-Management</strong> — Lesen, Schreiben, Löschen von Zeitreihen in 5 Zeitdimensionen (15min bis Jahr)</li>
        <li><strong>On-the-fly Aggregation</strong> — Summierung beliebig vieler Zeitreihen mit Cross-Dimension- und Unit-Konvertierung</li>
        <li><strong>Stammdaten</strong> — Geschäftspartner, Währungen, Objekte, Einheiten</li>
        <li><strong>Batchplanung</strong> — Quartz-basiertes Job-System mit Cron/Intervall-Schedules</li>
        <li><strong>Berechtigungssystem</strong> — RBAC mit Keycloak, Gruppen, Feld-Restriktionen</li>
        <li><strong>Diagramme</strong> — Interaktive Charts mit Zoom, Downsampling, 3 Bibliotheken</li>
    </ul>
</section>
```

- [ ] **Step 2: Tech-Stack Abschnitt schreiben**

```html
<section id="tech-stack" class="page-break">
    <h2>Tech-Stack</h2>

    <h3>Backend</h3>
    <table>
        <tr><th>Technologie</th><th>Version</th><th>Einsatz</th></tr>
        <tr><td>Java</td><td>17 (LTS)</td><td>Programmiersprache</td></tr>
        <tr><td>Spring Boot</td><td>3.4.1</td><td>Application Framework (Web, JDBC, Quartz)</td></tr>
        <tr><td>TimescaleDB</td><td>—</td><td>Zeitreihen-Datenbank (PostgreSQL-Extension)</td></tr>
        <tr><td>Raw JDBC</td><td>—</td><td>Zeitreihen-Zugriff + Übersichtsabfragen (Performance)</td></tr>
        <tr><td>JPA/Hibernate</td><td>—</td><td>Stammdaten-CRUD (Einzeloperationen)</td></tr>
        <tr><td>HikariCP</td><td>—</td><td>Connection Pool (30 Connections)</td></tr>
        <tr><td>Quartz Scheduler</td><td>—</td><td>Batch-Job-Scheduling</td></tr>
        <tr><td>Keycloak</td><td>—</td><td>OAuth2/OIDC Identity Provider</td></tr>
        <tr><td>Gradle</td><td>—</td><td>Build-System</td></tr>
    </table>

    <h3>Frontend</h3>
    <table>
        <tr><th>Technologie</th><th>Version</th><th>Einsatz</th></tr>
        <tr><td>React</td><td>18.3.1</td><td>UI-Framework</td></tr>
        <tr><td>TypeScript</td><td>5.6.2</td><td>Typsicherheit</td></tr>
        <tr><td>Vite</td><td>4.5.5</td><td>Build-Tool + Dev-Server</td></tr>
        <tr><td>TanStack Table</td><td>8.20.6</td><td>Headless Table (Sortierung, Filter)</td></tr>
        <tr><td>TanStack Virtual</td><td>3.11.2</td><td>Virtualisierung (nur sichtbare Zeilen im DOM)</td></tr>
        <tr><td>Headless Tree</td><td>1.6.3</td><td>Sidebar-Baumnavigation</td></tr>
        <tr><td>Keycloak JS</td><td>26.1</td><td>OAuth2 Client</td></tr>
        <tr><td>Recharts / Chart.js / Lightweight Charts</td><td>—</td><td>Diagramme (3 Bibliotheken zum Vergleich)</td></tr>
    </table>

    <div class="info-box">
        <strong>Persistenz-Strategie:</strong> Übersichtsseiten (Tabellen mit vielen Zeilen) nutzen immer Raw JDBC via DataSource.
        JPA wird nur für Einzel-CRUD-Operationen (findById, create, update, delete) eingesetzt.
    </div>
</section>
```

- [ ] **Step 3: Im Browser prüfen — Layout, Tabellen, Info-Box**

- [ ] **Step 4: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Projektübersicht + Tech-Stack"
```

---

### Task 4: Teil 1 — Architektur + Datenbank

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Architektur-Abschnitt schreiben**

Inhalt:
- Schichtenmodell: REST-Controller → Service → Repository
- Package-Struktur (de.market.*) als ASCII-Diagramm
- Dual-Persistenz: Raw JDBC für Übersichten, JPA für Einzel-CRUD
- Frontend: AppShell → TabContext → Pages (OverviewPage / DetailPage Templates)

```html
<section id="architektur" class="page-break">
    <h2>Architektur</h2>

    <h3>Schichtenmodell</h3>
    <div class="diagram">
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + TypeScript)                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Overview  │  │ Detail   │  │ Editor   │  ← Templates │
│  │ Page      │  │ Page     │  │ Page     │              │
│  └─────┬────┘  └────┬─────┘  └────┬─────┘              │
│        └─────────────┼─────────────┘                    │
│                      │ authFetch (Bearer Token)         │
├──────────────────────┼──────────────────────────────────┤
│  Backend (Spring Boot)                                  │
│        ┌─────────────▼──────────────┐                   │
│        │   REST Controller          │ ← @RestController │
│        └─────────────┬──────────────┘                   │
│        ┌─────────────▼──────────────┐                   │
│        │   Service                  │ ← @Service        │
│        └──────┬──────────────┬──────┘                   │
│        ┌──────▼──────┐ ┌────▼───────┐                   │
│        │ Raw JDBC    │ │ JPA Repo   │                   │
│        │ (Übersicht) │ │ (Einzel)   │                   │
│        └──────┬──────┘ └────┬───────┘                   │
├───────────────┼─────────────┼───────────────────────────┤
│  TimescaleDB (PostgreSQL)                               │
│  ┌────────────┐ ┌───────────┐ ┌──────────────┐         │
│  │ ts_values_* │ │ ts_header │ │ Stammdaten   │         │
│  │ (Hypertable)│ │           │ │ (JPA Tables) │         │
│  └────────────┘ └───────────┘ └──────────────┘         │
└─────────────────────────────────────────────────────────┘
    </div>

    <h3>Package-Struktur (Backend)</h3>
    <div class="diagram">
de.market/
├── shared/          Gemeinsame DTOs + Query-System
│   ├── dto/         TableResponse, ColumnMeta, FilterQueryBuilder
│   └── query/       QueryRegistry, QueryLoader, QueryController
├── timeseries/      Zeitreihen-Kernmodul
│   ├── api/         Services (TimeSeriesService, AggregationService)
│   ├── client/      Entwickler-API (DimensionConverter, UnitConverter)
│   ├── model/       POJOs + Enums (TimeDimension, Unit, TimeSeriesSlice)
│   ├── repository/  Raw JDBC Repositories
│   ├── rest/        REST-Controller + DTOs
│   └── security/    Keycloak, RBAC, Admin-API
├── currency/        Währungs-Modul (JPA)
├── businesspartner/ Geschäftspartner-Modul (JPA)
├── scheduling/      Batch-Job-System (Quartz)
└── benchmark/       Standalone Performance-Benchmark
    </div>

    <h3>Frontend-Struktur</h3>
    <div class="diagram">
frontend/src/
├── styles/              Design-Tokens (CSS Custom Properties)
├── shell/               App-Shell (Sidebar, TabBar, TabContext)
├── shared/              Wiederverwendbare UI-Komponenten
│   ├── overview-page/   OverviewPage, VirtualTable, FilterBuilder
│   └── detail-page/     DetailPage (Toolbar, Dirty-Guard, Validierung)
├── auth/                Keycloak-Integration, AuthContext
├── api/                 REST-Client (client.ts, types.ts)
├── admin/               Admin-Seiten (Users, Groups, Permissions)
├── timeseries-editor/   Zeitreihen-Editor + Charts
│   ├── data/            useMultiTimeSeries, aggregation, timestamps
│   ├── chart/           Recharts, Chart.js, Lightweight Charts
│   └── table/           ValuesTable (inline-editable)
└── pages/               Feature-Seiten (je Domain-Entität)
    </div>
</section>
```

- [ ] **Step 2: Datenbank-Design Abschnitt schreiben**

Inhalt:
- Zeitdimensionen-Tabelle (15min/1h/Tag/Monat/Jahr mit Tabellenname + Zeittyp)
- Header-Tabelle (ts_header) — Metadaten
- Hypertables + Hash-Partitionierung
- DST-Handling (Europe/Berlin, 92/96/100 QH)
- Stored Procedures für Aggregation

```html
<section id="datenbank" class="page-break">
    <h2>Datenbank-Design</h2>

    <h3>Zeitdimensionen</h3>
    <p>Jede Zeitdimension hat eine eigene Werte-Tabelle mit optimierter Chunk-Größe und Kompression.</p>
    <table>
        <tr><th>Code</th><th>Dimension</th><th>Tabelle</th><th>Zeittyp</th><th>Hypertable</th></tr>
        <tr><td>1</td><td>15 Minuten</td><td><code>ts_values_15min</code></td><td>TIMESTAMPTZ</td><td>Ja</td></tr>
        <tr><td>2</td><td>1 Stunde</td><td><code>ts_values_1h</code></td><td>TIMESTAMPTZ</td><td>Ja</td></tr>
        <tr><td>3</td><td>Tag</td><td><code>ts_values_day</code></td><td>DATE</td><td>Ja</td></tr>
        <tr><td>4</td><td>Monat</td><td><code>ts_values_month</code></td><td>DATE</td><td>Ja</td></tr>
        <tr><td>5</td><td>Jahr</td><td><code>ts_values_year</code></td><td>SMALLINT</td><td>Nein</td></tr>
    </table>

    <h3>Tabellendesign</h3>
    <ul>
        <li><strong><code>ts_header</code></strong> — Metadaten jeder Zeitreihe (Key, Dimension, Einheit, Währung, Objekt-Zuordnung)</li>
        <li><strong><code>ts_values_*</code></strong> — Separate Werte-Tabellen pro Dimension</li>
        <li><strong><code>ts_object</code> / <code>ts_object_type</code></strong> — Übergeordnete Objekte mit 1:n Beziehung zu Zeitreihen</li>
        <li><strong>Stammdaten-Tabellen</strong> — <code>ts_currency</code>, <code>business_partner</code>, <code>contact_person</code>, Auth-Tabellen</li>
    </ul>

    <h3>TimescaleDB-Features</h3>
    <ul>
        <li><strong>Hypertables</strong> — Automatische Partitionierung nach Zeit für 15min, 1h, Tag, Monat</li>
        <li><strong>Hash-Partitionierung</strong> — Auf <code>ts_id</code> für schnellen Einzelreihen-Zugriff</li>
        <li><strong>Kompression</strong> — <code>segmentby = ts_id</code>, automatisch nach 3–6 Monaten</li>
        <li><strong>Stored Procedures</strong> — <code>ts_sum_15min</code>, <code>ts_sum_1h</code> für DB-seitige Aggregation</li>
    </ul>

    <h3>DST-Handling (Sommerzeit/Winterzeit)</h3>
    <div class="info-box">
        <strong>Zeitzone:</strong> Immer <code>Europe/Berlin</code> (Konstante in <code>TimeSeriesSlice.ZONE</code>).<br>
        <code>TIMESTAMPTZ</code> speichert intern UTC — jeder Zeitpunkt ist eindeutig.
    </div>
    <table>
        <tr><th>Tag</th><th>Viertelstunden</th><th>Stunden</th></tr>
        <tr><td>Normaltag</td><td>96</td><td>24</td></tr>
        <tr><td>Sommerzeit (März)</td><td>92</td><td>23</td></tr>
        <tr><td>Winterzeit (Oktober)</td><td>100</td><td>25</td></tr>
    </table>
</section>
```

- [ ] **Step 3: Im Browser prüfen — Diagramme lesbar, Tabellen korrekt**

- [ ] **Step 4: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Architektur + Datenbank-Design"
```

---

### Task 5: Teil 1 — Backend-Patterns + Frontend-Patterns

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Backend-Patterns Abschnitt schreiben**

Inhalt:
- **Overview-Pattern**: Controller → OverviewRepository (Raw JDBC) → QueryRegistry → TableResponse
  - QueryRegistry lädt SQL aus DB (@PostConstruct)
  - QueryLoader synchronisiert XML → DB (ApplicationReadyEvent)
  - FilterQueryBuilder baut sichere WHERE-Klauseln
  - Unterstützte Operatoren: =, !=, <, >, LIKE, IN, BETWEEN, IS NULL
- **Detail-Pattern**: Controller → Service → JpaRepository
  - DTO-Mapping im Service
  - Validierung (required fields, uniqueness)
  - @Transactional
  - @OneToMany Cascade (z.B. BusinessPartner → ContactPerson)
- **Exception Handling**: IllegalArgumentException→400, IllegalStateException→409, SQLException→500

- [ ] **Step 2: Frontend-Patterns Abschnitt schreiben**

Inhalt:
- **OverviewPage-Template**: Props (pageKey, apiUrl, onNew, onDelete, columnOverrides, extraContextActions)
  - Automatisch: VirtualTable, FilterBuilder, Presets, Toolbar, Refresh
  - Beispiele: ZeitreihenPage, BusinessPartnerPage, BatchSchedulePage
- **DetailPage-Template**: Props (pageKey, mode, dirty, validate, onSave, onDelete)
  - Automatisch: Toolbar (Neu/Speichern/Löschen), Dirty-Guard, Validierung
  - Modi: view, edit, new
  - Beispiele: BusinessPartnerDetailPage, CurrencyDetailPage, BatchScheduleDetailPage
- **Tab-System**: openTab/closeTab, sessionStorage-Persistenz, Singleton-Tabs, Close-Guards
- **Design-System**: CSS Custom Properties (tokens.css), keine CSS-Frameworks, Component-scoped CSS

- [ ] **Step 3: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Backend- und Frontend-Patterns"
```

---

### Task 6: Teil 1 — Security + REST-API + Shared Components + Build

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Security-Modell Abschnitt schreiben**

Inhalt:
- Keycloak OAuth2 Resource Server (JWT)
- Auto-Registrierung bei erstem Login (UserRegistrationFilter)
- 3-Ebenen-RBAC: Users → Groups → Permissions (Resource × ObjectType)
- Permission-Aggregation: Read/Write/Delete = OR über Gruppen, Field-Restrictions = AND
- Admin-Bypass: Admin sieht und darf alles
- Frontend: AuthContext mit canRead/canWrite/canDelete/isFieldRestricted

- [ ] **Step 2: REST-API-Referenz schreiben**

Vollständige Tabelle aller Endpoints mit Methode (farbcodiert), Pfad, Beschreibung.
Gruppiert nach: Zeitreihen, Objekte, Stammdaten (GP, Währungen), Batch-Jobs, Admin, Config.

Jeder Endpoint als Zeile:
```html
<tr>
    <td><span class="badge badge-get">GET</span></td>
    <td><code>/api/timeseries/{tsId}</code></td>
    <td>Zeitreihen-Header lesen</td>
</tr>
```

- [ ] **Step 3: Shared Components + Build Abschnitt schreiben**

Shared Components:
- FilterQueryBuilder (Operatoren, Whitelist, parametrisierte Werte)
- QueryRegistry + QueryLoader (XML → DB → ConcurrentHashMap)
- TableResponse + ColumnMeta
- Frontend: VirtualTable, FormField, Button, Card, Chip, StatusMessage, TreeView

Build & Deployment:
- Backend: `./gradlew build`, `./gradlew bootRun`, `./gradlew bootJar`
- Frontend: `npm run dev` (Vite, Port 5173, Proxy zu 8080), `npm run build`
- Konfiguration: Umgebungsvariablen (TS_JDBC_URL, TS_DB_USER, TS_DB_PASSWORD)

- [ ] **Step 4: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Security, REST-API, Shared Components, Build"
```

---

### Task 7: Teil 2 — Use Cases (Zeitreihen + Aggregation + Diagramme)

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Use Case "Zeitreihen verwalten" schreiben**

Inhalt:
- **Übersicht**: ZeitreihenPage → OverviewPage-Template, alle Zeitreihen mit Metadaten
- **Filtern**: FilterBuilder mit Operatoren, Presets speichern/laden
- **Editor öffnen**: Doppelklick oder Kontextmenü → TimeSeriesEditor (neuer Tab)
- **Mehrfachauswahl**: Checkboxen → "Im Editor öffnen" (Multi-Zeitreihen mit NaN-Auffüllung)
- **Werte bearbeiten**: Inline-Editing in ValuesTable, Dirty-Tracking, Speichern per Tag
- **Zeitraum wählen**: Start/Ende Datepicker, automatisch aus Zeitreihen-Laufzeit vorbelegt

- [ ] **Step 2: Use Case "Aggregation" schreiben**

Inhalt:
- **Auslösung**: Mehrfachauswahl in Übersicht → Kontextaktion "Summieren"
- **Backend**: POST /api/timeseries/aggregate mit tsIds[], start, end
- **Cross-Dimension**: Disaggregation auf kleinste gemeinsame Dimension
- **Unit-Konvertierung**: Automatisch auf gemeinsame Einheit
- **Parallel**: ExecutorService (10 Threads), nicht ForkJoinPool
- **SQL-Shortcut**: Stored Procedures für gleiche Dimension + Einheit (ts_sum_15min, ts_sum_1h)
- **Ergebnis**: Read-only Editor mit synthetischer Summen-Zeitreihe

- [ ] **Step 3: Use Case "Diagramme" schreiben**

Inhalt:
- 3 Bibliotheken zum Vergleich: Recharts, Chart.js, Lightweight Charts
- Umschaltbar per Dropdown im Editor
- Downsampling: max 5.000 Punkte (chartUtils.ts)
- Farben: 10 vordefinierte Serien-Farben (chartTypes.ts)
- Zoom/Pan je nach Bibliothek

- [ ] **Step 4: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Use Cases Zeitreihen, Aggregation, Diagramme"
```

---

### Task 8: Teil 2 — Use Cases (Stammdaten + Batch + Admin + Filter)

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Use Case "Geschäftspartner" schreiben**

- Übersicht: BusinessPartnerPage → OverviewPage-Template
- Detail: BusinessPartnerDetailPage → DetailPage-Template
- Ansprechpartner: Nested ContactPersonCard (collapsible), cascade delete
- CRUD-Flow: Neu → Formular → Speichern → Übersicht aktualisiert

- [ ] **Step 2: Use Case "Währungen" schreiben**

- Analog zu GP, aber einfacher (keine Nested-Entities)
- ISO-Code Validierung (3 Zeichen), Eindeutigkeit

- [ ] **Step 3: Use Case "Batchplanung" schreiben**

- Job-Katalog: Verfügbare Job-Typen aus Code (AbstractBatchJob)
- Schedule anlegen: Job-Typ wählen, Cron/Intervall/Kein Schedule
- Parameter: Dynamisches Formular basierend auf Job-Parameterdefinitionen
- Manuell triggern: "Jetzt ausführen" mit optionalen Parametern
- Historie: Alle Ausführungen mit Status (RUNNING/COMPLETED/FAILED), Logfile-Einsicht

- [ ] **Step 4: Use Case "Benutzerverwaltung" schreiben**

- Admin-only (isAdmin-Check)
- Benutzer: Liste, anlegen (Keycloak), Admin-Flag, aktivieren/deaktivieren, Passwort setzen
- Gruppen: Liste, anlegen, Mitglieder zuweisen/entfernen
- Berechtigungen: Matrix-Editor (Resource × Gruppe → Read/Write/Delete)
- Field-Restrictions: Feld-Level-Einschränkungen pro Gruppe

- [ ] **Step 5: Use Case "Sidebar-Navigation" + "Filter-System" schreiben**

Sidebar:
- Konfiguration per sidebar.xml (Backend)
- Gefiltert nach Berechtigungen (Admin-Nodes nur für Admins)
- Fallback auf Frontend-Default wenn API nicht erreichbar

Filter:
- FilterBuilder: Spalte + Operator + Wert (mehrstufig)
- Presets: Speichern, Laden, Default-Preset pro Seite
- Scopes: USER (privat) + GLOBAL (Admin)
- SQL-Preview im FilterBuilder

- [ ] **Step 6: Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Use Cases Stammdaten, Batch, Admin, Filter"
```

---

### Task 9: Review + Feinschliff

**Files:**
- Modify: `docs/documentation.html`

- [ ] **Step 1: Gesamtdokument im Browser prüfen**

- Alle Sprungmarken funktionieren
- Alle Tabellen korrekt formatiert
- Diagramme lesbar
- Keine Lücken im Inhalt

- [ ] **Step 2: Print-Preview prüfen (Ctrl+P)**

- Sidebar verschwindet
- Seitenumbrüche an richtigen Stellen (page-break Klasse bei h2)
- Tabellen nicht abgeschnitten
- Code-Blöcke lesbar

- [ ] **Step 3: Feinschliff**

- Tippfehler korrigieren
- Konsistente Terminologie prüfen
- Fehlende Querverweise ergänzen

- [ ] **Step 4: Abschluss-Commit**

```bash
git add docs/documentation.html
git commit -m "docs: Vollständige Projektdokumentation als HTML"
```
