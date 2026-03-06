import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { Card } from '../shared/Card';
import { useTabContext } from '../shell/TabContext';
import { useMessageBar } from '../shell/MessageBarContext';
import { fetchObjects, type TimingInfo } from '../api/client';
import type { ObjectResponse } from '../api/types';
import './ObjectsPage.css';

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const columns: ColumnDef<ObjectResponse>[] = [
  {
    accessorKey: 'objectId',
    header: 'ID',
    size: 70,
  },
  {
    accessorKey: 'objectKey',
    header: 'Key',
    size: 200,
  },
  {
    accessorKey: 'type',
    header: 'Typ',
    size: 120,
  },
  {
    accessorKey: 'description',
    header: 'Beschreibung',
    meta: { flex: true },
    cell: ({ getValue }) => getValue() ?? '-',
  },
  {
    accessorKey: 'createdAt',
    header: 'Erstellt',
    size: 150,
    cell: ({ getValue }) => formatDateTime(getValue() as string),
  },
];

export function ObjectsPage({ tabId: _tabId }: { tabId: string }) {
  const [objects, setObjects] = useState<ObjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [timing, setTiming] = useState<TimingInfo | null>(null);
  const [sorting, setSorting] = useState<SortingState>([]);
  const { openTab } = useTabContext();
  const { showMessage } = useMessageBar();

  const loadData = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchObjects(signal);
      setObjects(result.data);
      setTiming(result.timing);
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const ac = new AbortController();
    loadData(ac.signal);
    return () => ac.abort();
  }, [loadData]);

  const handleRefresh = useCallback(() => {
    loadData();
    showMessage('Objekte aktualisiert', 'info');
  }, [loadData, showMessage]);

  const handleNew = useCallback(() => {
    openTab('objekt-neu');
  }, [openTab]);

  const table = useReactTable({
    data: objects,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const footer = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${objects.length} Objekt${objects.length !== 1 ? 'e' : ''}`);
    if (timing) {
      parts.push(`${timing.totalMs} ms`);
      if (timing.serverMs !== null) parts.push(`(Server: ${timing.serverMs} ms)`);
    }
    return <span>{parts.join(' | ')}</span>;
  }, [objects.length, timing]);

  return (
    <OverviewPage
      loading={loading}
      error={error}
      onRefresh={handleRefresh}
      onNew={handleNew}
      newLabel="Neues Objekt"
      footer={footer}
    >
      <Card>
        <div className="objects-table-wrap">
          <table className="objects-table">
            <thead>
              {table.getHeaderGroups().map(hg => (
                <tr key={hg.id}>
                  {hg.headers.map(header => (
                    <th
                      key={header.id}
                      style={{ width: header.column.columnDef.meta?.flex ? undefined : header.getSize() }}
                      className={header.column.columnDef.meta?.flex ? 'flex-col' : undefined}
                      onClick={header.column.getToggleSortingHandler()}
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {{ asc: ' ▲', desc: ' ▼' }[header.column.getIsSorted() as string] ?? ''}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody>
              {table.getRowModel().rows.length === 0 ? (
                <tr>
                  <td colSpan={columns.length} className="objects-empty">
                    Keine Objekte vorhanden
                  </td>
                </tr>
              ) : (
                table.getRowModel().rows.map(row => (
                  <tr key={row.id}>
                    {row.getVisibleCells().map(cell => (
                      <td key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </OverviewPage>
  );
}
