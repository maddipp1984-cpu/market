import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import { deleteCommodityGroup } from '../api/client';

const columnOverrides = { id: { hidden: true } };

export function WarengruppenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteCommodityGroup(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  return (
    <OverviewPage
      pageKey="commodity-groups"
      apiUrl="/api/commodity-groups"
      tabId={tabId}
      onNew={() => openTab('warengruppe-detail', { mode: 'new' })}
      newLabel="Neue Warengruppe"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Warengruppen vorhanden"
      onRowDoubleClick={(row) => openTab('warengruppe-detail', { mode: 'edit', entityId: row.id })}
      onDelete={handleDelete}
    />
  );
}
