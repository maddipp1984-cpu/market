import { useEffect, useRef, useState, useMemo } from 'react';
import { useTimeSeries } from '../data/useTimeSeries';
import { HeaderInfo } from '../table/HeaderInfo';
import { ValuesTable } from '../table/ValuesTable';

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

  const filteredRows = useMemo(() => {
    if (!filterStart && !filterEnd) return rows;
    const startMs = filterStart ? new Date(filterStart).getTime() : -Infinity;
    const endMs = filterEnd ? new Date(filterEnd).getTime() : Infinity;
    return rows.filter(r => r.timestampMs >= startMs && r.timestampMs < endMs);
  }, [rows, filterStart, filterEnd]);

  return (
    <div className="ts-editor">
      {loading && <div className="info">Lade Zeitreihe...</div>}

      {error && <div className="error">{error}</div>}

      {header && (
        <div className="ts-editor-header">
          <HeaderInfo header={header} rowCount={filteredRows.length} />
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

      {header && rows.length > 0 && filteredRows.length === 0 && (
        <div className="info">Keine Werte im Filterbereich.</div>
      )}

      {filteredRows.length > 0 && header && (
        <ValuesTable
          rows={filteredRows}
          edits={edits}
          unit={header.unit}
          dimension={header.dimension}
          onEdit={updateValue}
        />
      )}
    </div>
  );
}
