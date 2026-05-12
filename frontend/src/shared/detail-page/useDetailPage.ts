import { useState, useCallback, useEffect } from 'react';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import type { DetailMode } from './DetailPage';

interface UseDetailPageOptions<T extends { id: number | null }> {
  tabId: string;
  defaultData: T;
  fetchFn?: (id: number, signal?: AbortSignal) => Promise<T>;
  saveFn?: (dto: T) => Promise<T>;
  deleteFn?: (id: number) => Promise<void>;
  pageKey: string;
  labelPrefix?: string;
  labelField?: keyof T;
  onDataLoaded?: (data: T) => void;
  onSaved?: (saved: T) => void;
}

interface UseDetailPageReturn<T extends { id: number | null }> {
  mode: DetailMode;
  entityId: number | undefined;
  data: T;
  dirty: boolean;
  loading: boolean;
  setData: React.Dispatch<React.SetStateAction<T>>;
  setDirty: React.Dispatch<React.SetStateAction<boolean>>;
  setLoading: React.Dispatch<React.SetStateAction<boolean>>;
  updateField: (field: keyof T, value: unknown) => void;
  updateData: (updater: T | ((prev: T) => T)) => void;
  handleSave: () => Promise<void>;
  handleSaveSuccess: () => void;
  handleDelete: (() => Promise<void>) | undefined;
}

function getLabelValue(data: Record<string, unknown>, labelField?: string): string {
  if (labelField) return String(data[labelField] ?? '');
  return String(data.name ?? data.code ?? data.isoCode ?? '');
}

export function useDetailPage<T extends { id: number | null }>({
  tabId,
  defaultData,
  fetchFn,
  saveFn,
  deleteFn,
  pageKey: _pageKey,
  labelPrefix = '',
  labelField,
  onDataLoaded,
  onSaved,
}: UseDetailPageOptions<T>): UseDetailPageReturn<T> {
  void _pageKey;
  const { getTabParams, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<T>(defaultData);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  const labelKey = (labelField ?? 'name') as string;

  useEffect(() => {
    if (mode === 'new' || !entityId || !fetchFn) return;
    let cancelled = false;
    setLoading(true);
    fetchFn(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      const label = labelPrefix
        ? `${labelPrefix}: ${getLabelValue(result as Record<string, unknown>, labelKey)}`
        : getLabelValue(result as Record<string, unknown>, labelKey);
      updateTabLabel(tabId, label);
      setLoading(false);
      onDataLoaded?.(result);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel, showMessage, fetchFn, labelPrefix, labelKey, onDataLoaded]);

  const updateField = useCallback((field: keyof T, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const updateData = useCallback((updater: T | ((prev: T) => T)) => {
    setData(updater);
    setDirty(true);
  }, []);

  const handleSave = useCallback(async () => {
    if (!saveFn) return;
    const saved = await saveFn(data);
    setData(saved);
    const label = labelPrefix
      ? `${labelPrefix}: ${getLabelValue(saved as Record<string, unknown>, labelKey)}`
      : getLabelValue(saved as Record<string, unknown>, labelKey);
    updateTabLabel(tabId, label);
    onSaved?.(saved);
  }, [data, tabId, updateTabLabel, saveFn, labelPrefix, labelKey, onSaved]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId && deleteFn
    ? () => deleteFn(entityId)
    : undefined;

  return {
    mode,
    entityId,
    data,
    dirty,
    loading,
    setData,
    setDirty,
    setLoading,
    updateField,
    updateData,
    handleSave,
    handleSaveSuccess,
    handleDelete,
  };
}
