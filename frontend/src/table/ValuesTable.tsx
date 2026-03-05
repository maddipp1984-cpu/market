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
  edits: Map<number, number>;
  unit: string;
  dimension: Dimension;
  decimals: number;
  onEdit: (index: number, value: number) => void;
}

function formatValue(v: number, decimals: number): string {
  return v.toLocaleString('de-DE', { minimumFractionDigits: 0, maximumFractionDigits: decimals });
}

function EditableCell({ row, edits, decimals, onEdit }: {
  row: TimeSeriesRow;
  edits: Map<number, number>;
  decimals: number;
  onEdit: (index: number, value: number) => void;
}) {
  const [focused, setFocused] = useState(false);
  const [localValue, setLocalValue] = useState('');
  const edited = edits.get(row.index);
  const effectiveValue = edited ?? row.value;
  const isEdited = edits.has(row.index);
  const isEmpty = effectiveValue == null || isNaN(effectiveValue);

  const displayValue = focused
    ? localValue
    : (isEmpty ? '' : formatValue(effectiveValue, decimals));

  return (
    <input
      className={`value-input${isEdited ? ' edited' : ''}`}
      type="text"
      data-row-index={row.index}
      value={displayValue}
      onFocus={() => {
        setLocalValue(isEmpty ? '' : effectiveValue.toLocaleString('de-DE', { useGrouping: false, maximumFractionDigits: 20 }));
        setFocused(true);
      }}
      onBlur={() => {
        setFocused(false);
        const raw = localValue.replace(',', '.');
        if (raw === '' || raw === '-') {
          onEdit(row.index, NaN);
          return;
        }
        const num = parseFloat(raw);
        if (!isNaN(num)) onEdit(row.index, num);
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
      }}
      onChange={(e) => setLocalValue(e.target.value)}
    />
  );
}

export function ValuesTable({ rows, edits, unit, dimension, decimals, onEdit }: ValuesTableProps) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [copied, setCopied] = useState(false);
  const [pastedCount, setPastedCount] = useState(0);
  const parentRef = useRef<HTMLDivElement>(null);

  const getEffectiveValue = useCallback(
    (row: TimeSeriesRow) => edits.get(row.index) ?? row.value,
    [edits]
  );

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    const active = document.activeElement as HTMLElement | null;
    const startIndex = active?.dataset.rowIndex != null ? parseInt(active.dataset.rowIndex, 10) : null;
    if (startIndex == null) return;

    e.preventDefault();
    const text = e.clipboardData.getData('text/plain');
    const lines = text.split(/\r?\n/).filter(l => l.trim() !== '');

    const startPos = rows.findIndex(r => r.index === startIndex);
    if (startPos === -1) return;

    let count = 0;
    for (let i = 0; i < lines.length; i++) {
      const rowPos = startPos + i;
      if (rowPos >= rows.length) break;
      const raw = lines[i].trim().replace(/\./g, '').replace(',', '.');
      const num = parseFloat(raw);
      if (!isNaN(num)) {
        onEdit(rows[rowPos].index, num);
        count++;
      }
    }
    if (count > 0) {
      setPastedCount(count);
      setTimeout(() => setPastedCount(0), 2000);
    }
  }, [rows, onEdit]);

  const handleCopy = useCallback((e: React.ClipboardEvent) => {
    e.preventDefault();
    const tsv = rows
      .map(r => {
        const ts = formatTimestamp(r.timestampMs, dimension);
        const val = getEffectiveValue(r);
        return `${ts}\t${(val == null || isNaN(val)) ? '' : formatValue(val, decimals)}`;
      })
      .join('\n');
    e.clipboardData.setData('text/plain', `Datum\tWert\n${tsv}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [rows, dimension, decimals, getEffectiveValue]);

  const stats = useMemo(() => {
    const valid: number[] = [];
    for (const r of rows) {
      const v = getEffectiveValue(r);
      if (v != null && !isNaN(v)) valid.push(v);
    }
    if (valid.length === 0) return null;
    let min = Infinity, max = -Infinity, sum = 0;
    for (const v of valid) {
      if (v < min) min = v;
      if (v > max) max = v;
      sum += v;
    }
    return { min, max, avg: sum / valid.length, count: valid.length };
  }, [rows, getEffectiveValue]);

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
        cell: ({ row: tableRow }) => (
          <EditableCell row={tableRow.original} edits={edits} decimals={decimals} onEdit={onEdit} />
        ),
      },
    ],
    [unit, dimension, edits, onEdit]
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
    <div className="grid-table" tabIndex={0} onCopy={handleCopy} onPaste={handlePaste} onKeyDown={(e) => {
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
          {pastedCount > 0 && <div className="copy-hint">{pastedCount} Werte eingefügt!</div>}
          <div className="stat">
            <span className="stat-label">Min</span>
            <span className="stat-value">{formatValue(stats.min, decimals)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Max</span>
            <span className="stat-value">{formatValue(stats.max, decimals)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Avg</span>
            <span className="stat-value">{formatValue(stats.avg, decimals)}</span>
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
