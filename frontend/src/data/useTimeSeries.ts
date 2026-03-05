import { useState, useMemo, useCallback, useRef } from 'react';
import { fetchHeader, fetchValues, writeDay } from '../api/client';
import { calculateTimestampMs } from './timestampCalculator';
import type {
  TimeSeriesHeaderResponse,
  TimeSeriesValuesResponse,
  TimeSeriesRow,
} from '../api/types';

interface UseTimeSeriesResult {
  header: TimeSeriesHeaderResponse | null;
  rows: TimeSeriesRow[];
  edits: Map<number, number>;
  hasEdits: boolean;
  loading: boolean;
  saving: boolean;
  error: string | null;
  load: (tsId: number, start: string, end: string) => Promise<void>;
  updateValue: (index: number, value: number) => void;
  save: () => Promise<void>;
}

function toDateString(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function useTimeSeries(): UseTimeSeriesResult {
  const [header, setHeader] = useState<TimeSeriesHeaderResponse | null>(null);
  const [valuesResponse, setValuesResponse] = useState<TimeSeriesValuesResponse | null>(null);
  const [edits, setEdits] = useState<Map<number, number>>(new Map());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const tsIdRef = useRef<number>(0);

  const rows = useMemo<TimeSeriesRow[]>(() => {
    if (!valuesResponse) return [];
    const { start, dimension, values } = valuesResponse;
    return values.map((value, i) => ({
      index: i + 1,
      timestampMs: calculateTimestampMs(start, dimension, i),
      value,
    }));
  }, [valuesResponse]);

  const load = useCallback(async (tsId: number, start: string, end: string) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    tsIdRef.current = tsId;

    setLoading(true);
    setError(null);
    setHeader(null);
    setValuesResponse(null);
    setEdits(new Map());
    try {
      const [h, v] = await Promise.all([
        fetchHeader(tsId, controller.signal),
        fetchValues(tsId, start, end, controller.signal),
      ]);
      if (controller.signal.aborted) return;
      setHeader(h);
      setValuesResponse(v);
    } catch (e) {
      if (controller.signal.aborted) return;
      setError(e instanceof Error ? e.message : 'Unbekannter Fehler');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  const updateValue = useCallback((index: number, value: number) => {
    setEdits(prev => {
      const next = new Map(prev);
      next.set(index, value);
      return next;
    });
  }, []);

  const save = useCallback(async () => {
    if (edits.size === 0 || !header) return;
    setSaving(true);
    setError(null);
    try {
      // Finde geänderte Tage
      const changedDates = new Set<string>();
      for (const idx of edits.keys()) {
        const row = rows[idx - 1];
        if (row) changedDates.add(toDateString(row.timestampMs));
      }

      // Pro Tag: alle Werte sammeln (original + edits)
      for (const dateStr of changedDates) {
        const dayRows = rows.filter(r => toDateString(r.timestampMs) === dateStr);
        const values = dayRows.map(r => edits.has(r.index) ? edits.get(r.index)! : r.value);
        await writeDay(tsIdRef.current, { date: dateStr, values });
      }

      // Edits in Originaldaten übernehmen
      if (valuesResponse) {
        const newValues = [...valuesResponse.values];
        for (const [idx, val] of edits) {
          newValues[idx - 1] = val;
        }
        setValuesResponse({ ...valuesResponse, values: newValues });
      }
      setEdits(new Map());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Fehler beim Speichern');
    } finally {
      setSaving(false);
    }
  }, [edits, rows, header, valuesResponse]);

  return { header, rows, edits, hasEdits: edits.size > 0, loading, saving, error, load, updateValue, save };
}
