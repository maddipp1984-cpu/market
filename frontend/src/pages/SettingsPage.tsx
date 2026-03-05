import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';

export function SettingsPage({ tabId: _tabId }: { tabId: string }) {
  return (
    <DataPage title="Einstellungen" subtitle="Systemkonfiguration">
      <Card>
        <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
          Einstellungen werden in einer zukuenftigen Version verfuegbar sein.
        </div>
      </Card>
    </DataPage>
  );
}
