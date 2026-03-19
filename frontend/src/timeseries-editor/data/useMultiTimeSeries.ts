import { useState, useMemo, useCallback, useRef } from 'react';
import { fetchHeader, fetchValues, writeDay, writeSimpleValue, aggregateTimeSeries } from '../../api/client';
import type { TimingInfo } from '../../api/client';
import { calculateTimestampMs } from './timestampCalculator';
import { toDateStringBerlin } from './aggregation';
import type {
  Dimension,
  TimeSeriesHeaderResponse,
  TimeSeriesValuesResponse,
  MultiSeriesRow,
} from '../../api/types';

/**
 * Berechnet die Anzahl Slots zwischen zwei Timestamps fuer eine Dimension.
 */
function findSlotOffset(globalStartMs: number, seriesStartMs: number, dimension: Dimension): number {
  if (globalStartMs === seriesStartMs) return 0;
  const diffMs = seriesStartMs - globalStartMs;
  switch (dimension) {
    case 'QUARTER_HOUR': return Math.round(diffMs / (15 * 60_000));
    case 'HOUR': return Math.round(diffMs / (60 * 60_000));
    case 'DAY': return Math.round(diffMs / (24 * 60 * 60_000));
    case 'MONTH': {
      const a = new Date(globalStartMs);
      const b = new Date(seriesStartMs);
      return (b.getFullYear() - a.getFullYear()) * 12 + (b.getMonth() - a.getMonth());
    }
    case 'YEAR': {
      const a = new Date(globalStartMs);
      const b = new Date(seriesStartMs);
      return b.getFullYear() - a.getFullYear();
    }
  }
}

export interface LoadTiming {
  headerTimings: TimingInfo[];
  valuesTimings: TimingInfo[];
  totalMs: number;
}

interface UseMultiTimeSeriesResult {
  headers: TimeSeriesHeaderResponse[];
  rows: MultiSeriesRow[];
  edits: Map<string, number>;
  hasEdits: boolean;
  loading: boolean;
  saving: boolean;
  error: string | null;
  loadTiming: LoadTiming | null;
  load: (tsIds: number[], start: string, end: string, aggregateMode?: string) => Promise<void>;
  updateValue: (seriesIdx: number, rowIndex: number, value: number) => void;
  save: () => Promise<boolean>;
}

export function useMultiTimeSeries(): UseMultiTimeSeriesResult {
  const [headers, setHeaders] = useState<TimeSeriesHeaderResponse[]>([]);
  const [valuesResponses, setValuesResponses] = useState<TimeSeriesValuesResponse[]>([]);
  const [edits, setEdits] = useState<Map<string, number>>(new Map());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadTiming, setLoadTiming] = useState<LoadTiming | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const tsIdsRef = useRef<number[]>([]);
  const editsRef = useRef(edits);
  editsRef.current = edits;

  const rows = useMemo<MultiSeriesRow[]>(() => {
    if (valuesResponses.length === 0) return [];

    // Finde den groessten Zeitraum (fruehester Start, meiste Werte ab diesem Start)
    const dim = valuesResponses[0].dimension;
    const startTimestamps = valuesResponses.map(vr => calculateTimestampMs(vr.start, dim, 0));
    const globalStartMs = Math.min(...startTimestamps);
    const globalStartIdx = startTimestamps.indexOf(globalStartMs);
    const globalStartStr = valuesResponses[globalStartIdx].start;

    // Berechne fuer jede Serie: Offset (Anzahl Slots zwischen globalStart und ihrem Start)
    // und wie viele Gesamt-Slots noetig sind
    const offsets: number[] = [];
    let totalSlots = 0;
    for (let s = 0; s < valuesResponses.length; s++) {
      const vr = valuesResponses[s];
      const offset = findSlotOffset(globalStartMs, startTimestamps[s], dim);
      offsets.push(offset);
      totalSlots = Math.max(totalSlots, offset + vr.values.length);
    }

    // Baue Zeilen: fuer jeden Slot den Timestamp + Wert pro Serie (NaN wenn ausserhalb)
    const result: MultiSeriesRow[] = [];
    for (let i = 0; i < totalSlots; i++) {
      const values: number[] = [];
      for (let s = 0; s < valuesResponses.length; s++) {
        const localIdx = i - offsets[s];
        if (localIdx >= 0 && localIdx < valuesResponses[s].values.length) {
          values.push(valuesResponses[s].values[localIdx]);
        } else {
          values.push(NaN);
        }
      }
      result.push({
        index: i + 1,
        timestampMs: calculateTimestampMs(globalStartStr, dim, i),
        values,
      });
    }
    return result;
  }, [valuesResponses]);

  const load = useCallback(async (tsIds: number[], start: string, end: string, aggregateMode?: string) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    tsIdsRef.current = tsIds;

    setLoading(true);
    setError(null);
    setHeaders([]);
    setValuesResponses([]);
    setEdits(new Map());
    setLoadTiming(null);

    try {
      const t0 = performance.now();

      if (aggregateMode === 'sum') {
        // Aggregations-Modus: Backend summiert alles
        const result = await aggregateTimeSeries(tsIds, start, end, controller.signal);
        if (controller.signal.aborted) return;
        const totalMs = Math.round(performance.now() - t0);

        setHeaders([result.header]);
        setValuesResponses([result.values]);
        setLoadTiming({
          headerTimings: [result.timing],
          valuesTimings: [result.timing],
          totalMs,
        });
      } else {
        // Normaler Modus: einzelne Zeitreihen laden
        const [headerResults, valuesResults] = await Promise.all([
          Promise.all(tsIds.map(id => fetchHeader(id, controller.signal))),
          Promise.all(tsIds.map(id => fetchValues(id, start, end, controller.signal))),
        ]);

        if (controller.signal.aborted) return;
        const totalMs = Math.round(performance.now() - t0);

        // Validierung: gleiche Dimension (nur im normalen Modus)
        const dimensions = new Set(headerResults.map(h => h.data.dimension));
        if (dimensions.size > 1) {
          setError('Alle Zeitreihen müssen die gleiche Dimension haben');
          return;
        }

        setHeaders(headerResults.map(r => r.data));
        setValuesResponses(valuesResults.map(r => r.data));
        setLoadTiming({
          headerTimings: headerResults.map(r => r.timing),
          valuesTimings: valuesResults.map(r => r.timing),
          totalMs,
        });
      }
    } catch (e) {
      if (controller.signal.aborted) return;
      setError(e instanceof Error ? e.message : 'Unbekannter Fehler');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  const updateValue = useCallback((seriesIdx: number, rowIndex: number, value: number) => {
    setEdits(prev => {
      const next = new Map(prev);
      next.set(`${seriesIdx}:${rowIndex}`, value);
      return next;
    });
  }, []);

  const save = useCallback(async (): Promise<boolean> => {
    // Snapshot aus Ref — stabil, keine Race Condition mit neuen Edits
    const savedEdits = new Map(editsRef.current);
    if (savedEdits.size === 0 || headers.length === 0) return false;
    setSaving(true);
    setError(null);

    try {
      // Gruppiere Edits nach Serie
      const editsBySeries = new Map<number, Map<number, number>>();
      for (const [key, value] of savedEdits) {
        const [seriesIdxStr, rowIndexStr] = key.split(':');
        const seriesIdx = parseInt(seriesIdxStr, 10);
        const rowIndex = parseInt(rowIndexStr, 10);
        if (!editsBySeries.has(seriesIdx)) {
          editsBySeries.set(seriesIdx, new Map());
        }
        editsBySeries.get(seriesIdx)!.set(rowIndex, value);
      }

      // Rows nach Datum gruppieren (einmalig, O(n))
      const rowsByDate = new Map<string, MultiSeriesRow[]>();
      for (const row of rows) {
        const dateStr = toDateStringBerlin(row.timestampMs);
        let group = rowsByDate.get(dateStr);
        if (!group) {
          group = [];
          rowsByDate.set(dateStr, group);
        }
        group.push(row);
      }

      // Pro Serie: geänderte Tage finden und parallel speichern
      const writePromises: Promise<void>[] = [];
      for (const [seriesIdx, seriesEdits] of editsBySeries) {
        const dim = headers[seriesIdx].dimension;
        const isSubdaily = dim === 'QUARTER_HOUR' || dim === 'HOUR';

        if (isSubdaily) {
          // QH/H: Array pro Tag schreiben
          const changedDates = new Set<string>();
          for (const rowIndex of seriesEdits.keys()) {
            const row = rows[rowIndex - 1];
            if (row) changedDates.add(toDateStringBerlin(row.timestampMs));
          }

          for (const dateStr of changedDates) {
            const dayRows = rowsByDate.get(dateStr) ?? [];
            const values = dayRows.map(r => {
              const editKey = `${seriesIdx}:${r.index}`;
              return savedEdits.has(editKey) ? savedEdits.get(editKey)! : r.values[seriesIdx];
            });
            writePromises.push(writeDay(headers[seriesIdx].tsId, { date: dateStr, values }));
          }
        } else {
          // Tag/Monat/Jahr: Einzelwert pro Datum schreiben
          for (const [rowIndex, value] of seriesEdits) {
            const row = rows[rowIndex - 1];
            if (row) {
              const dateStr = toDateStringBerlin(row.timestampMs);
              writePromises.push(writeSimpleValue(headers[seriesIdx].tsId, dateStr, value));
            }
          }
        }
      }
      await Promise.all(writePromises);

      // Edits in Originaldaten übernehmen
      const newResponses = valuesResponses.map((vr, seriesIdx) => {
        const seriesEdits = editsBySeries.get(seriesIdx);
        if (!seriesEdits) return vr;
        const newValues = [...vr.values];
        for (const [rowIndex, val] of seriesEdits) {
          newValues[rowIndex - 1] = val;
        }
        return { ...vr, values: newValues };
      });
      setValuesResponses(newResponses);

      // Nur die gespeicherten Edits entfernen, neue behalten
      setEdits(prev => {
        const next = new Map(prev);
        for (const key of savedEdits.keys()) {
          next.delete(key);
        }
        return next;
      });
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Fehler beim Speichern');
      return false;
    } finally {
      setSaving(false);
    }
  }, [rows, headers, valuesResponses]);

  return {
    headers,
    rows,
    edits,
    hasEdits: edits.size > 0,
    loading,
    saving,
    error,
    loadTiming,
    load,
    updateValue,
    save,
  };
}
