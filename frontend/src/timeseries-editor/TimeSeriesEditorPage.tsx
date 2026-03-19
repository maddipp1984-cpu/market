import { useState, useEffect, useRef } from 'react';
import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';
import { Button } from '../shared/Button';
import { FormField } from '../shared/FormField';
import { TimeSeriesEditor } from './TimeSeriesEditor';
import { useTabContext } from '../shell/TabContext';

export function TimeSeriesEditorPage({ tabId }: { tabId: string }) {
  const { updateTabLabel, getTabParams } = useTabContext();
  const params = getTabParams(tabId);
  const initialTsIds = params?.tsIds as number[] | undefined;
  const aggregateMode = params?.aggregateMode as string | undefined;

  const [tsIds, setTsIds] = useState(initialTsIds ? initialTsIds.join(', ') : '');
  const [start, setStart] = useState('2022-01-01T00:00');
  const [end, setEnd] = useState('2025-01-01T00:00');
  const [activeTs, setActiveTs] = useState({ tsIds: [] as number[], start: '', end: '', seq: 0 });
  const didAutoLoad = useRef(false);

  useEffect(() => {
    if (!didAutoLoad.current && initialTsIds && initialTsIds.length > 0) {
      didAutoLoad.current = true;
      setActiveTs(prev => ({ tsIds: initialTsIds, start, end, seq: prev.seq + 1 }));
      updateTabLabel(tabId, aggregateMode === 'sum'
        ? 'SUM(' + initialTsIds.length + ' ZR)'
        : 'ZR ' + initialTsIds.join(', '));
    }
  }, []);

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
          aggregateMode={aggregateMode}
        />
      )}
    </DataPage>
  );
}
