# Market — Feature- und Architektur-Uebersicht

## 1. Zeitreihensystem (Kern)

### Datenmodell
- **TimescaleDB** (PostgreSQL-Extension) fuer performante Zeitreihenspeicherung
- **5 Zeitdimensionen**: Viertelstunde, Stunde, Tag, Monat, Jahr
- **Horizontales Modell**: QH/H speichern 92-100 bzw. 23-25 Werte pro Tag als Array (DST-aware)
- **Hypertables** mit automatischer Chunk-Verwaltung + Kompression nach 3-6 Monaten
- **Hash-Partitionierung** auf `ts_id` fuer schnellen Einzelreihen-Zugriff

### DST-Handling (Sommer-/Winterzeit)
- `TIMESTAMPTZ` speichert intern UTC — jeder Zeitpunkt eindeutig
- Array-Laenge variiert automatisch: 92 QH (Sommerzeit), 96 QH (normal), 100 QH (Winterzeit)
- Durchgaengig `Europe/Berlin` als Zeitzone

### Dimensions-Konvertierung
- **Aggregation** (fein → grob): QH → Stunde → Tag → Monat → Jahr
- **Disaggregation** (grob → fein): Gleichverteilung, DST-aware
- **Funktionen**: SUM, AVG (gewichtet nach DST-Tageslaenge), MIN, MAX
- Kaskadierung: z.B. QH → Monat laeuft automatisch ueber QH → Tag → Monat

### Unit-Konvertierung
- **Faktor-basiert**: kWh ↔ MWh ↔ GWh, kW ↔ MW, bar ↔ mbar etc.
- **Offset-basiert**: °C ↔ K
- **Cross-Domain**: Power ↔ Energy (kW × Stunden = kWh), DST-aware
- Automatische Erkennung des Konvertierungstyps anhand der Unit-Kategorie

---

## 2. On-the-fly Aggregation (Portfolio-Summierung)

### Funktionalitaet
- Beliebig viele Zeitreihen summieren (bis 100k+ getestet)
- **Cross-Dimension**: Verschiedene Dimensionen werden auf die kleinste disaggregiert
- **Cross-Unit**: Kompatible Einheiten werden automatisch konvertiert
- Ergebnis als virtuelle read-only Zeitreihe im Editor angezeigt

### Performance-Architektur (3-stufig)
1. **Gleiche Dimension + Einheit (QH/H)**: PL/pgSQL Stored Procedures (`ts_sum_15min`, `ts_sum_1h`) — Summierung komplett in PostgreSQL, nur Ergebnis wird uebertragen
2. **Gleiche Dimension + Einheit (Tag/Monat/Jahr)**: SQL `SUM(value) GROUP BY` in einer Query
3. **Cross-Dimension/Unit**: Parallele Java-Reads mit dediziertem ExecutorService (10 Threads), Disaggregation + Konvertierung im Speicher

### Benchmark (1000 QH-Zeitreihen, 1 Jahr)
- Stored Procedure: ~4.6s (komplett in DB)
- Parallele Java-Reads: ~5s (warm)
- Server-Gesamt: ~3.3s (nach Cache-Warmup)

---

## 3. Frontend (React SPA)

### Architektur
- **Tab-System** statt URL-Routing — IDE-artige Tabs, Zustand bleibt bei Tab-Wechsel erhalten
- **Tab-Persistenz**: sessionStorage sichert offene Tabs ueber Browser-Refresh
- **Sidebar**: XML-konfigurierbare Baumnavigation (Backend liefert Struktur per REST)
- **Design-System**: CSS Custom Properties (tokens.css), keine hardcodierten Farben

### Uebersichtsseiten (OverviewPage-Template)
- Generisches Template fuer alle Entitaeten (Zeitreihen, Geschaeftspartner, Waehrungen, Planungen)
- **VirtualTable**: TanStack Virtual — nur sichtbare Zeilen im DOM (performant bei 5000+ Zeilen)
- **Filter-System**: Spaltenbasiert, typsensitive Operatoren (LIKE, BETWEEN, IN, IS NULL)
- **Filter-Presets**: Speicherbar (GLOBAL/USER), als Default setzbar
- **Kontextmenue**: Rechtsklick auf Zeilen fuer Aktionen (Editor oeffnen, Summieren, Loeschen)
- **Mehrfachauswahl**: Checkboxen + Shift/Ctrl-Klick

### Zeitreihen-Editor
- **Multi-Zeitreihen**: N Zeitreihen nebeneinander mit gemeinsamer Datum-Spalte
- **Unterschiedliche Laufzeiten**: Automatische NaN-Auffuellung (kein Fehler)
- **Inline-Editing**: Zellenwerte direkt editierbar, Dirty-Tracking, parallel speichern
- **Copy/Paste**: Ctrl+C (TSV mit Header), Ctrl+V (aus Excel, deutsche Zahlenformate)
- **Dimensions-Aggregation**: Ansicht wechselbar (QH → Stunde → Tag → ...), read-only
- **Footer**: Min, Max, Avg, Sum ueber alle sichtbaren Werte
- **Schreiben**: Alle 5 Dimensionen (QH/H via writeDay, Tag/Monat/Jahr via writeSimple)

### Diagramm-Ansicht
- 3 Chart-Bibliotheken zum Vergleich: **Recharts** (SVG, Brush-Zoom), **Chart.js** (Canvas, Drag/Wheel-Zoom), **Lightweight Charts** (WebGL, TradingView-Stil)
- Legende, individuelle Farben pro Zeitreihe
- Gemeinsames Downsampling (max 5000 Punkte) in `chartUtils.ts`

### Detailmasken (DetailPage-Template)
- Modi: View, Edit, New
- Toolbar: Neu, Speichern, Speichern & Schliessen, Loeschen
- Validierung vor Speichern, Dirty-Warnung bei Tab-Schliessen
- Verwendet fuer: Geschaeftspartner (mit Ansprechpartner-Cards), Waehrungen, Batchplanungen

---

## 4. Stammdaten (JPA/Hibernate)

- **Geschaeftspartner**: CRUD mit verschachtelten Ansprechpartnern (Cascade ALL)
- **Waehrungen**: CRUD mit ISO-Code-Validierung
- **Objekte + Objekttypen**: Uebergeordnete Objekte mit 1:n zu Zeitreihen
- **Einheiten**: Referenztabelle mit 27 physikalischen Einheiten

### Architektur-Entscheidung: Dualer Persistenz-Ansatz
- **Raw JDBC** fuer alle Uebersichtsabfragen (Performance, fetchSize, kein N+1)
- **JPA/Hibernate** nur fuer Einzel-CRUD (findById, create, update, delete)
- `ddl-auto=validate` — Schema wird nicht automatisch geaendert

---

## 5. Scheduling / Batchplanung

- **Quartz Scheduler** mit JDBC Job-Store (persistent)
- **Template → Instanz**: Job-Typen im Code (Katalog), Planungen in der DB (n pro Typ)
- **Dynamische Parameter**: Job-Typen definieren Parameter-Schema, UI baut Formular automatisch
- **Ausfuehrungshistorie**: Logging pro Lauf mit Status (RUNNING/COMPLETED/FAILED)
- **Manueller Trigger**: Planungen koennen ad-hoc mit optionalen Parametern gestartet werden

---

## 6. Berechtigungssystem

- **Keycloak** OAuth2 (JWT) fuer Authentifizierung
- **3-Ebenen-Modell**: Seite (Dashboard, Zeitreihen, ...) → Objekttyp (Vertrag, Anschluss, ...) → Feld
- **Gruppen-basiert**: User → Gruppen → Permissions (can_read, can_write, can_delete)
- **Admin-UI**: 3 Verwaltungsseiten (Benutzer, Gruppen, Berechtigungsmatrix)
- **Sidebar-Filterung**: Backend filtert Navigationsbaum nach User-Permissions

---

## 7. Query-Registry

- SQL-Statements externalisiert in XML-Dateien (`src/main/resources/queries/*.xml`)
- Beim Start: XML → DB synchronisiert, dann in-memory Cache (ConcurrentHashMap)
- Zur Laufzeit nachladbar via REST (`POST /api/admin/queries/reload`)
- Trennung von Code und SQL — Queries versioniert, aenderbar ohne Recompile

---

## 8. Technologie-Stack

| Schicht | Technologie |
|---------|-------------|
| Backend | Java 17, Spring Boot 3.4.1 |
| Datenbank | TimescaleDB (PostgreSQL 16) |
| Auth | Keycloak (OAuth2 / JWT) |
| Frontend | React 18, TypeScript, Vite |
| Tabellen | TanStack Table + TanStack Virtual |
| Baum-Navigation | Headless Tree |
| Charts | Recharts, Chart.js, Lightweight Charts |
| Scheduling | Quartz Scheduler |
| Build | Gradle mit Spring Boot Plugin |
| Connection Pool | HikariCP (30 Connections) |

---

## 9. Codebase

| Bereich | Zeilen |
|---------|--------|
| Backend (Java) | ~11.400 |
| Frontend (TS/TSX) | ~6.600 |
| Frontend (CSS) | ~1.900 |
| SQL (Schema + Procedures) | ~1.100 |
| **Gesamt** | **~21.000** |

102 Commits, davon 35 in der letzten Session.
