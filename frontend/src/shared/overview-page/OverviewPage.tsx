import { useState, useEffect, useCallback, useMemo } from 'react';
import type { SortingState } from '@tanstack/react-table';
import type { ColumnMeta, FilterCondition, FilterRequest, TableResponse } from '../../api/types';
import { fetchTable, type TimingInfo } from '../../api/client';
import { Button } from '../Button';
import { StatusMessage } from '../StatusMessage';
import { Card } from '../Card';
import { VirtualTable, type ColumnOverride, type ContextAction } from './VirtualTable';
import { FilterBuilder } from './FilterBuilder';
import { useFilterPresets } from './useFilterPresets';
import { useAuth } from '../../auth/AuthContext';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { iconRefresh, iconPlus, iconFilter, iconOverwrite, iconTrash } from './icons';
import './OverviewPage.css';

interface OverviewPageProps {
  pageKey: string;
  resourceKey?: string;
  apiUrl: string;
  tabId?: string;
  onNew?: () => void;
  newLabel?: string;
  columnOverrides?: Record<string, ColumnOverride>;
  emptyMessage?: string;
  onRowDoubleClick?: (row: Record<string, unknown>) => void;
  onDelete?: (rows: Record<string, unknown>[]) => Promise<void>;
  extraContextActions?: ContextAction[];
}

export function OverviewPage({
  pageKey,
  resourceKey,
  apiUrl,
  tabId,
  onNew,
  newLabel = 'Neu',
  columnOverrides = {},
  emptyMessage = 'Keine Daten vorhanden',
  onRowDoubleClick,
  onDelete,
  extraContextActions,
}: OverviewPageProps) {
  const { canWrite, canDelete: canDeletePerm } = useAuth();
  const { showMessage } = useMessageBar();
  const effectiveResourceKey = resourceKey ?? pageKey;
  const [tableData, setTableData] = useState<TableResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [timing, setTiming] = useState<TimingInfo | null>(null);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [activeFilter, setActiveFilter] = useState<FilterRequest | null>(null);
  const [columns, setColumns] = useState<ColumnMeta[]>([]);
  const [filterOpen, setFilterOpen] = useState(false);
  const [selectedRowIds, setSelectedRowIds] = useState<Set<string>>(new Set());
  const [pendingDelete, setPendingDelete] = useState<Record<string, unknown>[] | null>(null);
  const [deleting, setDeleting] = useState(false);
  const { presets, loading: presetsLoading, defaultPreset, savePreset, updatePreset, deletePreset: removePreset, setDefault, clearDefault } = useFilterPresets(pageKey);

  const hasDeletePerm = canDeletePerm(effectiveResourceKey);
  const selectable = !!(onDelete || extraContextActions);

  const loadData = useCallback(async (filter?: FilterRequest, signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchTable(apiUrl, filter, signal);
      setTableData(result.data);
      setTiming(result.timing);
      if (result.data.columns.length > 0) {
        setColumns(result.data.columns);
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [apiUrl]);

  // Initial load: wait for presets, then load with default filter or unfiltered
  useEffect(() => {
    if (presetsLoading) return;
    const ac = new AbortController();
    if (defaultPreset) {
      const filter: FilterRequest = { conditions: defaultPreset.conditions };
      setActiveFilter(filter);
      loadData(filter, ac.signal);
    } else {
      loadData(undefined, ac.signal);
    }
    return () => ac.abort();
  }, [presetsLoading, defaultPreset, loadData]);

  // Auto-refresh when tab becomes active and data is stale
  const { activeTabId, consumeStale } = useTabContext();
  useEffect(() => {
    if (!tabId || activeTabId !== tabId) return;
    if (consumeStale(pageKey)) {
      loadData(activeFilter ?? undefined);
    }
  }, [activeTabId, tabId, pageKey, consumeStale, loadData, activeFilter]);

  // Clear selection when data changes
  useEffect(() => {
    setSelectedRowIds(new Set());
  }, [tableData]);

  const handleFilterExecute = useCallback((conditions: FilterCondition[]) => {
    const filter: FilterRequest = { conditions };
    setActiveFilter(filter);
    loadData(filter);
  }, [loadData]);

  const handleRefresh = useCallback(() => {
    loadData(activeFilter ?? undefined);
  }, [loadData, activeFilter]);

  const handleFilterClose = useCallback(() => {
    setFilterOpen(false);
  }, []);

  const handleFilterReset = useCallback(() => {
    setActiveFilter(null);
    loadData();
  }, [loadData]);

  const handleDeleteConfirmed = useCallback(async (rows: Record<string, unknown>[]) => {
    if (!onDelete) return;
    setDeleting(true);
    try {
      await onDelete(rows);
      setPendingDelete(null);
      setSelectedRowIds(new Set());
      showMessage('Geloescht', 'success');
      loadData(activeFilter ?? undefined);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    } finally {
      setDeleting(false);
    }
  }, [onDelete, loadData, activeFilter, showMessage]);

  const data = tableData?.data ?? [];

  const mergedOverrides = useMemo(() => {
    const merged: Record<string, ColumnOverride> = {};
    for (const col of columns) {
      merged[col.key] = { header: col.label };
    }
    for (const [key, override] of Object.entries(columnOverrides)) {
      merged[key] = { ...merged[key], ...override };
    }
    return merged;
  }, [columns, columnOverrides]);

  // Build context actions from props
  const contextActions = useMemo((): ContextAction[] => {
    const actions: ContextAction[] = [];

    if (onRowDoubleClick) {
      actions.push({
        label: 'Bearbeiten',
        icon: iconOverwrite,
        onClick: (rows) => onRowDoubleClick(rows[0]),
      });
    }

    if (onNew && canWrite(effectiveResourceKey)) {
      actions.push({
        label: newLabel,
        icon: iconPlus,
        onClick: () => onNew(),
        multi: true,
      });
    }

    if (onDelete && hasDeletePerm) {
      actions.push({
        label: 'Loeschen',
        icon: iconTrash,
        danger: true,
        multi: true,
        onClick: (rows) => setPendingDelete(rows),
      });
    }

    if (extraContextActions && extraContextActions.length > 0) {
      actions.push(...extraContextActions);
    }

    return actions;
  }, [onRowDoubleClick, onNew, onDelete, canWrite, hasDeletePerm, effectiveResourceKey, newLabel, extraContextActions]);

  // Resolve selected IDs to row data for toolbar delete
  const getSelectedData = useCallback((): Record<string, unknown>[] => {
    return data.filter(row => selectedRowIds.has(String(row.id ?? '')));
  }, [data, selectedRowIds]);

  const footer = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${data.length} Eintr${data.length !== 1 ? 'aege' : 'ag'}`);
    if (activeFilter) parts.push('(gefiltert)');
    if (selectedRowIds.size > 0) parts.push(`${selectedRowIds.size} ausgewaehlt`);
    if (timing) {
      parts.push(`${timing.totalMs} ms`);
      if (timing.serverMs !== null) parts.push(`(Server: ${timing.serverMs} ms)`);
    }
    return <span>{parts.join(' | ')}</span>;
  }, [data.length, activeFilter, timing, selectedRowIds.size]);

  return (
    <div className="overview-page">
      <div className="overview-page-toolbar">
        <Button variant="ghost" icon onClick={handleRefresh} disabled={loading} title="Aktualisieren" aria-label="Aktualisieren">
          {iconRefresh}
        </Button>
        {columns.length > 0 && (
          <Button
            variant="ghost"
            icon
            onClick={() => setFilterOpen(o => !o)}
            title="Filter"
            aria-label="Filter"
            className={activeFilter ? 'filter-icon-active' : undefined}
          >
            {iconFilter}
          </Button>
        )}
        {onNew && canWrite(effectiveResourceKey) && (
          <Button variant="primary" icon onClick={onNew} title={newLabel} aria-label={newLabel}>
            {iconPlus}
          </Button>
        )}
        {selectedRowIds.size > 0 && hasDeletePerm && onDelete && (
          <Button
            variant="ghost"
            icon
            onClick={() => setPendingDelete(getSelectedData())}
            title={`${selectedRowIds.size} Eintraege loeschen`}
            aria-label="Ausgewaehlte loeschen"
            className="overview-delete-btn"
          >
            {iconTrash}
          </Button>
        )}
      </div>
      <div className="overview-page-content">
        {loading && <StatusMessage type="info">Lade...</StatusMessage>}
        {error && <StatusMessage type="error">{error}</StatusMessage>}
        {!loading && !error && (
          <Card>
            <VirtualTable
              data={data}
              columnOverrides={mergedOverrides}
              sorting={sorting}
              onSortingChange={setSorting}
              emptyMessage={emptyMessage}
              onRowDoubleClick={onRowDoubleClick}
              selectable={selectable}
              selectedRowIds={selectedRowIds}
              onSelectionChange={setSelectedRowIds}
              contextActions={contextActions}
            />
          </Card>
        )}
      </div>
      <div className="overview-page-footer">{footer}</div>
      {filterOpen && <div className="filter-drawer-backdrop" onClick={handleFilterClose} />}
      <div className={`filter-drawer ${filterOpen ? 'open' : ''}`}>
        {columns.length > 0 && (
          <FilterBuilder
            columns={columns}
            hasActiveFilter={activeFilter !== null}
            activeConditions={activeFilter?.conditions}
            onExecute={handleFilterExecute}
            onReset={handleFilterReset}
            onClose={handleFilterClose}
            presets={presets}
            onSavePreset={savePreset}
            onUpdatePreset={updatePreset}
            onDeletePreset={removePreset}
            onSetDefault={setDefault}
            onClearDefault={clearDefault}
          />
        )}
      </div>

      {pendingDelete && (
        <div className="overview-confirm-backdrop" onClick={() => setPendingDelete(null)}>
          <div className="overview-confirm-modal" onClick={e => e.stopPropagation()}>
            <h3>Wirklich loeschen?</h3>
            <p>
              {pendingDelete.length === 1
                ? 'Dieser Eintrag wird unwiderruflich geloescht.'
                : `${pendingDelete.length} Eintraege werden unwiderruflich geloescht.`}
            </p>
            <div className="overview-confirm-actions">
              <Button
                variant="ghost"
                onClick={() => handleDeleteConfirmed(pendingDelete)}
                disabled={deleting}
                className="overview-delete-btn"
              >
                {deleting ? 'Loeschen...' : 'Ja, loeschen'}
              </Button>
              <Button variant="ghost" onClick={() => setPendingDelete(null)}>Abbrechen</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
