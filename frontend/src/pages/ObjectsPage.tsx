import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';

export function ObjectsPage({ tabId: _tabId }: { tabId: string }) {
  return (
    <DataPage title="Objekte" subtitle="Stammdatenverwaltung">
      <Card>
        <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
          Objekt-Verwaltung wird in einer zukuenftigen Version verfuegbar sein.
        </div>
      </Card>
    </DataPage>
  );
}
