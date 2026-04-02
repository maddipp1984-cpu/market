import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import { deleteSeriesType } from '../api/client';

const columnOverrides = { id: { hidden: true } };

export function ReihenartenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteSeriesType(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  return (
    <OverviewPage
      pageKey="series-types"
      apiUrl="/api/series-types"
      tabId={tabId}
      onNew={() => openTab('reihenart-detail', { mode: 'new' })}
      newLabel="Neue Reihenart"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Reihenarten vorhanden"
      onRowDoubleClick={(row) => openTab('reihenart-detail', { mode: 'edit', entityId: row.id })}
      onDelete={handleDelete}
    />
  );
}
