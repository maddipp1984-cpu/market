import { DataPage } from '../shared/DataPage';
import { Card } from '../shared/Card';

const kpis = [
  { label: 'Zeitreihen gesamt', value: '--' },
  { label: 'Letzte Aktualisierung', value: '--' },
  { label: 'Objekte', value: '--' },
  { label: 'Dimensionen', value: '5' },
];

export function DashboardPage({ tabId: _tabId }: { tabId: string }) {
  return (
    <DataPage title="Dashboard" subtitle="System-Uebersicht">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 'var(--space-lg)' }}>
        {kpis.map(kpi => (
          <Card key={kpi.label}>
            <div style={{ padding: 'var(--space-sm)' }}>
              <div style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 'var(--space-sm)' }}>
                {kpi.label}
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--color-text-heading)' }}>
                {kpi.value}
              </div>
            </div>
          </Card>
        ))}
      </div>
    </DataPage>
  );
}
