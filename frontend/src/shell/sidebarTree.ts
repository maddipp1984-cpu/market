import type { ReactNode } from 'react';

export interface SidebarNode {
  id: string;
  label: string;
  icon?: ReactNode;
  children?: SidebarNode[];
  tabType?: string;
}

// Fallback wenn Backend nicht erreichbar. Muss mit sidebar.xml synchron gehalten werden!
// Siehe: src/main/resources/sidebar.xml
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
      { id: 'business-partners', label: 'Geschaeftspartner', tabType: 'business-partners' },
      { id: 'objekte', label: 'Objekte', tabType: 'objekte' },
      { id: 'objekttypen', label: 'Objekttypen', tabType: 'objekttypen' },
      { id: 'einheiten', label: 'Einheiten', tabType: 'einheiten' },
      { id: 'waehrungen', label: 'Waehrungen', tabType: 'waehrungen' },
      { id: 'reihenarten', label: 'Reihenarten', tabType: 'reihenarten' },
      { id: 'warengruppen', label: 'Warengruppen', tabType: 'warengruppen' },
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
