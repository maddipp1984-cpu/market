# Lese-Benchmark 2026-03-04

## Testumgebung
- 20.000 PERF_TEST-Zeitreihen (PERF_TEST_00001–20000)
- 12,80 Mio Zeilen in ts_values_15min
- Tabellengröße: 11 GB, 48 Chunks
- Stichprobe: 100 zufällige Zeitreihen
- HikariCP Pool: 4 Connections
- Lokal (localhost), Windows 11

## Ergebnisse

| Test | Pro Zeitreihe (avg) | Min | Max | Gesamt (100 ZR) |
|------|---------------------|-----|-----|-----------------|
| Raw komplett (1 Jahr) | 10,8 ms | 5,4 ms | 41,3 ms | 1,08 s |
| Expanded komplett (1 Jahr) | 66,4 ms | 39,8 ms | 143,4 ms | 6,64 s |
| Monatsbereich Raw (Juni) | 1,39 ms | 1,26 ms | 1,69 ms | 139 ms |

## Datenpunkte pro Zeitreihe
- Raw komplett: ~636 Tage, ~61.100 Werte
- Expanded komplett: ~61.100 DataPoints
- Monatsbereich: ~2.880 Werte (30 Tage x 96 QH)
