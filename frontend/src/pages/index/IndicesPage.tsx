import { useState, useCallback } from 'react';
import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import type { ContextAction } from '../../shared/overview-page/VirtualTable';
import { useTabContext } from '../../shell/TabContext';
import { deleteIndex } from '../../api/client';

const columnOverrides = {
  id: { hidden: true },
  timeDim: {
    header: 'Zeitdimension',
    format: (v: unknown) => {
      const labels: Record<number, string> = { 1: '15 Min', 2: '1 Stunde', 3: 'Tag', 4: 'Monat', 5: 'Jahr' };
      return labels[v as number] ?? String(v);
    },
  },
};

export function IndicesPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
  const [dialogRow, setDialogRow] = useState<Record<string, unknown> | null>(null);

  const handleDelete = useCallback(async (rows: Record<string, unknown>[]) => {
    const results = await Promise.allSettled(
      rows.map(row => deleteIndex(row.id as number))
    );
    const failed = results.filter(r => r.status === 'rejected').length;
    if (failed > 0) {
      const ok = results.length - failed;
      throw new Error(`${ok} von ${results.length} geloescht, ${failed} fehlgeschlagen`);
    }
  }, []);

  const extraContextActions: ContextAction[] = [
    {
      label: 'Zeitreihe anzeigen',
      onClick: (rows) => {
        const row = rows[0];
        openTab('index-detail', {
          mode: 'edit',
          entityId: row.id,
          editorMode: 'view',
        });
      },
    },
    {
      label: 'Zeitreihe bearbeiten',
      onClick: (rows) => {
        setDialogRow(rows[0]);
      },
    },
  ];

  return (
    <>
      <OverviewPage
        pageKey="indices"
        apiUrl="/api/indices"
        tabId={tabId}
        onNew={() => openTab('index-detail', { mode: 'new' })}
        newLabel="Neuer Index"
        columnOverrides={columnOverrides}
        emptyMessage="Keine Indices vorhanden"
        onRowDoubleClick={(row) => openTab('index-detail', { mode: 'edit', entityId: row.id })}
        onDelete={handleDelete}
        extraContextActions={extraContextActions}
      />
      {dialogRow && (
        <DateRangeDialog
          onConfirm={(from, to) => {
            openTab('index-detail', {
              mode: 'edit',
              entityId: dialogRow.id,
              editorMode: 'edit',
              dateFrom: from,
              dateTo: to,
            });
            setDialogRow(null);
          }}
          onCancel={() => setDialogRow(null)}
        />
      )}
    </>
  );
}

function DateRangeDialog({ onConfirm, onCancel }: {
  onConfirm: (from: string, to: string) => void;
  onCancel: () => void;
}) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  return (
    <div style={{
      position: 'fixed', inset: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      backgroundColor: 'rgba(0,0,0,0.4)', zIndex: 9999,
    }} onClick={onCancel}>
      <div style={{
        background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-lg)', minWidth: '320px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
      }} onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: '0 0 var(--space-md)', color: 'var(--color-text-primary)' }}>
          Zeitraum waehlen
        </h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <label style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
            Von
            <input type="date" value={from} onChange={e => setFrom(e.target.value)}
              style={{ display: 'block', width: '100%', marginTop: '4px' }} />
          </label>
          <label style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
            Bis
            <input type="date" value={to} onChange={e => setTo(e.target.value)}
              style={{ display: 'block', width: '100%', marginTop: '4px' }} />
          </label>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-sm)', marginTop: 'var(--space-md)' }}>
          <button onClick={onCancel} style={{ padding: '6px 16px' }}>Abbrechen</button>
          <button onClick={() => { if (from && to) onConfirm(from, to); }}
            disabled={!from || !to}
            style={{ padding: '6px 16px', background: 'var(--color-accent)', color: '#fff', border: 'none', borderRadius: 'var(--radius-sm)' }}>
            Oeffnen
          </button>
        </div>
      </div>
    </div>
  );
}
