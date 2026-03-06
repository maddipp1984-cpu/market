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
import type { Dimension, MultiSeriesRow, TimeSeriesHeaderResponse } from '../../api/types';
import './ValuesTable.css';

interface ValuesTableProps {
  rows: MultiSeriesRow[];
  headers: TimeSeriesHeaderResponse[];
  edits: Map<string, number>;
  dimension: Dimension;
  decimals: number;
  readOnly?: boolean;
  onEdit: (seriesIdx: number, rowIndex: number, value: number) => void;
}

function formatValue(v: number, decimals: number): string {
  return v.toLocaleString('de-DE', { minimumFractionDigits: 0, maximumFractionDigits: decimals });
}

function EditableCell({ seriesIdx, row, edits, decimals, onEdit }: {
  seriesIdx: number;
  row: MultiSeriesRow;
  edits: Map<string, number>;
  decimals: number;
  onEdit: (seriesIdx: number, rowIndex: number, value: number) => void;
}) {
  const [focused, setFocused] = useState(false);
  const [localValue, setLocalValue] = useState('');
  const editKey = `${seriesIdx}:${row.index}`;
  const edited = edits.get(editKey);
  const effectiveValue = edited ?? row.values[seriesIdx];
  const isEdited = edits.has(editKey);
  const isEmpty = effectiveValue == null || isNaN(effectiveValue);

  const displayValue = focused
    ? localValue
    : (isEmpty ? '' : formatValue(effectiveValue, decimals));

  return (
    <input
      className={`value-input${isEdited ? ' edited' : ''}`}
      type="text"
      data-row-index={row.index}
      data-series-idx={seriesIdx}
      value={displayValue}
      onFocus={() => {
        setLocalValue(isEmpty ? '' : effectiveValue.toLocaleString('de-DE', { useGrouping: false, maximumFractionDigits: 20 }));
        setFocused(true);
      }}
      onBlur={() => {
        setFocused(false);
        const raw = localValue.replace(',', '.');
        if (raw === '' || raw === '-') {
          if (!isEmpty) onEdit(seriesIdx, row.index, NaN);
          return;
        }
        const num = parseFloat(raw);
        if (!isNaN(num) && num !== effectiveValue) onEdit(seriesIdx, row.index, num);
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
      }}
      onChange={(e) => setLocalValue(e.target.value)}
    />
  );
}

export function ValuesTable({ rows, headers, edits, dimension, decimals, readOnly, onEdit }: ValuesTableProps) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [copied, setCopied] = useState(false);
  const [pastedCount, setPastedCount] = useState(0);
  const parentRef = useRef<HTMLDivElement>(null);
  const editsRef = useRef(edits);
  editsRef.current = edits;

  const getEffectiveValue = useCallback(
    (row: MultiSeriesRow, seriesIdx: number) => edits.get(`${seriesIdx}:${row.index}`) ?? row.values[seriesIdx],
    [edits]
  );

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    if (readOnly) return;
    const active = document.activeElement as HTMLElement | null;
    const startIndex = active?.dataset.rowIndex != null ? parseInt(active.dataset.rowIndex, 10) : null;
    const seriesIdx = active?.dataset.seriesIdx != null ? parseInt(active.dataset.seriesIdx, 10) : null;
    if (startIndex == null || seriesIdx == null) return;

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
        onEdit(seriesIdx, rows[rowPos].index, num);
        count++;
      }
    }
    if (count > 0) {
      setPastedCount(count);
      setTimeout(() => setPastedCount(0), 2000);
    }
  }, [rows, readOnly, onEdit]);

  const handleCopy = useCallback((e: React.ClipboardEvent) => {
    e.preventDefault();
    const headerLine = ['Datum', ...headers.map(h => h.tsKey)].join('\t');
    const tsv = rows
      .map(r => {
        const ts = formatTimestamp(r.timestampMs, dimension);
        const vals = headers.map((_, i) => {
          const val = getEffectiveValue(r, i);
          return (val == null || isNaN(val)) ? '' : formatValue(val, decimals);
        });
        return [ts, ...vals].join('\t');
      })
      .join('\n');
    e.clipboardData.setData('text/plain', `${headerLine}\n${tsv}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [rows, headers, dimension, decimals, getEffectiveValue]);

  const allStats = useMemo(() => {
    return headers.map((_, seriesIdx) => {
      const valid: number[] = [];
      for (const r of rows) {
        const v = getEffectiveValue(r, seriesIdx);
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
    });
  }, [rows, headers, getEffectiveValue]);

  const columns = useMemo<ColumnDef<MultiSeriesRow>[]>(() => {
    const cols: ColumnDef<MultiSeriesRow>[] = [
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
    ];

    for (let i = 0; i < headers.length; i++) {
      const h = headers[i];
      const seriesIdx = i;
      cols.push({
        id: `value_${h.tsId}`,
        header: () => headers.length > 1 ? (
          <div title={`TS-ID: ${h.tsId}`}>
            <div className="col-header-key">{h.tsKey}</div>
            <div className="col-header-unit">Wert ({h.unit})</div>
          </div>
        ) : (
          <span title={`TS-ID: ${h.tsId}`}>Wert ({h.unit})</span>
        ),
        accessorFn: (row) => row.values[seriesIdx],
        meta: { flex: true },
        enableResizing: false,
        cell: ({ row: tableRow }) => {
          const v = tableRow.original.values[seriesIdx];
          if (readOnly) {
            return (v == null || isNaN(v)) ? '' : formatValue(v, decimals);
          }
          return <EditableCell seriesIdx={seriesIdx} row={tableRow.original} edits={editsRef.current} decimals={decimals} onEdit={onEdit} />;
        },
      });
    }

    return cols;
  }, [headers, dimension, decimals, readOnly, onEdit]);

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    columnResizeMode: 'onEnd',
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

  const hasAnyStats = allStats.some(s => s !== null);

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
              {header.column.getCanResize() && (
                <div
                  className={`resize-handle${header.column.getIsResizing() ? ' resizing' : ''}`}
                  onPointerDown={header.getResizeHandler()}
                  onClick={e => e.stopPropagation()}
                />
              )}
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
      {hasAnyStats && (
        <div className="grid-footer">
          {copied && <div className="copy-hint">Kopiert!</div>}
          {pastedCount > 0 && <div className="copy-hint">{pastedCount} Werte eingefügt!</div>}
          {(['Min', 'Max', 'Avg'] as const).map(label => {
            const allCols = table.getAllColumns();
            return (
              <div key={label} className="grid-row stat-row">
                <div className="grid-cell" style={{ width: allCols[0]?.getSize() }} />
                <div className="grid-cell stat-label-cell" style={{ width: allCols[1]?.getSize() }}>{label}</div>
                {allStats.map((stats, i) => (
                  <div key={headers[i].tsId} className="grid-cell stat-value-cell" style={{ flex: 1 }}>
                    {stats ? formatValue(
                      label === 'Min' ? stats.min : label === 'Max' ? stats.max : stats.avg,
                      decimals
                    ) : ''}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
