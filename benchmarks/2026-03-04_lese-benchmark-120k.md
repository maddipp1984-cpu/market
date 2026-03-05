# Lese-Benchmark (120k Zeitreihen)

**Datum:** 2026-03-04
**Datenbestand:** 120.000 PERF_TEST-Zeitreihen, 12,8 Mio Zeilen (Tage), 11 GB, 48 Chunks
**Stichprobe:** 100 zufällige Zeitreihen

## Ergebnisse

| Test | Werte gesamt | Pro Zeitreihe (avg) | Min | Max | Gesamt |
|------|-------------|---------------------|-----|-----|--------|
| Jahr 2024 (365 Tage) | 3,51 Mio | 4,6 ms | 1,6 ms | 63,4 ms | 461,2 ms |
| Juni 2024 (30 Tage) | 288k | 1,1 ms | 1,0 ms | 1,9 ms | 111,9 ms |

## Kontext

- Vorheriger Benchmark mit 20k Zeitreihen: ~2,4 ms/Zeitreihe (Jahr), ~0,8 ms (Monat)
- Mit 120k Zeitreihen leicht höhere Latenzen (mehr Chunks, größere Tabelle)
- Max-Wert 63,4 ms beim Jahres-Lesen vermutlich durch Cold-Chunk (erster Zugriff nach Kompression)
