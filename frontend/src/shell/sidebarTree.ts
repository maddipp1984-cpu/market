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
export const defaultSidebarTree: SidebarNode[] = [
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
      {
        id: 'batchplanung',
        label: 'Batchplanung',
        children: [
          { id: 'batch-schedules', label: 'Planungen', tabType: 'batch-schedules' },
          { id: 'batch-history', label: 'Historie', tabType: 'batch-history' },
        ],
      },
      { id: 'einstellungen', label: 'Einstellungen', tabType: 'einstellungen' },
    ],
  },
  {
    id: 'administration',
    label: 'Administration',
    children: [
      { id: 'admin-users', label: 'Benutzer', tabType: 'admin-users' },
      { id: 'admin-groups', label: 'Gruppen', tabType: 'admin-groups' },
      { id: 'admin-permissions', label: 'Berechtigungen', tabType: 'admin-permissions' },
    ],
  },
];
