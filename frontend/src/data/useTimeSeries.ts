import { useState, useMemo, useCallback, useRef } from 'react';
import { fetchHeader, fetchValues } from '../api/client';
import { calculateTimestampMs } from './timestampCalculator';
import type {
  TimeSeriesHeaderResponse,
  TimeSeriesValuesResponse,
  TimeSeriesRow,
} from '../api/types';

interface UseTimeSeriesResult {
  header: TimeSeriesHeaderResponse | null;
  rows: TimeSeriesRow[];
  loading: boolean;
  error: string | null;
  load: (tsId: number, start: string, end: string) => Promise<void>;
}

export function useTimeSeries(): UseTimeSeriesResult {
  const [header, setHeader] = useState<TimeSeriesHeaderResponse | null>(null);
  const [valuesResponse, setValuesResponse] = useState<TimeSeriesValuesResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

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

    setLoading(true);
    setError(null);
    setHeader(null);
    setValuesResponse(null);
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

  return { header, rows, loading, error, load };
}
