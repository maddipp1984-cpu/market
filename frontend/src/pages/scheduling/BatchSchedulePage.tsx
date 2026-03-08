import { useCallback } from 'react';
import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import { useTabContext } from '../../shell/TabContext';
import { deleteSchedule } from '../../api/client';
import type { ContextAction } from '../../shared/overview-page/VirtualTable';

const columnOverrides = { id: { hidden: true }, jobKey: { hidden: true } };

const iconPlay = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polygon points="5 3 19 12 5 21 5 3" />
  </svg>
);

export function BatchSchedulePage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const handleTrigger = useCallback((rows: Record<string, unknown>[]) => {
    const row = rows[0];
    openTab('batch-schedule-detail', { mode: 'trigger', entityId: row.id });
  }, [openTab]);

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    for (const row of rows) {
      await deleteSchedule(row.id as number);
    }
  }, []);

  const extraActions: ContextAction[] = [
    {
      label: 'Start einmalig',
      icon: iconPlay,
      onClick: handleTrigger,
    },
  ];

  return (
    <OverviewPage
      pageKey="batch-schedules"
      apiUrl="/api/batch-schedules"
      tabId={tabId}
      columnOverrides={columnOverrides}
      emptyMessage="Keine Batchplanungen vorhanden"
      onRowDoubleClick={(row) => openTab('batch-schedule-detail', { mode: 'edit', entityId: row.id })}
      onNew={() => openTab('batch-schedule-detail', { mode: 'new' })}
      onDelete={handleDelete}
      extraContextActions={extraActions}
    />
  );
}
