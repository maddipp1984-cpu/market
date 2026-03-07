import { createContext, useContext, useState, useCallback, useRef, type ReactNode } from 'react';
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

function createDashboardTab(counter: React.MutableRefObject<number>): Tab {
  const dashboard = getTabType('dashboard')!;
  return { id: `tab-${++counter.current}`, type: 'dashboard', label: dashboard.label, icon: dashboard.icon };
}

export function TabProvider({ children }: { children: ReactNode }) {
  const tabCounterRef = useRef(0);
  const closeGuardsRef = useRef<Map<string, () => boolean>>(new Map());
  const [tabs, setTabs] = useState<Tab[]>(() => [createDashboardTab(tabCounterRef)]);
  const [activeTabId, setActiveTabId] = useState<string | null>(() => tabs[0]?.id ?? null);

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

  const tabParamsRef = useRef<Map<string, Record<string, unknown>>>(new Map());

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
