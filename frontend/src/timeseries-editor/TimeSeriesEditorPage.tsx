import { useState } from 'react';
import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';
import { Button } from '../shared/Button';
import { FormField } from '../shared/FormField';
import { TimeSeriesEditor } from './TimeSeriesEditor';
import { useTabContext } from '../shell/TabContext';

export function TimeSeriesEditorPage({ tabId }: { tabId: string }) {
  const { updateTabLabel } = useTabContext();
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
    updateTabLabel(tabId, 'ZR ' + ids.join(', '));
  };

  return (
    <DataPage title="Zeitreihen">
      <Card>
        <form onSubmit={handleSubmit}>
          <div style={{ display: 'flex', gap: 'var(--space-lg)', alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <FormField label="TS-IDs">
              <input
                type="text"
                value={tsIds}
                onChange={(e) => setTsIds(e.target.value)}
                placeholder="z.B. 15201, 15202"
              />
            </FormField>
            <FormField label="Start">
              <input
                type="datetime-local"
                value={start}
                onChange={(e) => setStart(e.target.value)}
                required
              />
            </FormField>
            <FormField label="Ende">
              <input
                type="datetime-local"
                value={end}
                onChange={(e) => setEnd(e.target.value)}
                required
              />
            </FormField>
            <Button type="submit">Laden</Button>
          </div>
        </form>
      </Card>

      {activeTs.tsIds.length > 0 && (
        <TimeSeriesEditor
          key={activeTs.seq}
          tsIds={activeTs.tsIds}
          start={activeTs.start}
          end={activeTs.end}
        />
      )}
    </DataPage>
  );
}
