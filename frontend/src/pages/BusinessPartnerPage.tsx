import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import { deleteBusinessPartner } from '../api/client';

const columnOverrides = {
  id: { hidden: true },
  systemRank: {
    header: 'Systemfirma',
    format: (value: unknown) => {
      if (value == null) return '';
      const rank = value as number;
      return rank === 1 ? 'Fuehrend' : `Tochter (${rank})`;
    },
  },
};

export function BusinessPartnerPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteBusinessPartner(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  return (
    <OverviewPage
      pageKey="business-partners"
      apiUrl="/api/business-partners"
      tabId={tabId}
      onNew={() => openTab('business-partner-detail', { mode: 'new' })}
      newLabel="Neuer Geschaeftspartner"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Geschaeftspartner vorhanden"
      onRowDoubleClick={(row) => openTab('business-partner-detail', { mode: 'edit', entityId: row.id })}
      onDelete={handleDelete}
    />
  );
}
