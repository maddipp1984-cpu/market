import { useState, useEffect, useCallback, useMemo } from 'react';
import type { SortingState } from '@tanstack/react-table';
import type { ColumnMeta, FilterCondition, FilterRequest, TableResponse } from '../../api/types';
import { fetchTable, type TimingInfo } from '../../api/client';
import { Button } from '../Button';
import { StatusMessage } from '../StatusMessage';
import { Card } from '../Card';
import { VirtualTable, type ColumnOverride } from './VirtualTable';
import { FilterBuilder } from './FilterBuilder';
import { useFilterPresets } from './useFilterPresets';
import { useAuth } from '../../auth/AuthContext';
import { useTabContext } from '../../shell/TabContext';
import { iconRefresh, iconPlus, iconFilter } from './icons';
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
}: OverviewPageProps) {
  const { canWrite } = useAuth();
  const effectiveResourceKey = resourceKey ?? pageKey;
  const [tableData, setTableData] = useState<TableResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [timing, setTiming] = useState<TimingInfo | null>(null);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [activeFilter, setActiveFilter] = useState<FilterRequest | null>(null);
  const [columns, setColumns] = useState<ColumnMeta[]>([]);
  const [filterOpen, setFilterOpen] = useState(false);
  const { presets, loading: presetsLoading, defaultPreset, savePreset, updatePreset, deletePreset: removePreset, setDefault, clearDefault } = useFilterPresets(pageKey);

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

  const footer = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${data.length} Eintr${data.length !== 1 ? 'aege' : 'ag'}`);
    if (activeFilter) parts.push('(gefiltert)');
    if (timing) {
      parts.push(`${timing.totalMs} ms`);
      if (timing.serverMs !== null) parts.push(`(Server: ${timing.serverMs} ms)`);
    }
    return <span>{parts.join(' | ')}</span>;
  }, [data.length, activeFilter, timing]);

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
    </div>
  );
}
