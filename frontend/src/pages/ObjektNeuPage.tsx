import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';

export function ObjektNeuPage({ tabId: _tabId }: { tabId: string }) {
  return (
    <DataPage title="Neues Objekt">
      <Card>
        <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
          Objekt-Erstellung wird in einer zukuenftigen Version verfuegbar sein.
        </div>
      </Card>
    </DataPage>
  );
}
