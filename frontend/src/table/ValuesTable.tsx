import { useRef, useMemo, useState, useCallback } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import { useVirtualizer } from '@tanstack/react-virtual';
import { formatTimestamp } from '../data/timestampCalculator';
import type { Dimension, TimeSeriesRow } from '../api/types';

interface ValuesTableProps {
  rows: TimeSeriesRow[];
  unit: string;
  dimension: Dimension;
}

function formatValue(v: number): string {
  return v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}

export function ValuesTable({ rows, unit, dimension }: ValuesTableProps) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [copied, setCopied] = useState(false);
  const parentRef = useRef<HTMLDivElement>(null);

  const handleCopy = useCallback((e: React.ClipboardEvent) => {
    e.preventDefault();
    const tsv = rows
      .map(r => {
        const ts = formatTimestamp(r.timestampMs, dimension);
        const val = (r.value == null || isNaN(r.value)) ? '' : formatValue(r.value);
        return `${ts}\t${val}`;
      })
      .join('\n');
    e.clipboardData.setData('text/plain', `Datum\tWert\n${tsv}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [rows, dimension]);

  const stats = useMemo(() => {
    const valid = rows.filter(r => r.value != null && !isNaN(r.value));
    if (valid.length === 0) return null;
    let min = Infinity, max = -Infinity, sum = 0;
    for (const r of valid) {
      if (r.value < min) min = r.value;
      if (r.value > max) max = r.value;
      sum += r.value;
    }
    return { min, max, avg: sum / valid.length, count: valid.length };
  }, [rows]);

  const columns = useMemo<ColumnDef<TimeSeriesRow>[]>(
    () => [
      {
        accessorKey: 'index',
        header: '#',
        size: 80,
        enableSorting: false,
      },
      {
        accessorKey: 'timestampMs',
        header: 'Datum',
        size: 170,
        cell: ({ getValue }) => formatTimestamp(getValue() as number, dimension),
      },
      {
        accessorKey: 'value',
        header: `Wert (${unit})`,
        meta: { flex: true },
        cell: ({ getValue }) => {
          const v = getValue() as number;
          if (v == null || isNaN(v)) return '';
          return formatValue(v);
        },
      },
    ],
    [unit, dimension]
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const { rows: tableRows } = table.getRowModel();

  const virtualizer = useVirtualizer({
    count: tableRows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 35,
    overscan: 20,
  });

  return (
    <div className="grid-table" tabIndex={0} onCopy={handleCopy} onKeyDown={(e) => {
      if (e.ctrlKey && e.key === 'a') {
        e.preventDefault();
        const sel = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(e.currentTarget);
        sel?.removeAllRanges();
        sel?.addRange(range);
      }
    }}>
      <div className="grid-header">
        {table.getHeaderGroups().map((headerGroup) =>
          headerGroup.headers.map((header) => (
            <div
              key={header.id}
              className={`grid-cell header-cell${header.column.getCanSort() ? ' sortable' : ''}`}
              style={header.column.columnDef.meta?.flex ? { flex: 1 } : { width: header.getSize() }}
              onClick={header.column.getToggleSortingHandler()}
            >
              {flexRender(header.column.columnDef.header, header.getContext())}
              {header.column.getIsSorted() === 'asc' && ' ▲'}
              {header.column.getIsSorted() === 'desc' && ' ▼'}
            </div>
          ))
        )}
      </div>
      <div ref={parentRef} className="grid-body">
        <div style={{ height: `${virtualizer.getTotalSize()}px`, position: 'relative' }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const row = tableRows[virtualRow.index];
            return (
              <div
                key={row.id}
                className={`grid-row${virtualRow.index % 2 === 0 ? '' : ' odd'}`}
                style={{
                  position: 'absolute',
                  top: 0,
                  transform: `translateY(${virtualRow.start}px)`,
                  height: `${virtualRow.size}px`,
                  width: '100%',
                }}
              >
                {row.getVisibleCells().map((cell) => (
                  <div
                    key={cell.id}
                    className="grid-cell"
                    style={cell.column.columnDef.meta?.flex ? { flex: 1 } : { width: cell.column.getSize() }}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
      {stats && (
        <div className="grid-footer">
          {copied && <div className="copy-hint">Kopiert!</div>}
          <div className="stat">
            <span className="stat-label">Min</span>
            <span className="stat-value">{formatValue(stats.min)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Max</span>
            <span className="stat-value">{formatValue(stats.max)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Avg</span>
            <span className="stat-value">{formatValue(stats.avg)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Werte</span>
            <span className="stat-value">{stats.count.toLocaleString('de-DE')}</span>
          </div>
        </div>
      )}
    </div>
  );
}
