# Zeitreihen-Uebersichtsseite

## Ziel
Neue Uebersichtsseite fuer Zeitreihen nach dem bestehenden OverviewPage-Pattern. Zeigt alle `ts_header`-Eintraege mit gejointen Metadaten. Mehrfachauswahl oeffnet den bestehenden TimeSeriesEditorPage mit allen selektierten Zeitreihen.

## Scope
- Nur Lesen + Editor oeffnen (kein Loeschen, kein Anlegen — kommt spaeter mit Detailmaske)

## Backend

### SQL-Query (`queries/timeseries.xml`)
- Key: `timeseries/overview`
- JOIN auf `ts_unit` (INNER), `ts_currency` (LEFT), `ts_object` (LEFT)
- `time_dim` als lesbarer Text via CASE:
  - 1 → '15 Minuten'
  - 2 → '1 Stunde'
  - 3 → 'Tag'
  - 4 → 'Monat'
  - 5 → 'Jahr'
- Spalten: ts_id, ts_key, dimension (Text), symbol (Einheit), iso_code (Waehrung), object_key (Objekt), description, created_at, updated_at
- ORDER BY ts_key

### TimeSeriesOverviewRepository (Raw JDBC)
- Package: `de.market.timeseries.repository`
- Pattern wie `CurrencyOverviewRepository`
- `findAllAsRows()` + `findFiltered(whereSql, params)`
- Nutzt `QueryRegistry` mit Key `"timeseries/overview"`

### TimeSeriesOverviewService
- Package: `de.market.timeseries.api`
- Delegiert an `TimeSeriesOverviewRepository`
- Methoden: `findAllAsRows()`, `findFiltered()`

### TimeSeriesOverviewController (`/api/timeseries-overview`)
- Package: `de.market.timeseries.rest`
- Eigener Controller (nicht im bestehenden TimeSeriesController)
- `GET /` → `TableResponse` (alle Zeitreihen)
- `POST /query` → `TableResponse` (gefiltert)
- COLUMNS:

| key | label | sqlColumn | type |
|-----|-------|-----------|------|
| id | ID | ts_id | NUMBER |
| key | Schluessel | ts_key | TEXT |
| dimension | Dimension | dimension | TEXT |
| unit | Einheit | symbol | TEXT |
| currency | Waehrung | iso_code | TEXT |
| object | Objekt | object_key | TEXT |
| description | Beschreibung | h.description | TEXT |
| createdAt | Erstellt | created_at | TIMESTAMP |
| updatedAt | Geaendert | updated_at | TIMESTAMP |

- `ALLOWED_SQL_COLUMNS` aus COLUMNS fuer FilterQueryBuilder

## Frontend

### ZeitreihenPage.tsx (`pages/`)
- Nutzt `<OverviewPage>` mit `apiUrl="/api/timeseries-overview"`
- `pageKey="zeitreihen"`, `resourceKey="zeitreihen"`
- `columnOverrides`: id hidden
- `onRowDoubleClick`: Oeffnet einzelne Zeitreihe im Editor
- `extraContextActions`: "Im Editor oeffnen" — oeffnet alle selektierten Zeitreihen zusammen

### Tab-Integration
- Neuer Eintrag in `tabTypes.tsx` (Typ: `zeitreihen`, Label: `Zeitreihen`, Komponente: `ZeitreihenPage`)
- Sidebar-Eintrag in `sidebar.xml`

### Editor-Anbindung
- `openTab('timeseries-editor', { tsIds: [1, 2, 3] })` uebergibt Array der selektierten ts_ids
- TimeSeriesEditorPage liest `tsIds` aus Tab-Params und laedt diese Zeitreihen
- Bestehende Logik (manuelles Eingeben von tsId) bleibt als Fallback erhalten

## Nicht im Scope (spaeter)
- Detailmaske fuer Zeitreihen-Metadaten (Bearbeiten, Anlegen)
- Loeschen aus der Uebersicht
- Permission-Checks (AUTH10-13)
