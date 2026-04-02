# Design: Modul "Reihenart" (Series Type)

## Zusammenfassung

Neues Stammdaten-Modul zur Pflege von Reihenarten. Jede Reihenart hat ein Kuerzel (unique), einen Namen und eine feste Kategorie (Finanziell/Physikalisch). Wird als Pflichtfeld an `ts_header` gehaengt.

## Datenmodell

### Neue Tabelle `ts_series_type`

```sql
CREATE TABLE ts_series_type (
    series_type_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    category        SMALLINT NOT NULL  -- 1=FINANCIAL, 2=PHYSICAL
);
```

### Aenderung an `ts_header`

```sql
ALTER TABLE ts_header ADD COLUMN series_type_id SMALLINT NOT NULL REFERENCES ts_series_type(series_type_id);
```

Da `ts_header` bereits Daten enthaelt, muss die Migration einen Default-Wert setzen oder in zwei Schritten erfolgen (Spalte nullable hinzufuegen, Daten fuellen, NOT NULL setzen).

## Java Enum

```java
public enum SeriesCategory {
    FINANCIAL(1, "Finanziell"),
    PHYSICAL(2, "Physikalisch");
}
```

Numerischer Code in DB (SMALLINT), Label fuer Frontend. Pattern analog zu `TimeDimension`.

## Backend-Struktur

Folgt dem Currency-Pattern (einfache Stammdaten ohne Relationen).

```
de.market.seriestype/
    model/
        SeriesTypeEntity.java              -- @Entity auf ts_series_type
        SeriesCategory.java                -- Enum (FINANCIAL, PHYSICAL)
    repository/
        SeriesTypeJpaRepository.java       -- existsByCode, existsByCodeAndIdNot
        SeriesTypeOverviewRepository.java  -- jOOQ, extends AbstractOverviewRepository
    service/
        SeriesTypeService.java             -- extends AbstractCrudService
    rest/
        SeriesTypeController.java          -- /api/series-types
        dto/
            SeriesTypeDto.java             -- id, code, name, category
```

### REST-API

| Method | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/api/series-types` | Uebersicht (TableResponse) |
| POST | `/api/series-types/query` | Gefilterte Uebersicht |
| GET | `/api/series-types/{id}` | Einzelner Datensatz (DTO) |
| POST | `/api/series-types` | Anlegen |
| PUT | `/api/series-types/{id}` | Aendern |
| DELETE | `/api/series-types/{id}` | Loeschen (FK-Schutz via Exception) |

### Validierung

- `code`: Pflichtfeld, nicht leer, UNIQUE
- `name`: Pflichtfeld, nicht leer
- `category`: Pflichtfeld, muss gueltiger SeriesCategory-Code sein (1 oder 2)

### Overview-Query (jOOQ)

Kategorie-Spalte per CASE-Ausdruck auf Label gemappt:

```sql
SELECT series_type_id AS id, code, name,
       CASE category WHEN 1 THEN 'Finanziell' WHEN 2 THEN 'Physikalisch' END AS category
FROM ts_series_type
ORDER BY code
```

## Frontend

### Neue Dateien

- `frontend/src/pages/ReihenartenPage.tsx` — OverviewPage (singleton Tab)
- `frontend/src/pages/reihenart/ReihenartDetailPage.tsx` — DetailPage mit Combobox fuer Kategorie

### Tab-Types

- `reihenarten` (singleton, Uebersicht)
- `reihenart-detail` (Detail/Bearbeitung)

### API-Client

- `fetchSeriesType(id)` — GET einzeln
- `saveSeriesType(dto)` — POST (neu) / PUT (bestehend)
- `deleteSeriesType(id)` — DELETE

### TypeScript-Typen

```typescript
export interface SeriesTypeDto {
  id: number | null;
  code: string;
  name: string;
  category: number;  // 1=FINANCIAL, 2=PHYSICAL
}
```

### DetailPage-Formular

- Textfeld: Kuerzel (code)
- Textfeld: Name
- Combobox/Select: Kategorie (Finanziell, Physikalisch)

### Sidebar

Neuer Eintrag unter "Stammdaten" in `sidebar.xml`:

```xml
<item id="reihenarten" tabType="reihenarten" />
```

## Auswirkung auf bestehenden Code

### TimeSeriesOverviewRepository

- JOIN auf `ts_series_type` hinzufuegen
- Neue Spalte "Reihenart" (code oder name) in der Zeitreihen-Uebersicht

### Zeitreihen-Detailmaske (spaeter)

- Combobox fuer Reihenart-Auswahl (nicht in diesem Scope)

## Nicht im Scope

- Zeitreihen-Detailmaske mit Reihenart-Auswahl (eigenes Feature)
- Seed-Daten (werden bei Bedarf vom Anwender angelegt)
