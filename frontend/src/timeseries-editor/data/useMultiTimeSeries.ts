import { useState, useMemo, useCallback, useRef } from 'react';
import { fetchHeader, fetchValues, writeDay } from '../../api/client';
import { calculateTimestampMs } from './timestampCalculator';
import { toDateStringBerlin } from './aggregation';
import type {
  TimeSeriesHeaderResponse,
  TimeSeriesValuesResponse,
  MultiSeriesRow,
} from '../../api/types';

interface UseMultiTimeSeriesResult {
  headers: TimeSeriesHeaderResponse[];
  rows: MultiSeriesRow[];
  edits: Map<string, number>;
  hasEdits: boolean;
  loading: boolean;
  saving: boolean;
  error: string | null;
  load: (tsIds: number[], start: string, end: string) => Promise<void>;
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
  const abortRef = useRef<AbortController | null>(null);
  const tsIdsRef = useRef<number[]>([]);

  const rows = useMemo<MultiSeriesRow[]>(() => {
    if (valuesResponses.length === 0) return [];
    const first = valuesResponses[0];
    return first.values.map((_, i) => ({
      index: i + 1,
      timestampMs: calculateTimestampMs(first.start, first.dimension, i),
      values: valuesResponses.map(vr => vr.values[i]),
    }));
  }, [valuesResponses]);

  const load = useCallback(async (tsIds: number[], start: string, end: string) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    tsIdsRef.current = tsIds;

    setLoading(true);
    setError(null);
    setHeaders([]);
    setValuesResponses([]);
    setEdits(new Map());

    try {
      const [headerResults, valuesResults] = await Promise.all([
        Promise.all(tsIds.map(id => fetchHeader(id, controller.signal))),
        Promise.all(tsIds.map(id => fetchValues(id, start, end, controller.signal))),
      ]);

      if (controller.signal.aborted) return;

      // Validierung: gleiche Dimension
      const dimensions = new Set(headerResults.map(h => h.dimension));
      if (dimensions.size > 1) {
        setError('Alle Zeitreihen müssen die gleiche Dimension haben');
        return;
      }

      // Validierung: gleiche Anzahl Werte
      const counts = new Set(valuesResults.map(v => v.count));
      if (counts.size > 1) {
        setError('Alle Zeitreihen müssen die gleiche Anzahl Werte haben');
        return;
      }

      setHeaders(headerResults);
      setValuesResponses(valuesResults);
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
    if (edits.size === 0 || headers.length === 0) return false;
    setSaving(true);
    setError(null);

    // Snapshot der Edits zum Zeitpunkt des Save-Starts
    const savedEdits = new Map(edits);

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
  }, [edits, rows, headers, valuesResponses]);

  return {
    headers,
    rows,
    edits,
    hasEdits: edits.size > 0,
    loading,
    saving,
    error,
    load,
    updateValue,
    save,
  };
}
