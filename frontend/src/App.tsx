import { useState } from 'react';
import { TimeSeriesEditor } from './components/TimeSeriesEditor';

export default function App() {
  const [tsId, setTsId] = useState('15201');
  const [start, setStart] = useState('2022-01-01T00:00');
  const [end, setEnd] = useState('2025-01-01T00:00');
  const [activeTs, setActiveTs] = useState({ tsId: 0, start: '', end: '', seq: 0 });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(tsId, 10);
    if (isNaN(id) || id <= 0) return;
    setActiveTs(prev => ({ tsId: id, start, end, seq: prev.seq + 1 }));
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
          <button type="submit">Laden</button>
        </div>
      </form>

      {activeTs.tsId > 0 && (
        <TimeSeriesEditor
          key={activeTs.seq}
          tsId={activeTs.tsId}
          start={activeTs.start}
          end={activeTs.end}
        />
      )}
    </div>
  );
}
