# Design: Preisindex-Modul

## Übersicht

Neues Stammdaten-Modul für Preisindices (HPFC, Temperaturkurven etc.). Ein Index ist ein eigenständiges Objekt mit genau einer zugehörigen Zeitreihe (1:1). Die Daten werden manuell über einen eingebetteten Zeitreiheneditor gepflegt.

## Datenmodell

### Neue Tabelle `ts_index`

```sql
CREATE TABLE ts_index (
    index_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_id   BIGINT NOT NULL UNIQUE REFERENCES ts_object(object_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- Weitere Index-spezifische Spalten werden später ergänzt
- `object_id` ist UNIQUE (1:1 zu ts_object)
- ON DELETE CASCADE: Löschen des Objekts löscht auch den Index-Eintrag

### Bestehende Tabellen (keine Änderungen)

- **ts_object_type**: INDEX (type_id=5) existiert bereits
- **ts_object**: Trägt `object_key` (= Index-Name, unique), `description`, Typ INDEX
- **ts_header**: Verknüpft über `object_id`, trägt `time_dim`, `unit_id`, `currency_id`
- **ts_series_type**: Neuer Eintrag "INDEX" erforderlich (falls noch nicht vorhanden)

### Anlegen-Kette (eine Transaktion)

1. `ts_object` erstellen (type_id=5 INDEX, object_key = Name)
2. `ts_index` erstellen (object_id → neues Objekt)
3. `ts_header` erstellen (Dimension, Einheit/Währung, series_type=INDEX, object_id → selbes Objekt)

### Löschen-Kette (kaskadierend)

1. Zeitreihen-Werte löschen (via bestehende Delete-Procedures)
2. `ts_header` löschen
3. `ts_index` löschen
4. `ts_object` löschen

### Validierung

- **Name** (object_key): Pflicht, unique
- **Zeitdimension**: Pflicht (1=15min, 2=1h, 3=Tag, 4=Monat, 5=Jahr)
- **Einheit/Währung**: Mindestens eines muss gesetzt sein (beide erlaubt)
- **Reihenart**: Automatisch "INDEX" — kein Benutzer-Auswahlfeld

## Backend-Architektur

### Neues Modul `de.market.index/`

```
index/
  model/
    IndexEntity.java              -- JPA @Entity auf ts_index
  repository/
    IndexJpaRepository.java       -- JpaRepository (Einzel-CRUD)
    IndexOverviewRepository.java  -- jOOQ für Übersicht (JOIN ts_index + ts_object + ts_header)
  service/
    IndexService.java             -- @Service extends AbstractCrudService
  rest/
    IndexController.java          -- @RestController /api/indices
    dto/
      IndexDto.java               -- name, dimension, unitId, currencyId
```

### IndexService

- Erbt von `AbstractCrudService<IndexDto, IndexEntity, Long>`
- **create()**: Orchestriert ts_object → ts_index → ts_header in einer Transaktion
- **delete()**: Löscht Zeitreihen-Werte (via bestehende Procedures), dann ts_header → ts_index → ts_object
- **findById()**: JOIN über ts_index + ts_object + ts_header, liefert vollständiges DTO
- **validate()**: Name unique, Dimension gesetzt, mindestens Einheit oder Währung

### IndexOverviewRepository (jOOQ)

- Erbt von `AbstractOverviewRepository`
- Query joined ts_index + ts_object + ts_header
- Spalten: Name, Zeitdimension, Einheit, Währung, Erstelldatum, Datenbereich (first_date/last_date)

### REST-API

| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| GET | `/api/indices` | Alle Indices |
| POST | `/api/indices/query` | Gefilterte Übersicht |
| GET | `/api/indices/{id}` | Einzelner Index |
| POST | `/api/indices` | Index anlegen |
| PUT | `/api/indices/{id}` | Index aktualisieren |
| DELETE | `/api/indices/{id}` | Index + Zeitreihe löschen |

### ObjectType-Enum

- `INDEX(5, "Index")` zu `ObjectType.java` hinzufügen

## Frontend-Architektur

### Neue Dateien

```
frontend/src/pages/index/
  IndicesPage.tsx             -- Übersicht (OverviewPage-Template)
  IndexDetailPage.tsx         -- Detailmaske mit eingebettetem Editor
```

### IndicesPage (Übersicht)

- Nutzt `<OverviewPage>` mit `apiUrl="/api/indices"`
- **Kontextmenü** (Rechtsklick) mit zwei Einträgen:
  - "Zeitreihe anzeigen" → öffnet Detailmaske, lädt Editor read-only mit allen vorhandenen Daten
  - "Zeitreihe bearbeiten" → zeigt Von/Bis-Dialog, öffnet Detailmaske mit editierbarem Editor
- **Doppelklick** → Detailmaske (Stammdaten bearbeiten)
- **Neu-Button** → Detailmaske im New-Modus

### IndexDetailPage (Detailmaske)

- **Oberer Bereich**: Formularfelder
  - Name (Text, Pflicht)
  - Zeitdimension (Dropdown)
  - Einheit (Dropdown, optional)
  - Währung (Dropdown, optional)
  - Validierung: mindestens Einheit oder Währung
- **Unterer Bereich**: Eingebetteter `<TimeSeriesEditor>`
  - Nur sichtbar wenn Index bereits gespeichert (entityId vorhanden)
  - Read-Only-Modus bei "Zeitreihe anzeigen"
  - Edit-Modus bei "Zeitreihe bearbeiten" (Von/Bis aus Dialog)

### Von/Bis-Dialog

- Modaler Dialog mit zwei Datumsfeldern
- Erscheint bei "Zeitreihe bearbeiten" aus dem Kontextmenü
- Nach Bestätigung öffnet sich Detailmaske mit Editor im gewählten Zeitraum

### Tab-Registry (tabTypes.tsx)

- `indices` — Singleton, Übersichtsseite
- `index-detail` — Detailmaske, Params: `mode`, `entityId`, `editorMode` (view/edit), `dateFrom`, `dateTo`

### Sidebar

- Neuer Eintrag "Indices" unter "Stammdaten" in `sidebar.xml`

## Nicht im Scope

- Versionierung von Indices (kommt später als eigenes Feature)
- Berechnung/Import von Index-Werten (nur manuelle Eingabe)
- Spezifische Zusatzfelder in `ts_index` (Tabelle wird vorbereitet, Felder kommen nach Bedarf)
