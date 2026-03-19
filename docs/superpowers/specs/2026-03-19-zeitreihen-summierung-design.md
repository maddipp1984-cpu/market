# Zeitreihen-Summierung (On-the-fly)

## Ziel
Selektierte Zeitreihen aus der Uebersicht on-the-fly summieren und als virtuelle read-only Zeitreihe im Editor anzeigen. Unterschiedliche Zeitdimensionen und kompatible Einheiten werden automatisch konvertiert.

## Backend

### Endpunkt: POST /api/timeseries/aggregate

**Request:**
```json
{
  "tsIds": [1, 2, 3],
  "start": "2023-01-01T00:00",
  "end": "2024-01-01T00:00"
}
```

**Response:**
```json
{
  "header": {
    "tsId": -1,
    "tsKey": "SUM(QH_00001, H_00001, D_00001)",
    "dimension": "QUARTER_HOUR",
    "unit": "kWh",
    "currency": null,
    "description": "Summierung von 3 Zeitreihen"
  },
  "values": {
    "start": "2023-01-01T00:00:00",
    "end": "2024-01-01T00:00:00",
    "dimension": "QUARTER_HOUR",
    "count": 35136,
    "values": [1.5, 2.3, ...]
  }
}
```

### Logik (in TimeSeriesClient oder neuem AggregationService)

1. Alle Header laden
2. Kleinste Dimension bestimmen (niedrigster `code`)
3. Ziel-Einheit = Einheit der ersten Zeitreihe
4. Einheiten-Kompatibilitaet pruefen: `source.isConvertibleTo(target)` oder `source.isCrossDomainConvertibleTo(target)` — sonst 400 Bad Request
5. Fuer jede Zeitreihe: `TimeSeriesClient.read(tsId, start, end, targetDimension, SUM, targetUnit)` — disaggregiert + konvertiert automatisch
6. Alle resultierenden Arrays elementweise summieren (NaN = 0)
7. Synthetischen Header + Values zurueckgeben

### Bestehende Infrastruktur nutzen
- `TimeSeriesClient.read(tsId, start, end, targetDim, AggregationFunction.SUM, targetUnit)` — macht Disaggregation + Unit-Konvertierung in korrekter Reihenfolge
- `DimensionConverter` — DST-aware Disaggregation
- `UnitConverter` — Faktor/Offset/Power-Energy Konvertierung
- Kein neuer DB-Zugriff noetig

## Frontend

### ZeitreihenPage: Kontextaktion "Summieren"
- Nur bei Mehrfachauswahl sichtbar (`multi: true`)
- Oeffnet neuen Editor-Tab mit `{ aggregateMode: 'sum', tsIds: [...] }`

### TimeSeriesEditorPage: Aggregations-Modus
- Erkennt `aggregateMode` in Tab-Params
- Ruft `POST /api/timeseries/aggregate` statt einzelne Zeitreihen
- Zeigt Ergebnis als einzelne read-only Zeitreihe
- Start/Ende waehlbar (wie normaler Editor)

### API-Client: neue Funktion
- `aggregateTimeSeries(tsIds, start, end)` → ruft den neuen Endpunkt auf
