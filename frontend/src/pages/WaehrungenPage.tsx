import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import { deleteCurrency } from '../api/client';

const columnOverrides = { id: { hidden: true } };

export function WaehrungenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteCurrency(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  return (
    <OverviewPage
      pageKey="currencies"
      apiUrl="/api/currencies"
      tabId={tabId}
      onNew={() => openTab('currency-detail', { mode: 'new' })}
      newLabel="Neue Waehrung"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Waehrungen vorhanden"
      onRowDoubleClick={(row) => openTab('currency-detail', { mode: 'edit', entityId: row.id })}
      onDelete={handleDelete}
    />
  );
}
