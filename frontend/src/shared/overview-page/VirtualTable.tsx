import { useMemo } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import './VirtualTable.css';

export interface ColumnOverride {
  header?: string;
  hidden?: boolean;
  format?: (value: unknown) => string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface VirtualTableProps<T extends Record<string, any>> {
  data: T[];
  columnOverrides?: Record<string, ColumnOverride>;
  sorting: SortingState;
  onSortingChange: (sorting: SortingState) => void;
  emptyMessage?: string;
}

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}([T ]|$)/;

function formatDateTime(value: unknown): string {
  if (typeof value !== 'string') return '-';
  const d = new Date(value);
  if (isNaN(d.getTime())) return String(value);
  const date = d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const time = d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  return `${date} ${time}`;
}

function detectAutoFormat(sampleValue: unknown): ((v: unknown) => string) | undefined {
  if (typeof sampleValue === 'string' && ISO_DATE_RE.test(sampleValue)) {
    return formatDateTime;
  }
  return undefined;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function buildColumns<T extends Record<string, any>>(
  data: T[],
  overrides: Record<string, ColumnOverride>,
): ColumnDef<T, unknown>[] {
  if (data.length === 0) return [];

  const keys = Object.keys(data[0]);
  const columns: ColumnDef<T, unknown>[] = [];

  for (const key of keys) {
    const override = overrides[key];
    if (override?.hidden) continue;

    const headerText = override?.header ?? key;
    const sample = data.find(row => row[key] != null)?.[key];
    const formatter = override?.format ?? detectAutoFormat(sample);

    const col: ColumnDef<T, unknown> = {
      id: key,
      accessorKey: key as keyof T & string,
      header: headerText,
    };

    if (formatter) {
      col.cell = ({ getValue }) => formatter(getValue());
    }

    columns.push(col);
  }

  return columns;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function VirtualTable<T extends Record<string, any>>({
  data,
  columnOverrides = {},
  sorting,
  onSortingChange,
  emptyMessage = 'Keine Daten vorhanden',
}: VirtualTableProps<T>) {
  const columns = useMemo(
    () => buildColumns(data, columnOverrides),
    [data, columnOverrides],
  );

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: updater => {
      onSortingChange(typeof updater === 'function' ? updater(sorting) : updater);
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const { rows: tableRows } = table.getRowModel();

  return (
    <div className="vtable">
      <table className="vtable-table">
        <thead>
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id}>
              {hg.headers.map(header => (
                <th
                  key={header.id}
                  className={header.column.getCanSort() ? 'sortable' : undefined}
                  onClick={header.column.getToggleSortingHandler()}
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                  {{ asc: ' \u25B2', desc: ' \u25BC' }[header.column.getIsSorted() as string] ?? ''}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {tableRows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="vtable-empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            tableRows.map((row, i) => (
              <tr key={row.id} className={i % 2 !== 0 ? 'odd' : undefined}>
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
  );
}
