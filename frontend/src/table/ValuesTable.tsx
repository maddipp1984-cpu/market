import { useRef, useMemo, useState } from 'react';
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
import type { Dimension } from '../api/types';
import type { TimeSeriesRow } from '../api/types';

interface ValuesTableProps {
  rows: TimeSeriesRow[];
  unit: string;
  dimension: Dimension;
}

export function ValuesTable({ rows, unit, dimension }: ValuesTableProps) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const parentRef = useRef<HTMLDivElement>(null);

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
          return v.toLocaleString('de-DE', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4,
          });
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
    <div className="grid-table">
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
    </div>
  );
}
