import { useCallback } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import type { ContextAction } from '../shared/overview-page/VirtualTable';

const columnOverrides = { id: { hidden: true } };

const iconEditor = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

export function ZeitreihenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const openInEditor = useCallback((rows: Record<string, unknown>[]) => {
    const tsIds = rows.map(r => r.id as number);
    openTab('timeseries-editor', { tsIds });
  }, [openTab]);

  const extraActions: ContextAction[] = [
    {
      label: 'Im Editor oeffnen',
      icon: iconEditor,
      onClick: openInEditor,
      multi: true,
    },
  ];

  return (
    <OverviewPage
      pageKey="zeitreihen"
      apiUrl="/api/timeseries-overview"
      tabId={tabId}
      columnOverrides={columnOverrides}
      emptyMessage="Keine Zeitreihen vorhanden"
      onRowDoubleClick={(row) => openTab('timeseries-editor', { tsIds: [row.id as number] })}
      extraContextActions={extraActions}
    />
  );
}
