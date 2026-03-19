import { useCallback, useMemo } from 'react';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';
import type { ContextAction } from '../shared/overview-page/VirtualTable';

const columnOverrides = { id: { hidden: true } };

function getDateRange(rows: Record<string, unknown>[]): { start: string; end: string } {
  let minDate = '';
  let maxDate = '';
  for (const r of rows) {
    const first = r.firstDate as string | null;
    const last = r.lastDate as string | null;
    if (first && (!minDate || first < minDate)) minDate = first;
    if (last && (!maxDate || last > maxDate)) maxDate = last;
  }
  return {
    start: minDate ? minDate + 'T00:00' : '2020-01-01T00:00',
    end: maxDate ? maxDate + 'T00:00' : '2025-01-01T00:00',
  };
}

const iconEditor = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

const iconSum = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 6H6l6 6-6 6h12" />
  </svg>
);

export function ZeitreihenPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const openInEditor = useCallback((rows: Record<string, unknown>[]) => {
    const tsIds = rows.map(r => r.id as number);
    const { start, end } = getDateRange(rows);
    openTab('timeseries-editor', { tsIds, start, end });
  }, [openTab]);

  const openSumView = useCallback((rows: Record<string, unknown>[]) => {
    const tsIds = rows.map(r => r.id as number);
    const { start, end } = getDateRange(rows);
    openTab('timeseries-editor', { tsIds, aggregateMode: 'sum', start, end });
  }, [openTab]);

  const extraActions = useMemo((): ContextAction[] => [
    {
      label: 'Im Editor oeffnen',
      icon: iconEditor,
      onClick: openInEditor,
      multi: true,
    },
    {
      label: 'Summieren',
      icon: iconSum,
      onClick: openSumView,
      multi: true,
    },
  ], [openInEditor, openSumView]);

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
