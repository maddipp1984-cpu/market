import type { ReactNode } from 'react';

export interface SidebarNode {
  id: string;
  label: string;
  icon?: ReactNode;
  children?: SidebarNode[];
  tabType?: string;
}

// Konvention:
// - children → Ordner (Klick klappt auf/zu, kein Tab)
// - tabType  → Blatt  (Klick oeffnet Tab)
// - Beides gleichzeitig: tabType wird ignoriert
export const sidebarTree: SidebarNode[] = [
  {
    id: 'daten',
    label: 'Daten',
    children: [
      { id: 'dashboard', label: 'Dashboard', tabType: 'dashboard' },
      { id: 'zeitreihen', label: 'Zeitreihen', tabType: 'zeitreihen' },
    ],
  },
  {
    id: 'stammdaten',
    label: 'Stammdaten',
    children: [
      {
        id: 'objekte-gruppe',
        label: 'Objekte',
        children: [
          { id: 'objekttypen', label: 'Objekttypen', tabType: 'objekttypen' },
          { id: 'einheiten', label: 'Einheiten', tabType: 'einheiten' },
          { id: 'waehrungen', label: 'Waehrungen', tabType: 'waehrungen' },
        ],
      },
    ],
  },
  {
    id: 'system',
    label: 'System',
    children: [
      { id: 'einstellungen', label: 'Einstellungen', tabType: 'einstellungen' },
    ],
  },
];
