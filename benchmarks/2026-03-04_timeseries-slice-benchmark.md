# TimeSeriesSlice Benchmark 2026-03-04

## Testumgebung
- 20.000 PERF_TEST-Zeitreihen (PERF_TEST_00001–20000)
- 12,80 Mio Zeilen in ts_values_15min
- Tabellengröße: 11 GB, 48 Chunks
- Stichprobe: 100 zufällige Zeitreihen
- HikariCP Pool: 4 Connections
- Lokal (localhost), Windows 11

## Ergebnisse (TimeSeriesSlice — flaches double[])

| Test | Pro Zeitreihe (avg) | Min | Max | Gesamt (100 ZR) |
|------|---------------------|-----|-----|-----------------|
| Jahr 2024 (35.136 Werte) | 7,4 ms | 5,0 ms | 39,8 ms | 740 ms |
| Juni 2024 (2.880 Werte) | 1,5 ms | 1,3 ms | 3,4 ms | 150 ms |

## Vergleich mit vorherigem Benchmark (gleiche Daten, gleiche Maschine)

| Test | Vorher (Raw/Map) | Jetzt (Slice/double[]) | Verbesserung |
|------|------------------|------------------------|-------------|
| Jahr komplett | 10,8 ms | 7,4 ms | **31% schneller** |
| Monatsbereich | 1,39 ms | 1,5 ms | ~gleich |
| Expanded (entfernt) | 66,4 ms | — | — |

## Analyse
- Jahres-Lesen 31% schneller: kein `ts_date`-Parsing mehr, kein Map-Overhead, nur `SELECT vals`
- Monatsbereich marginal langsamer (Messungenauigkeit, gleiche Größenordnung)
- `readExpanded()` komplett entfernt — war 6x langsamer als Raw und ist nicht mehr nötig
- Speicherverbrauch pro Zeitreihe/Jahr: ~274 KB (flaches double[]) statt ~4,3 MB (DataPoint-Objekte)
