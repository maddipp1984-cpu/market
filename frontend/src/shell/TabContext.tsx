import { createContext, useContext, useState, useCallback, useRef, useEffect, type ReactNode } from 'react';
import { getTabType } from './tabTypes';

export interface Tab {
  id: string;
  type: string;
  label: string;
  icon: ReactNode;
  params?: Record<string, unknown>;
}

interface TabContextValue {
  tabs: Tab[];
  activeTabId: string | null;
  openTab: (type: string, params?: Record<string, unknown>) => void;
  closeTab: (id: string) => void;
  closeAllTabs: () => void;
  setActiveTab: (id: string) => void;
  updateTabLabel: (id: string, label: string) => void;
  getTabParams: (tabId: string) => Record<string, unknown> | undefined;
  registerCloseGuard: (tabId: string, guard: () => boolean) => () => void;
  markOverviewStale: (pageKey: string) => void;
  consumeStale: (pageKey: string) => boolean;
}

const TabContext = createContext<TabContextValue | null>(null);

const SESSION_KEY = 'market-tabs';

interface PersistedTab {
  id: string;
  type: string;
  label: string;
  params?: Record<string, unknown>;
}

interface PersistedState {
  tabs: PersistedTab[];
  activeTabId: string | null;
  counter: number;
}

function saveSession(tabs: Tab[], activeTabId: string | null, counter: number) {
  const state: PersistedState = {
    tabs: tabs.map(t => ({ id: t.id, type: t.type, label: t.label, params: t.params })),
    activeTabId,
    counter,
  };
  try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(state)); } catch { /* ignore */ }
}

function restoreSession(): { tabs: Tab[]; activeTabId: string | null; counter: number } | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const state: PersistedState = JSON.parse(raw);
    if (!Array.isArray(state.tabs) || state.tabs.length === 0) return null;

    const tabs: Tab[] = [];
    for (const pt of state.tabs) {
      const tabType = getTabType(pt.type);
      if (!tabType) continue; // Tab-Typ existiert nicht mehr
      tabs.push({ id: pt.id, type: pt.type, label: pt.label, icon: tabType.icon, params: pt.params });
    }
    if (tabs.length === 0) return null;

    const activeTabId = tabs.some(t => t.id === state.activeTabId) ? state.activeTabId : tabs[0].id;
    return { tabs, activeTabId, counter: state.counter || 0 };
  } catch {
    return null;
  }
}

function createDashboardTab(counter: React.MutableRefObject<number>): Tab {
  const dashboard = getTabType('dashboard')!;
  return { id: `tab-${++counter.current}`, type: 'dashboard', label: dashboard.label, icon: dashboard.icon };
}

export function TabProvider({ children }: { children: ReactNode }) {
  const tabCounterRef = useRef(0);
  const closeGuardsRef = useRef<Map<string, () => boolean>>(new Map());

  const [tabs, setTabs] = useState<Tab[]>(() => {
    const restored = restoreSession();
    if (restored) {
      tabCounterRef.current = restored.counter;
      return restored.tabs;
    }
    return [createDashboardTab(tabCounterRef)];
  });
  const [activeTabId, setActiveTabId] = useState<string | null>(() => {
    const restored = restoreSession();
    if (restored) return restored.activeTabId;
    return tabs[0]?.id ?? null;
  });

  // Persist tab state to sessionStorage on every change
  useEffect(() => {
    saveSession(tabs, activeTabId, tabCounterRef.current);
  }, [tabs, activeTabId]);

  const openTab = useCallback((type: string, params?: Record<string, unknown>) => {
    const tabType = getTabType(type);
    if (!tabType) return;

    const newId = `tab-${++tabCounterRef.current}`;
    const newTab: Tab = { id: newId, type, label: tabType.label, icon: tabType.icon, params };
    if (params) tabParamsRef.current.set(newId, params);

    setTabs(prev => {
      // Singleton: fokussiere existierenden Tab statt neuen zu oeffnen
      if (tabType.singleton) {
        const existing = prev.find(t => t.type === type);
        if (existing) {
          setActiveTabId(existing.id);
          return prev;
        }
      }
      setActiveTabId(newId);
      return [...prev, newTab];
    });
  }, []);

  const closeTab = useCallback((id: string) => {
    const guard = closeGuardsRef.current.get(id);
    if (guard && !guard()) {
      return; // Guard hat Schliessen verhindert
    }

    setTabs(prev => {
      if (prev.length <= 1) return prev; // mind. 1 Tab offen
      const idx = prev.findIndex(t => t.id === id);
      const next = prev.filter(t => t.id !== id);
      setActiveTabId(currentActive => {
        if (currentActive !== id) return currentActive;
        // Naechster Tab rechts, sonst links
        const newIdx = Math.min(idx, next.length - 1);
        return next[newIdx]?.id ?? null;
      });
      closeGuardsRef.current.delete(id);
      tabParamsRef.current.delete(id);
      return next;
    });
  }, []);

  const closeAllTabs = useCallback(() => {
    // Alle Guards pruefen — bei Ablehnung abbrechen
    for (const [, guard] of closeGuardsRef.current) {
      if (!guard()) return;
    }
    closeGuardsRef.current.clear();
    const tab = createDashboardTab(tabCounterRef);
    setTabs([tab]);
    setActiveTabId(tab.id);
  }, []);

  const setActiveTab = useCallback((id: string) => {
    setActiveTabId(id);
  }, []);

  const updateTabLabel = useCallback((id: string, label: string) => {
    setTabs(prev => prev.map(t => t.id === id ? { ...t, label } : t));
  }, []);

  const tabParamsRef = useRef(new Map<string, Record<string, unknown>>(
    tabs.filter(t => t.params).map(t => [t.id, t.params!])
  ));

  const getTabParams = useCallback((tabId: string): Record<string, unknown> | undefined => {
    return tabParamsRef.current.get(tabId);
  }, []);

  const registerCloseGuard = useCallback((tabId: string, guard: () => boolean) => {
    closeGuardsRef.current.set(tabId, guard);
    return () => { closeGuardsRef.current.delete(tabId); };
  }, []);

  const staleKeysRef = useRef<Set<string>>(new Set());

  const markOverviewStale = useCallback((pageKey: string) => {
    staleKeysRef.current.add(pageKey);
  }, []);

  const consumeStale = useCallback((pageKey: string): boolean => {
    if (staleKeysRef.current.has(pageKey)) {
      staleKeysRef.current.delete(pageKey);
      return true;
    }
    return false;
  }, []);

  return (
    <TabContext.Provider value={{ tabs, activeTabId, openTab, closeTab, closeAllTabs, setActiveTab, updateTabLabel, getTabParams, registerCloseGuard, markOverviewStale, consumeStale }}>
      {children}
    </TabContext.Provider>
  );
}

export function useTabContext(): TabContextValue {
  const ctx = useContext(TabContext);
  if (!ctx) throw new Error('useTabContext must be used within TabProvider');
  return ctx;
}
