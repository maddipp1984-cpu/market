import { useState, useEffect, useCallback, useMemo } from 'react';
import type { SortingState } from '@tanstack/react-table';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { VirtualTable, type ColumnOverride } from '../shared/overview-page/VirtualTable';
import { Card } from '../shared/Card';
import { useTabContext } from '../shell/TabContext';
import { useMessageBar } from '../shell/MessageBarContext';
import { fetchObjects, type TimingInfo } from '../api/client';
import type { ObjectResponse } from '../api/types';

const columnOverrides: Record<string, ColumnOverride> = {
  updatedAt: { hidden: true },
};

export function ObjectsPage({ tabId: _tabId }: { tabId: string }) {
  const [objects, setObjects] = useState<ObjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [timing, setTiming] = useState<TimingInfo | null>(null);
  const [sorting, setSorting] = useState<SortingState>([]);
  const { openTab } = useTabContext();
  const { showMessage } = useMessageBar();

  const loadData = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchObjects(signal);
      setObjects(result.data);
      setTiming(result.timing);
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const ac = new AbortController();
    loadData(ac.signal);
    return () => ac.abort();
  }, [loadData]);

  const handleRefresh = useCallback(() => {
    loadData();
    showMessage('Objekte aktualisiert', 'info');
  }, [loadData, showMessage]);

  const handleNew = useCallback(() => {
    openTab('objekt-neu');
  }, [openTab]);

  const footer = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${objects.length} Objekt${objects.length !== 1 ? 'e' : ''}`);
    if (timing) {
      parts.push(`${timing.totalMs} ms`);
      if (timing.serverMs !== null) parts.push(`(Server: ${timing.serverMs} ms)`);
    }
    return <span>{parts.join(' | ')}</span>;
  }, [objects.length, timing]);

  return (
    <OverviewPage
      loading={loading}
      error={error}
      onRefresh={handleRefresh}
      onNew={handleNew}
      newLabel="Neues Objekt"
      footer={footer}
    >
      <Card>
        <VirtualTable
          data={objects}
          columnOverrides={columnOverrides}
          sorting={sorting}
          onSortingChange={setSorting}
          emptyMessage="Keine Objekte vorhanden"
        />
      </Card>
    </OverviewPage>
  );
}
