import { useMemo, useState, useCallback, useRef, type ReactNode } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import { ContextMenu, type ContextMenuEntry } from './ContextMenu';
import './VirtualTable.css';

export interface ColumnOverride {
  header?: string;
  hidden?: boolean;
  format?: (value: unknown) => string;
}

export interface ContextAction {
  label: string;
  icon?: ReactNode;
  onClick: (rows: Record<string, unknown>[]) => void;
  danger?: boolean;
  multi?: boolean; // true = show also for multi-selection (default: single only)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface VirtualTableProps<T extends Record<string, any>> {
  data: T[];
  columnOverrides?: Record<string, ColumnOverride>;
  sorting: SortingState;
  onSortingChange: (sorting: SortingState) => void;
  emptyMessage?: string;
  onRowDoubleClick?: (row: T) => void;
  selectable?: boolean;
  selectedRows?: Set<number>;
  onSelectionChange?: (selected: Set<number>) => void;
  contextActions?: ContextAction[];
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

const CHAR_WIDTH = 8;
const PADDING = 32;
const MIN_COL_WIDTH = 60;
const MAX_COL_WIDTH = 400;

function estimateColumnWidth<T>(
  data: T[],
  key: string,
  headerText: string,
  formatter?: (v: unknown) => string,
): number {
  let maxLen = headerText.length + 2; // +2 for sort indicator
  const sampleSize = Math.min(data.length, 100);
  for (let i = 0; i < sampleSize; i++) {
    const raw = (data[i] as Record<string, unknown>)[key];
    if (raw == null) continue;
    const display = formatter ? formatter(raw) : String(raw);
    if (display.length > maxLen) maxLen = display.length;
  }
  return Math.max(MIN_COL_WIDTH, Math.min(MAX_COL_WIDTH, maxLen * CHAR_WIDTH + PADDING));
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
    const size = estimateColumnWidth(data, key, headerText, formatter);

    const col: ColumnDef<T, unknown> = {
      id: key,
      accessorKey: key as keyof T & string,
      header: headerText,
      size,
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
  onRowDoubleClick,
  selectable = false,
  selectedRows,
  onSelectionChange,
  contextActions,
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
    columnResizeMode: 'onEnd',
  });

  const { rows: tableRows } = table.getRowModel();

  // Selection
  const selected = selectedRows ?? new Set<number>();
  const lastClickedRef = useRef<number | null>(null);

  const handleRowClick = useCallback((e: React.MouseEvent, rowIndex: number) => {
    if (!selectable || !onSelectionChange) return;

    const next = new Set(selected);

    if (e.shiftKey && lastClickedRef.current !== null) {
      const from = Math.min(lastClickedRef.current, rowIndex);
      const to = Math.max(lastClickedRef.current, rowIndex);
      for (let i = from; i <= to; i++) next.add(i);
    } else if (e.ctrlKey || e.metaKey) {
      if (next.has(rowIndex)) next.delete(rowIndex);
      else next.add(rowIndex);
    } else {
      // Single click without modifier — don't change selection
      return;
    }

    lastClickedRef.current = rowIndex;
    onSelectionChange(next);
  }, [selectable, selected, onSelectionChange]);

  const handleCheckbox = useCallback((rowIndex: number, checked: boolean) => {
    if (!onSelectionChange) return;
    const next = new Set(selected);
    if (checked) next.add(rowIndex);
    else next.delete(rowIndex);
    lastClickedRef.current = rowIndex;
    onSelectionChange(next);
  }, [selected, onSelectionChange]);

  const handleSelectAll = useCallback((checked: boolean) => {
    if (!onSelectionChange) return;
    if (checked) {
      onSelectionChange(new Set(tableRows.map((_, i) => i)));
    } else {
      onSelectionChange(new Set());
    }
  }, [tableRows, onSelectionChange]);

  const allSelected = selectable && tableRows.length > 0 && selected.size === tableRows.length;
  const someSelected = selectable && selected.size > 0 && selected.size < tableRows.length;

  // Context menu
  const [ctxMenu, setCtxMenu] = useState<{ x: number; y: number; rowIndex: number } | null>(null);

  const handleContextMenu = useCallback((e: React.MouseEvent, rowIndex: number) => {
    if (!contextActions || contextActions.length === 0) return;
    e.preventDefault();

    // If right-clicked row is not in selection, select only that row
    if (selectable && onSelectionChange && !selected.has(rowIndex)) {
      onSelectionChange(new Set([rowIndex]));
    }

    setCtxMenu({ x: e.clientX, y: e.clientY, rowIndex });
  }, [contextActions, selectable, selected, onSelectionChange]);

  const ctxMenuItems = useMemo((): ContextMenuEntry[] => {
    if (!ctxMenu || !contextActions) return [];

    const effectiveSelected = selected.size > 0 ? selected : new Set([ctxMenu.rowIndex]);
    const isSingle = effectiveSelected.size === 1;
    const selectedData = [...effectiveSelected].map(i => tableRows[i]?.original as Record<string, unknown>).filter(Boolean);

    const items: ContextMenuEntry[] = [];
    let lastWasDanger = false;
    for (const action of contextActions) {
      if (!isSingle && !action.multi) continue;
      // Add separator before danger items
      if (action.danger && !lastWasDanger && items.length > 0) {
        items.push({ separator: true });
      }
      items.push({
        label: action.label,
        icon: action.icon,
        danger: action.danger,
        onClick: () => action.onClick(selectedData),
      });
      lastWasDanger = !!action.danger;
    }
    return items;
  }, [ctxMenu, contextActions, selected, tableRows]);

  const colSpan = columns.length + (selectable ? 1 : 0);

  return (
    <div className="vtable">
      <table className="vtable-table" style={{ width: table.getTotalSize() + (selectable ? 40 : 0) }}>
        <thead>
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id}>
              {selectable && (
                <th className="vtable-checkbox-col">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={el => { if (el) el.indeterminate = someSelected; }}
                    onChange={e => handleSelectAll(e.target.checked)}
                  />
                </th>
              )}
              {hg.headers.map(header => (
                <th
                  key={header.id}
                  className={header.column.getCanSort() ? 'sortable' : undefined}
                  onClick={header.column.getToggleSortingHandler()}
                  style={{ width: header.getSize() }}
                >
                  {flexRender(header.column.columnDef.header, header.getContext())}
                  {{ asc: ' \u25B2', desc: ' \u25BC' }[header.column.getIsSorted() as string] ?? ''}
                  {header.column.getCanResize() && (
                    <div
                      className={`vtable-resize-handle${header.column.getIsResizing() ? ' resizing' : ''}`}
                      onPointerDown={header.getResizeHandler()}
                      onClick={e => e.stopPropagation()}
                    />
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {tableRows.length === 0 ? (
            <tr>
              <td colSpan={colSpan} className="vtable-empty">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            tableRows.map((row, i) => {
              const isSelected = selectable && selected.has(i);
              return (
                <tr
                  key={row.id}
                  className={`${i % 2 !== 0 ? 'odd' : ''}${isSelected ? ' selected' : ''}`}
                  onClick={e => handleRowClick(e, i)}
                  onDoubleClick={() => onRowDoubleClick?.(row.original)}
                  onContextMenu={e => handleContextMenu(e, i)}
                  style={onRowDoubleClick || contextActions ? { cursor: 'pointer' } : undefined}
                >
                  {selectable && (
                    <td className="vtable-checkbox-col">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={e => handleCheckbox(i, e.target.checked)}
                        onClick={e => e.stopPropagation()}
                      />
                    </td>
                  )}
                  {row.getVisibleCells().map(cell => (
                    <td key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {ctxMenu && ctxMenuItems.length > 0 && (
        <ContextMenu
          x={ctxMenu.x}
          y={ctxMenu.y}
          items={ctxMenuItems}
          onClose={() => setCtxMenu(null)}
        />
      )}
    </div>
  );
}
