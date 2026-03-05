import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';

export function WaehrungenPage({ tabId: _tabId }: { tabId: string }) {
  return (
    <DataPage title="Waehrungen" subtitle="Stammdatenverwaltung">
      <Card>
        <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
          Waehrungen-Verwaltung wird in einer zukuenftigen Version verfuegbar sein.
        </div>
      </Card>
    </DataPage>
  );
}
