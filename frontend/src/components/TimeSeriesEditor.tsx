import { useEffect, useRef, useState, useMemo } from 'react';
import { useTimeSeries } from '../data/useTimeSeries';
import { getAvailableDimensions, aggregateRows } from '../data/aggregation';
import { HeaderInfo } from '../table/HeaderInfo';
import { ValuesTable } from '../table/ValuesTable';
import type { Dimension } from '../api/types';

const dimensionLabels: Record<Dimension, string> = {
  QUARTER_HOUR: '15 Minuten',
  HOUR: '1 Stunde',
  DAY: 'Tag',
  MONTH: 'Monat',
  YEAR: 'Jahr',
};

interface TimeSeriesEditorProps {
  tsId: number;
  start: string;
  end: string;
}

export function TimeSeriesEditor({ tsId, start, end }: TimeSeriesEditorProps) {
  const { header, rows, edits, hasEdits, loading, saving, error, load, updateValue, save } = useTimeSeries();
  const loadedRef = useRef('');
  const [filterStart, setFilterStart] = useState(start);
  const [filterEnd, setFilterEnd] = useState(end);
  const [decimals, setDecimals] = useState(5);
  const [viewDimension, setViewDimension] = useState<Dimension | null>(null);

  useEffect(() => {
    const key = `${tsId}|${start}|${end}`;
    if (key === loadedRef.current) return;
    loadedRef.current = key;
    setFilterStart(start);
    setFilterEnd(end);
    if (tsId > 0 && start && end) {
      load(tsId, start, end);
    }
  }, [tsId, start, end, load]);

  useEffect(() => {
    if (!header) return;
    const currencyOnly = header.currency && (!header.unit || header.unit === header.currency);
    setDecimals(currencyOnly ? 2 : 5);
    setViewDimension(header.dimension);
  }, [header]);

  const filteredRows = useMemo(() => {
    if (!filterStart && !filterEnd) return rows;
    const startMs = filterStart ? new Date(filterStart).getTime() : -Infinity;
    const endMs = filterEnd ? new Date(filterEnd).getTime() : Infinity;
    return rows.filter(r => r.timestampMs >= startMs && r.timestampMs < endMs);
  }, [rows, filterStart, filterEnd]);

  const isAggregated = header != null && viewDimension != null && viewDimension !== header.dimension;

  const displayRows = useMemo(() => {
    if (!isAggregated || !viewDimension) return filteredRows;
    return aggregateRows(filteredRows, edits, viewDimension);
  }, [filteredRows, edits, viewDimension, isAggregated]);

  return (
    <div className="ts-editor">
      {loading && <div className="info">Lade Zeitreihe...</div>}

      {error && <div className="error">{error}</div>}

      {header && (
        <div className="ts-editor-header">
          <HeaderInfo header={header} />
          {hasEdits && (
            <button type="button" onClick={save} disabled={saving} className="save-btn">
              {saving ? 'Speichere...' : `Speichern (${edits.size})`}
            </button>
          )}
        </div>
      )}

      {header && rows.length > 0 && (
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
              {header && getAvailableDimensions(header.dimension).map(d => (
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
        </div>
      )}

      {header && rows.length === 0 && !loading && !error && (
        <div className="info">Keine Werte im gewählten Zeitraum.</div>
      )}

      {header && rows.length > 0 && displayRows.length === 0 && (
        <div className="info">Keine Werte im Filterbereich.</div>
      )}

      {displayRows.length > 0 && header && viewDimension && (
        <ValuesTable
          rows={displayRows}
          edits={isAggregated ? new Map() : edits}
          unit={header.unit}
          dimension={viewDimension}
          decimals={decimals}
          readOnly={isAggregated}
          onEdit={updateValue}
        />
      )}
    </div>
  );
}
