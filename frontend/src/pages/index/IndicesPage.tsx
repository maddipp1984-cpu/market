import { useState, useCallback } from 'react';
import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import type { ContextAction } from '../../shared/overview-page/VirtualTable';
import { DateRangeDialog } from '../../shared/DateRangeDialog';
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

