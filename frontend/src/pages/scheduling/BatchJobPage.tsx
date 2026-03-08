import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import { useTabContext } from '../../shell/TabContext';

const columnOverrides = { id: { hidden: true }, jobKey: { hidden: true } };

export function BatchJobPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  return (
    <OverviewPage
      pageKey="batch-jobs"
      apiUrl="/api/batch-jobs"
      tabId={tabId}
      columnOverrides={columnOverrides}
      emptyMessage="Keine Batch-Jobs registriert"
      onRowDoubleClick={(row) => openTab('batch-job-detail', { mode: 'edit', entityId: row.id })}
    />
  );
}
