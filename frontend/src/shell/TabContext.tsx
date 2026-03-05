import { createContext, useContext, useState, useCallback, useRef, type ReactNode } from 'react';
import { tabTypes, getTabType } from './tabTypes';

export interface Tab {
  id: string;
  type: string;
  label: string;
  icon: ReactNode;
}

interface TabContextValue {
  tabs: Tab[];
  activeTabId: string | null;
  openTab: (type: string) => void;
  closeTab: (id: string) => void;
  closeAllTabs: () => void;
  setActiveTab: (id: string) => void;
  updateTabLabel: (id: string, label: string) => void;
}

const TabContext = createContext<TabContextValue | null>(null);

export function TabProvider({ children }: { children: ReactNode }) {
  const tabCounterRef = useRef(0);
  const [tabs, setTabs] = useState<Tab[]>(() => {
    const dashboard = tabTypes.find(t => t.type === 'dashboard')!;
    const id = `tab-${++tabCounterRef.current}`;
    return [{ id, type: 'dashboard', label: dashboard.label, icon: dashboard.icon }];
  });
  const [activeTabId, setActiveTabId] = useState<string | null>(() => tabs[0]?.id ?? null);

  const openTab = useCallback((type: string) => {
    const tabType = getTabType(type);
    if (!tabType) return;

    const newId = `tab-${++tabCounterRef.current}`;
    const newTab: Tab = { id: newId, type, label: tabType.label, icon: tabType.icon };

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
      return next;
    });
  }, []);

  const closeAllTabs = useCallback(() => {
    const dashboard = tabTypes.find(t => t.type === 'dashboard')!;
    const id = `tab-${++tabCounterRef.current}`;
    const tab: Tab = { id, type: 'dashboard', label: dashboard.label, icon: dashboard.icon };
    setTabs([tab]);
    setActiveTabId(id);
  }, []);

  const setActiveTab = useCallback((id: string) => {
    setActiveTabId(id);
  }, []);

  const updateTabLabel = useCallback((id: string, label: string) => {
    setTabs(prev => prev.map(t => t.id === id ? { ...t, label } : t));
  }, []);

  return (
    <TabContext.Provider value={{ tabs, activeTabId, openTab, closeTab, closeAllTabs, setActiveTab, updateTabLabel }}>
      {children}
    </TabContext.Provider>
  );
}

export function useTabContext(): TabContextValue {
  const ctx = useContext(TabContext);
  if (!ctx) throw new Error('useTabContext must be used within TabProvider');
  return ctx;
}
