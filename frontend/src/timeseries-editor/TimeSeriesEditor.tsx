import { useEffect, useRef, useState, useMemo } from 'react';
import { useMultiTimeSeries } from './data/useMultiTimeSeries';
import { getAvailableDimensions, aggregateMultiRows, dimensionLabels, type AggregationMode } from './data/aggregation';
import { ValuesTable } from './table/ValuesTable';
import type { Dimension } from '../api/types';

const EMPTY_EDITS: Map<string, number> = new Map();

interface TimeSeriesEditorProps {
  tsIds: number[];
  start: string;
  end: string;
}

export function TimeSeriesEditor({ tsIds, start, end }: TimeSeriesEditorProps) {
  const { headers, rows, edits, hasEdits, loading, saving, error, load, updateValue, save } = useMultiTimeSeries();
  const loadedRef = useRef('');
  const [filterStart, setFilterStart] = useState(start);
  const [filterEnd, setFilterEnd] = useState(end);
  const [decimals, setDecimals] = useState(5);
  const [viewDimension, setViewDimension] = useState<Dimension | null>(null);

  useEffect(() => {
    const key = `${tsIds.join(',')}|${start}|${end}`;
    if (key === loadedRef.current) return;
    loadedRef.current = key;
    setFilterStart(start);
    setFilterEnd(end);
    if (tsIds.length > 0 && start && end) {
      load(tsIds, start, end);
    }
  }, [tsIds, start, end, load]);

  useEffect(() => {
    if (headers.length === 0) return;
    const hasCurrencyOnly = headers.every(h => h.currency && (!h.unit || h.unit === h.currency));
    setDecimals(hasCurrencyOnly ? 2 : 5);
    setViewDimension(headers[0].dimension);
  }, [headers]);

  const filteredRows = useMemo(() => {
    if (!filterStart && !filterEnd) return rows;
    const startMs = filterStart ? new Date(filterStart).getTime() : -Infinity;
    const endMs = filterEnd ? new Date(filterEnd).getTime() : Infinity;
    return rows.filter(r => r.timestampMs >= startMs && r.timestampMs < endMs);
  }, [rows, filterStart, filterEnd]);

  const isAggregated = headers.length > 0 && viewDimension != null && viewDimension !== headers[0].dimension;

  const aggregationModes: AggregationMode[] = useMemo(
    () => headers.map(h => (h.currency && h.unit) ? 'avg' : 'sum'),
    [headers]
  );

  const displayRows = useMemo(() => {
    if (!isAggregated || !viewDimension) return filteredRows;
    return aggregateMultiRows(filteredRows, edits, headers.length, viewDimension, aggregationModes);
  }, [filteredRows, edits, headers.length, viewDimension, isAggregated, aggregationModes]);

  return (
    <div className="ts-editor">
      {loading && <div className="info">Lade Zeitreihe{tsIds.length > 1 ? 'n' : ''}...</div>}

      {error && <div className="error">{error}</div>}

      {headers.length > 0 && rows.length > 0 && (
        <div className="filter-bar">
          <label>
            Von
            <input
              type="datetime-local"
              value={filterStart}
              onChange={(e) => setFilterStart(e.target.value)}
            />
          </label>
          <label>
            Bis
            <input
              type="datetime-local"
              value={filterEnd}
              onChange={(e) => setFilterEnd(e.target.value)}
            />
          </label>
          <label>
            Ansicht
            <select
              value={viewDimension ?? ''}
              onChange={(e) => setViewDimension(e.target.value as Dimension)}
            >
              {getAvailableDimensions(headers[0].dimension).map(d => (
                <option key={d} value={d}>{dimensionLabels[d]}</option>
              ))}
            </select>
          </label>
          <label>
            Nachkommastellen
            <input
              type="number"
              min={0}
              max={10}
              value={decimals}
              onChange={(e) => setDecimals(Math.max(0, Math.min(10, parseInt(e.target.value, 10) || 0)))}
              style={{ width: '60px' }}
            />
          </label>
          {(filterStart !== start || filterEnd !== end) && (
            <button
              type="button"
              className="filter-reset"
              onClick={() => { setFilterStart(start); setFilterEnd(end); }}
            >
              Zurücksetzen
            </button>
          )}
          {hasEdits && (
            <button type="button" onClick={save} disabled={saving} className="save-btn">
              {saving ? 'Speichere...' : `Speichern (${edits.size})`}
            </button>
          )}
        </div>
      )}

      {headers.length > 0 && rows.length === 0 && !loading && !error && (
        <div className="info">Keine Werte im gewählten Zeitraum.</div>
      )}

      {headers.length > 0 && rows.length > 0 && displayRows.length === 0 && (
        <div className="info">Keine Werte im Filterbereich.</div>
      )}

      {displayRows.length > 0 && headers.length > 0 && viewDimension && (
        <ValuesTable
          rows={displayRows}
          headers={headers}
          edits={isAggregated ? EMPTY_EDITS : edits}
          dimension={viewDimension}
          decimals={decimals}
          readOnly={isAggregated}
          onEdit={updateValue}
        />
      )}
    </div>
  );
}
