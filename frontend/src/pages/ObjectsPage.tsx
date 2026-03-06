import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';

const columnOverrides = { updatedAt: { hidden: true } };

export function ObjectsPage({ tabId: _tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
  return (
    <OverviewPage
      pageKey="objects"
      apiUrl="/api/objects"
      onNew={() => openTab('objekt-neu')}
      newLabel="Neues Objekt"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Objekte vorhanden"
    />
  );
}
