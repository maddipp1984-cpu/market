import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';

const columnOverrides = { id: { hidden: true } };

export function BusinessPartnerPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
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
    />
  );
}
