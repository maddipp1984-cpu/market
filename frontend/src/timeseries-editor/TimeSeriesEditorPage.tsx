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

  const handleReload = (e: React.FormEvent) => {
    e.preventDefault();
    if (!initialTsIds || initialTsIds.length === 0) return;
    setActiveTs(prev => ({ tsIds: initialTsIds, start, end, seq: prev.seq + 1 }));
  };

  return (
    <DataPage title="Zeitreihen">
      <Card>
        <form onSubmit={handleReload}>
          <div style={{ display: 'flex', gap: 'var(--space-lg)', alignItems: 'flex-end', flexWrap: 'wrap' }}>
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
