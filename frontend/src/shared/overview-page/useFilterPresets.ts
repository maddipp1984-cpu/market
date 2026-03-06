import { useState, useEffect, useCallback } from 'react';
import type { FilterCondition, FilterPreset, PresetScope } from '../../api/types';
import {
  fetchFilterPresets,
  createFilterPreset,
  updateFilterPreset as apiUpdatePreset,
  deleteFilterPreset as apiDeletePreset,
  setPresetDefault as apiSetDefault,
  clearPresetDefault as apiClearDefault,
} from '../../api/client';

const CACHE_KEY = (pageKey: string) => `filter-presets:${pageKey}`;

export function useFilterPresets(pageKey: string) {
  const [presets, setPresets] = useState<FilterPreset[]>(() => {
    try {
      const cached = localStorage.getItem(CACHE_KEY(pageKey));
      return cached ? JSON.parse(cached) : [];
    } catch { return []; }
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const ac = new AbortController();
    setLoading(true);
    fetchFilterPresets(pageKey, ac.signal)
      .then(data => {
        setPresets(data);
        localStorage.setItem(CACHE_KEY(pageKey), JSON.stringify(data));
      })
      .catch(e => {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        console.error('Failed to load presets:', e);
      })
      .finally(() => setLoading(false));
    return () => ac.abort();
  }, [pageKey]);

  const updateCache = useCallback((updated: FilterPreset[]) => {
    setPresets(updated);
    localStorage.setItem(CACHE_KEY(pageKey), JSON.stringify(updated));
  }, [pageKey]);

  const reload = useCallback(async () => {
    const data = await fetchFilterPresets(pageKey);
    updateCache(data);
    return data;
  }, [pageKey, updateCache]);

  const savePreset = useCallback(async (name: string, conditions: FilterCondition[], scope: PresetScope) => {
    await createFilterPreset({ pageKey, name, conditions, scope });
    await reload();
  }, [pageKey, reload]);

  const updatePreset = useCallback(async (presetId: number, name: string, conditions: FilterCondition[], scope: PresetScope) => {
    await apiUpdatePreset(presetId, { pageKey, name, conditions, scope });
    await reload();
  }, [pageKey, reload]);

  const removePreset = useCallback(async (presetId: number) => {
    await apiDeletePreset(presetId);
    setPresets(prev => {
      const updated = prev.filter(p => p.presetId !== presetId);
      localStorage.setItem(CACHE_KEY(pageKey), JSON.stringify(updated));
      return updated;
    });
  }, [pageKey]);

  const setDefault = useCallback(async (presetId: number) => {
    await apiSetDefault(presetId);
    await reload();
  }, [reload]);

  const clearDefault = useCallback(async (presetId: number) => {
    await apiClearDefault(presetId);
    await reload();
  }, [reload]);

  const defaultPreset = presets.find(p => p.isDefault && p.userId !== null)
    ?? presets.find(p => p.isDefault && p.scope === 'GLOBAL')
    ?? null;

  return { presets, loading, defaultPreset, savePreset, updatePreset, deletePreset: removePreset, setDefault, clearDefault };
}
