import { useState } from 'react';
import { TimeSeriesEditor } from './timeseries-editor/TimeSeriesEditor';

export default function App() {
  const [tsIds, setTsIds] = useState('15201');
  const [start, setStart] = useState('2022-01-01T00:00');
  const [end, setEnd] = useState('2025-01-01T00:00');
  const [activeTs, setActiveTs] = useState({ tsIds: [] as number[], start: '', end: '', seq: 0 });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const ids = tsIds.split(',')
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !isNaN(n) && n > 0);
    if (ids.length === 0) return;
    setActiveTs(prev => ({ tsIds: ids, start, end, seq: prev.seq + 1 }));
  };

  return (
    <div className="app">
      <h1>Zeitreihen-Viewer</h1>

      <form onSubmit={handleSubmit} className="form">
        <div className="form-row">
          <label>
            TS-IDs
            <input
              type="text"
              value={tsIds}
              onChange={(e) => setTsIds(e.target.value)}
              placeholder="z.B. 15201, 15202"
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
          <button type="submit">Laden</button>
        </div>
      </form>

      {activeTs.tsIds.length > 0 && (
        <TimeSeriesEditor
          key={activeTs.seq}
          tsIds={activeTs.tsIds}
          start={activeTs.start}
          end={activeTs.end}
        />
      )}
    </div>
  );
}
