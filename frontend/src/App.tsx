import { useState } from 'react';
import { useTimeSeries } from './data/useTimeSeries';
import { HeaderInfo } from './table/HeaderInfo';
import { ValuesTable } from './table/ValuesTable';

function toLocalDateTimeString(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function getDefaultStart(): string {
  const d = new Date();
  d.setMonth(0, 1);
  d.setHours(0, 0, 0, 0);
  return toLocalDateTimeString(d);
}

function getDefaultEnd(): string {
  return toLocalDateTimeString(new Date());
}

export default function App() {
  const [tsId, setTsId] = useState('15201');
  const [start, setStart] = useState('2022-01-01T00:00');
  const [end, setEnd] = useState('2025-01-01T00:00');
  const { header, rows, loading, error, load } = useTimeSeries();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(tsId, 10);
    if (isNaN(id) || id <= 0) return;
    load(id, start, end);
  };

  return (
    <div className="app">
      <h1>Zeitreihen-Viewer</h1>

      <form onSubmit={handleSubmit} className="form">
        <div className="form-row">
          <label>
            TS-ID
            <input
              type="number"
              value={tsId}
              onChange={(e) => setTsId(e.target.value)}
              placeholder="z.B. 12345"
              min={1}
              required
            />
          </label>
          <label>
            Start
            <input
              type="datetime-local"
              value={start}
              onChange={(e) => setStart(e.target.value)}
              required
            />
          </label>
          <label>
            Ende
            <input
              type="datetime-local"
              value={end}
              onChange={(e) => setEnd(e.target.value)}
              required
            />
          </label>
          <button type="submit" disabled={loading}>
            {loading ? 'Lade...' : 'Laden'}
          </button>
        </div>
      </form>

      {error && <div className="error">{error}</div>}

      {header && <HeaderInfo header={header} rowCount={rows.length} />}

      {header && rows.length === 0 && !loading && !error && (
        <div className="info">Keine Werte im gewählten Zeitraum.</div>
      )}

      {rows.length > 0 && header && (
        <ValuesTable rows={rows} unit={header.unit} dimension={header.dimension} />
      )}
    </div>
  );
}
